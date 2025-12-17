/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.services.sync;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.gateway.reactor.SecurityDomainManager;
import io.gravitee.am.gateway.reactor.impl.DefaultReactor;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.monitoring.DomainState;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.EventRepository;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.common.event.EventManager;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import java.text.Collator;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SyncManager implements InitializingBean {

    /**
     * Add 30s delay before and after to avoid problem with out of sync clocks.
     */
    public static final int TIMEFRAME_BEFORE_DELAY = 30000;
    public static final int TIMEFRAME_AFTER_DELAY = 30000;

    private final Logger logger = LoggerFactory.getLogger(SyncManager.class);
    private static final String SHARDING_TAGS_SYSTEM_PROPERTY = "tags";
    private static final String ENVIRONMENTS_SYSTEM_PROPERTY = "environments";
    private static final String ORGANIZATIONS_SYSTEM_PROPERTY = "organizations";
    private static final String DATAPLANE_ID_PROPERTY = Scope.GATEWAY.getRepositoryPropertyKey()+".dataPlane.id";
    private static final String SEPARATOR = ",";

    @Autowired
    private EventManager eventManager;

    @Autowired
    private SecurityDomainManager securityDomainManager;

    @Autowired
    private Environment environment;

    @Lazy
    @Autowired
    private DomainRepository domainRepository;

    @Lazy
    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private Node node;

    @Autowired
    @Lazy
    private DefaultReactor defaultReactor;

    @Autowired
    private GatewayMetricProvider gatewayMetricProvider;

    @Autowired
    private DomainReadinessService domainReadinessService;

    private Optional<List<String>> shardingTags;

    private Optional<List<String>> environments;

    private Optional<List<String>> organizations;

    private List<String> environmentIds;

    private String dataPlaneId;

    private long lastRefreshAt = -1;

    private long lastDelay = 0;

    @Getter
    private boolean allSecurityDomainsSync = false;

    @Value("${services.sync.initTimeOutMillis:-1}")
    private int initDomainTimeOut = -1;

    @Value("${services.sync.eventsTimeOutMillis:30000}")
    private int eventsTimeOut = 30000;

    @Value("${services.sync.timeframeBeforeDelay:" + TIMEFRAME_BEFORE_DELAY + "}")
    private int timeframeBeforeDelay;

    @Value("${services.sync.timeframeAfterDelay:" + TIMEFRAME_AFTER_DELAY + "}")
    private int timeframeAfterDelay;

    private Cache<String, String> processedEventIds;

    @Override
    public void afterPropertiesSet() {
        logger.info("Starting gateway tags initialization ...");
        this.initShardingTags();
        this.initEnvironments();
        logger.info("Gateway has been loaded with the following information :");
        logger.info("\t\t - Sharding tags : {}", shardingTags.isPresent() ? shardingTags.get() : "[]");
        logger.info("\t\t - Organizations : {}", organizations.isPresent() ? organizations.get() : "[]");
        logger.info("\t\t - Environments : {}", environments.isPresent() ? environments.get() : "[]");
        logger.info("\t\t - Environments loaded : {}", environmentIds != null ? environmentIds : "[]");
        this.processedEventIds = CacheBuilder.newBuilder()
                .expireAfterWrite(timeframeBeforeDelay + timeframeAfterDelay, TimeUnit.MILLISECONDS)
                .build();
    }

    public void refresh() {
        if (!defaultReactor.isStarted()) {
            logger.info("No domain listener, rescheduling initial synchronization process");
            return;
        }

        logger.debug("Refreshing sync state...");
        long nextLastRefreshAt = System.currentTimeMillis();

        try {
            if (lastRefreshAt == -1) {
                logger.debug("Initial synchronization");
                deployDomains();
                allSecurityDomainsSync = true;
            } else {
                // search for events and compute them
                logger.debug("Events synchronization");

                final long from = (lastRefreshAt - lastDelay) - timeframeBeforeDelay;
                final long to = nextLastRefreshAt + timeframeAfterDelay;
                Single<List<Event>> findEvents = eventRepository.findByTimeFrameAndDataPlaneId(from, to, dataPlaneId).toList();
                if (this.eventsTimeOut > 0) {
                    findEvents = findEvents.timeout(this.eventsTimeOut, TimeUnit.MILLISECONDS);
                }
                List<Event> events = findEvents.blockingGet();

                if (!events.isEmpty()) {
                    gatewayMetricProvider.updateSyncEvents(events.size());

                    // Extract only the latest events by type and id
                    Map<AbstractMap.SimpleEntry<Object, Object>, Event> sortedEvents = events
                            .stream()
                            .collect(
                                    toMap(
                                            event -> new AbstractMap.SimpleEntry<>(event.getType(), event.getPayload().getId()),
                                            event -> event, BinaryOperator.maxBy(comparing(Event::getCreatedAt)), LinkedHashMap::new));
                    computeEvents(sortedEvents.values());
                } else {
                    gatewayMetricProvider.updateSyncEvents(0);
                }

            }
            lastRefreshAt = nextLastRefreshAt;
            lastDelay = System.currentTimeMillis() - nextLastRefreshAt;
        } catch (Exception ex) {
            if (logger.isDebugEnabled()) {
                logger.error("Synchronization failed", ex);
            } else {
                logger.error("Synchronization failed, ex={}", ex.toString());
            }
        }
    }

    private void deployDomains() {
        logger.info("Starting security domains initialization ...");
        Single<List<Domain>> findDomains = domainRepository.findAll()
                // remove disabled domains
                .filter(Domain::isEnabled)
                // Can the security domain be deployed ?
                .filter(this::canHandle)
                .toList();
        if (this.initDomainTimeOut > 0) {
            findDomains = findDomains.timeout(this.initDomainTimeOut, TimeUnit.MILLISECONDS);
        }
        List<Domain> domains = findDomains.blockingGet();

        // deploy security domains
        domains.forEach(domain -> {
            securityDomainManager.deploy(domain);
            domainReadinessService.updateDomainStatus(domain.getId(), DomainState.Status.DEPLOYED);
        });
        logger.info("Security domains initialization done");
    }

    private void computeEvents(Collection<Event> events) {
        events.forEach(event -> {
            logger.debug("Compute event id : {}, with type : {} and timestamp : {} and payload : {}", event.getId(), event.getType(), event.getCreatedAt(), event.getPayload());
            if (Objects.requireNonNull(event.getType()) == Type.DOMAIN) {
                synchronizeDomain(event);
            } else {
                String eventId = event.getId();
                if (processedEventIds.asMap().putIfAbsent(eventId, eventId) == null) {
                    eventManager.publishEvent(io.gravitee.am.common.event.Event.valueOf(event.getType(), event.getPayload().getAction()), event.getPayload());
                } else {
                    logger.debug("Event id {} already processed", eventId);
                }
            }
        });
    }

    private void synchronizeDomain(Event event) {
        final String domainId = event.getPayload().getId();
        final Action action = event.getPayload().getAction();
        switch (action) {
            case CREATE, UPDATE -> {
                domainReadinessService.updateDomainStatus(domainId, DomainState.Status.INITIALIZING);
                Maybe<Domain> maybeDomain = domainRepository.findById(domainId);
                if (this.eventsTimeOut > 0) {
                    maybeDomain = maybeDomain.timeout(this.eventsTimeOut, TimeUnit.MILLISECONDS);
                }
                Domain domain = maybeDomain.blockingGet();
                if (domain == null) {
                    domainReadinessService.removeDomain(domainId);
                    return;
                }

                // Get deployed domain
                Domain deployedDomain = securityDomainManager.get(domain.getId());
                // Can the security domain be deployed?
                if (!canHandle(domain)) {
                    // Check that the security domain was not previously deployed with other tags
                    // In that case, we must undeploy it
                    if (deployedDomain != null) {
                        securityDomainManager.undeploy(domainId);
                    }
                    domainReadinessService.removeDomain(domainId);
                    return;
                }

                // domain is not yet deployed, so let's do it !
                if (deployedDomain == null) {
                    securityDomainManager.deploy(domain);
                } else if (deployedDomain.getUpdatedAt().before(domain.getUpdatedAt())) {
                    securityDomainManager.update(domain);
                }

                if (domain.isEnabled()) {
                    domainReadinessService.updateDomainStatus(domain.getId(), DomainState.Status.DEPLOYED);
                } else {
                    domainReadinessService.removeDomain(domain.getId());
                }
            }
            case DELETE -> {
                domainReadinessService.updateDomainStatus(domainId, DomainState.Status.REMOVING);
                securityDomainManager.undeploy(domainId);
                domainReadinessService.removeDomain(domainId);
            }
            default -> {
                // No action needed for default case
            }
        }
    }

    private boolean canHandle(Domain domain) {
        return hasMatchingDataplane(domain) && hasMatchingEnvironments(domain) && hasMatchingTags(domain);
    }

    private boolean hasMatchingTags(Domain domain) {
        if (shardingTags.isEmpty()) {
            // no tags configured on this gateway instance
            return true;
        }

        final List<String> tagList = shardingTags.get();
        if (domain.getTags() == null || domain.getTags().isEmpty()) {
            logger.debug("Tags {} are configured on gateway instance but not found on the security domain {}", tagList, domain.getName());
            return false;
        }

        final List<String> inclusionTags = getInclusionElements(tagList);
        final List<String> exclusionTags = getExclusionElements(tagList);

        if (inclusionTags.stream().anyMatch(exclusionTags::contains)) {
            throw new IllegalArgumentException("You must not configure a tag to be included and excluded");
        }

        final boolean hasMatchingTags = hasMatchingElements(inclusionTags, exclusionTags, domain.getTags());
        if (!hasMatchingTags) {
            logger.debug("The security domain {} has been ignored because not in configured tags {}", domain.getName(), tagList);
        }
        return hasMatchingTags;
    }

    private boolean hasMatchingDataplane(Domain domain) {
        return dataPlaneId.equals(domain.getDataPlaneId());
    }

    private boolean hasMatchingEnvironments(Domain domain) {
        if (environmentIds == null || environmentIds.isEmpty()) {
            // check if there is no environment because gravitee.yml is empty (OK)
            // or no matching environment has been found in the database (KO)
            return organizations.isEmpty() && environments.isEmpty();
        }
        return environmentIds.contains(domain.getReferenceId());
    }

    private void initShardingTags() {
        shardingTags = getSystemValues(SHARDING_TAGS_SYSTEM_PROPERTY);
    }

    private void initEnvironments() {
        environments = getSystemValues(ENVIRONMENTS_SYSTEM_PROPERTY);
        organizations = getSystemValues(ORGANIZATIONS_SYSTEM_PROPERTY);
        this.environmentIds = new ArrayList<>((Set<String>) node.metadata().get(Node.META_ENVIRONMENTS));
        dataPlaneId =  environment.getProperty(DATAPLANE_ID_PROPERTY, String.class, DataPlaneDescription.DEFAULT_DATA_PLANE_ID);
    }

    private Optional<List<String>> getSystemValues(String key) {
        String systemProperty = System.getProperty(key);
        String values = systemProperty == null ? environment.getProperty(key) : systemProperty;
        if (values != null && !values.isEmpty()) {
            return Optional.of(Arrays.asList(values.split(SEPARATOR)));
        }
        return Optional.empty();
    }

    private static boolean hasMatchingElements(final List<String> inclusionElements,
                                               final List<String> exclusionElements,
                                               final Set<String> domainElements) {
        return inclusionElements
                .stream()
                .anyMatch(element ->
                        domainElements
                                .stream()
                                .anyMatch(domainElement -> matchingString(element, domainElement))
                ) || (!exclusionElements.isEmpty() &&
                exclusionElements
                        .stream()
                        .noneMatch(element ->
                                domainElements
                                        .stream()
                                        .anyMatch(domainElement -> matchingString(element, domainElement))));
    }

    private static List<String> getInclusionElements(final List<String> elements) {
        return elements.stream()
                .map(String::trim)
                .filter(elt -> !elt.startsWith("!"))
                .collect(Collectors.toList());
    }

    private static List<String> getExclusionElements(final List<String> elements) {
        return elements.stream()
                .map(String::trim)
                .filter(elt -> elt.startsWith("!"))
                .map(elt -> elt.substring(1))
                .collect(Collectors.toList());
    }

    private static boolean matchingString(final String a, final String b) {
        final Collator collator = Collator.getInstance();
        collator.setStrength(Collator.NO_DECOMPOSITION);
        return collator.compare(a, b) == 0;
    }
}
