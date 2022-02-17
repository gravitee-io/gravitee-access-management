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

import io.gravitee.am.common.event.Action;
import io.gravitee.am.gateway.reactor.SecurityDomainManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.gravitee.am.repository.management.api.EventRepository;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.gravitee.common.event.EventManager;
import io.gravitee.node.api.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import java.text.Collator;
import java.util.*;
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

    private final Logger logger = LoggerFactory.getLogger(SyncManager.class);
    private static final String SHARDING_TAGS_SYSTEM_PROPERTY = "tags";
    private static final String ENVIRONMENTS_SYSTEM_PROPERTY = "environments";
    private static final String ORGANIZATIONS_SYSTEM_PROPERTY = "organizations";
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

    private Optional<List<String>> shardingTags;

    private Optional<List<String>> environments;

    private Optional<List<String>> organizations;

    private List<String> environmentIds;

    private long lastRefreshAt = -1;

    private long lastDelay = 0;

    private boolean allSecurityDomainsSync = false;

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Starting gateway tags initialization ...");
        this.initShardingTags();
        this.initEnvironments();
        logger.info("Gateway has been loaded with the following information :");
        logger.info("\t\t - Sharding tags : " + (shardingTags.isPresent() ? shardingTags.get() : "[]"));
        logger.info("\t\t - Organizations : " + (organizations.isPresent() ? organizations.get() : "[]"));
        logger.info("\t\t - Environments : " + (environments.isPresent() ? environments.get() : "[]"));
        logger.info("\t\t - Environments loaded : " + (environmentIds != null ? environmentIds : "[]"));
    }

    public void refresh() {
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

                List<Event> events = eventRepository.findByTimeFrame(lastRefreshAt - lastDelay, nextLastRefreshAt).toList().blockingGet();

                if (events != null && !events.isEmpty()) {
                    // Extract only the latest events by type and id
                    Map<AbstractMap.SimpleEntry, Event> sortedEvents = events
                            .stream()
                            .collect(
                                    toMap(
                                            event -> new AbstractMap.SimpleEntry<>(event.getType(), event.getPayload().getId()),
                                            event -> event, BinaryOperator.maxBy(comparing(Event::getCreatedAt)), LinkedHashMap::new));
                    computeEvents(sortedEvents.values());
                }

            }
            lastRefreshAt = nextLastRefreshAt;
            lastDelay = System.currentTimeMillis() - nextLastRefreshAt;
        } catch (Exception ex) {
            logger.error("An error has occurred during synchronization", ex);
        }
    }

    public boolean isAllSecurityDomainsSync() {
        return allSecurityDomainsSync;
    }

    private void deployDomains() {
        logger.info("Starting security domains initialization ...");
        List<Domain> domains = domainRepository.findAll()
                // remove disabled domains
                .filter(Domain::isEnabled)
                // Can the security domain be deployed ?
                .filter(this::canHandle)
                .toList()
                .blockingGet();

        // deploy security domains
        domains.stream().forEach(domain -> securityDomainManager.deploy(domain));
        logger.info("Security domains initialization done");
    }

    private void computeEvents(Collection<Event> events) {
        events.forEach(event -> {
            logger.debug("Compute event id : {}, with type : {} and timestamp : {} and payload : {}", event.getId(), event.getType(), event.getCreatedAt(), event.getPayload());
            switch (event.getType()) {
                case DOMAIN:
                    synchronizeDomain(event);
                    break;
                default:
                    eventManager.publishEvent(io.gravitee.am.common.event.Event.valueOf(event.getType(), event.getPayload().getAction()), event.getPayload());
            }
        });
    }

    private void synchronizeDomain(Event event) {
        final String domainId = event.getPayload().getId();
        final Action action = event.getPayload().getAction();
        switch (action) {
            case CREATE:
            case UPDATE:
                Domain domain = domainRepository.findById(domainId).blockingGet();
                if (domain != null) {
                    // Get deployed domain
                    Domain deployedDomain = securityDomainManager.get(domain.getId());
                    // Can the security domain be deployed ?
                    if (canHandle(domain)) {
                        // domain is not yet deployed, so let's do it !
                        if (deployedDomain == null) {
                            securityDomainManager.deploy(domain);
                        } else if (deployedDomain.getUpdatedAt().before(domain.getUpdatedAt())) {
                            securityDomainManager.update(domain);
                        }
                    } else {
                        // Check that the security domain was not previously deployed with other tags
                        // In that case, we must undeploy it
                        if (deployedDomain != null) {
                            securityDomainManager.undeploy(domainId);
                        }
                    }
                }
                break;
            case DELETE:
                securityDomainManager.undeploy(domainId);
                break;
        }
    }

    private boolean canHandle(Domain domain) {
        return hasMatchingEnvironments(domain) && hasMatchingTags(domain);
    }

    private boolean hasMatchingTags(Domain domain) {
        if (!shardingTags.isPresent()) {
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

    private boolean hasMatchingEnvironments(Domain domain) {
        if (environmentIds == null || environmentIds.isEmpty()) {
            // check if there is no environment because gravitee.yml is empty (OK)
            // or no matching environment has been found in the database (KO)
            return !organizations.isPresent() && !environments.isPresent();
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
    }

    private Optional<List<String>> getSystemValues(String key) {
        String systemPropertyEnvs = System.getProperty(key);
        String envs = systemPropertyEnvs == null ? environment.getProperty(key) : systemPropertyEnvs;
        if (envs != null && !envs.isEmpty()) {
            return Optional.of(Arrays.asList(envs.split(SEPARATOR)));
        }
        return Optional.empty();
    }

    private static boolean hasMatchingElements(final List<String> inclusionElements,
                                               final List<String> exclusionElements,
                                               final Set<String> domainElements) {
        final boolean hasMatchingElements =
                inclusionElements
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
        return hasMatchingElements;
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
