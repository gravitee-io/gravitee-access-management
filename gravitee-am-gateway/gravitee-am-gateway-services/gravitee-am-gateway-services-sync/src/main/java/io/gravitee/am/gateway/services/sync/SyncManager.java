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
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.EventRepository;
import io.gravitee.common.event.EventManager;
import io.gravitee.node.api.Node;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import java.text.Collator;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SyncManager implements InitializingBean, Subscriber<SyncContext> {

    public static final boolean NO_DELAY_ERRORS = false;
    public static final int NO_CONCURRENCY = 1;
    public static final int ONE_MINUTE = 60_000;
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

    private boolean allSecurityDomainsSync = false;

    private final PublishProcessor<SyncContext> offsetUpdater = PublishProcessor.create();
    private final PublishProcessor<Long> synchronizer = PublishProcessor.create();
    private Subscription syncSubscription;

    private SyncContext lastSyncContext = new SyncContext();

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

        initializeSyncFlow();
    }

    private void initializeSyncFlow() {
        // listen to the synchronization events
        // this synchronizer listen event received by refresh call
        // and merge this event with the last execution state
        synchronizer
                .observeOn(Schedulers.io())
                .onBackpressureBuffer()
                .zipWith(offsetUpdater, (time, context) -> {
                    context.setNextLastRefreshAt(time);
                    return context;
                })
                .flatMap(context -> {
                    try {
                        if (context.getLastRefreshAt() == -1) {
                            logger.debug("Initial synchronization");
                            return deployDomains()
                                    .toList()
                                    .map(domains -> {
                                        allSecurityDomainsSync = true;
                                        return context.toNextOffset();
                                    }).toFlowable()
                                    .doOnError(error -> logger.error("An error has occurred during initiale synchronization", error))
                                    .onErrorResumeNext(Flowable.just(new SyncContext()));
                        } else {
                            logger.trace("eventRepository.findByTimeFrame({}, {})", context.computeStartOffset(), context.getNextLastRefreshAt());
                            return eventRepository.findByTimeFrame(context.computeStartOffset(), context.getNextLastRefreshAt())
                                    .toList()
                                    .flatMap(events -> {
                                        if (events != null && !events.isEmpty()) {
                                            // Extract only the latest events by type and id
                                            Map<AbstractMap.SimpleEntry, Event> sortedEvents = events
                                                    .stream()
                                                    .collect(
                                                            toMap(
                                                                    event -> new AbstractMap.SimpleEntry<>(event.getType(), event.getPayload().getId()),
                                                                    event -> event, BinaryOperator.maxBy(comparing(Event::getCreatedAt)), LinkedHashMap::new));

                                            return Flowable.fromIterable(sortedEvents.values())
                                                    .flatMapMaybe(this::computeEvent, NO_DELAY_ERRORS, NO_CONCURRENCY).toList();
                                        }
                                        return Single.just(events);
                                    })
                                    .map(e -> context.toNextOffset())
                                    .toFlowable()
                                    .doOnError(error -> logger.error("An error has occurred during initiale synchronization", error))
                                    .onErrorResumeNext(Flowable.just(context));
                        }
                    } catch (Exception ex) {
                        logger.error("An error has occurred during synchronization", ex);
                        return Flowable.just(context);
                    }
                })
                .onErrorResumeNext(Flowable.just(this.lastSyncContext))
                .subscribe(this);

        // provide initial value for the domain synchronization
        offsetUpdater.onNext(this.lastSyncContext);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.syncSubscription = subscription;
        this.syncSubscription.request(1);
    }

    @Override
    public void onNext(SyncContext context) {
        logger.debug("Events synchronization successful");
        offsetUpdater.onNext(context);
        this.lastSyncContext = context;
        this.syncSubscription.request(1);
    }

    @Override
    public void onError(Throwable error) {
        logger.error("[FATAL] An error has occurred during synchronization, gateway synchronization is stopped!", error);
        this.syncSubscription.request(1);
    }

    @Override
    public void onComplete() {
        logger.info("Events synchronization finalized");
    }

    public void refresh() {
        logger.debug("Refreshing sync state...");
        long nextLastRefreshAt = System.currentTimeMillis();
        if (this.lastSyncContext.getLastRefreshAt() > 0 && nextLastRefreshAt - this.lastSyncContext.getLastRefreshAt() > ONE_MINUTE) {
            logger.warn("SyncContext not updated since 60s, restart the sync flow");
            if (this.syncSubscription != null) {
                this.syncSubscription.cancel();
            }
            this.initializeSyncFlow();
        }
        this.synchronizer.onNext(nextLastRefreshAt);
    }

    public boolean isAllSecurityDomainsSync() {
        return allSecurityDomainsSync;
    }

    private Flowable<Domain> deployDomains() {
        logger.info("Starting security domains initialization ...");
        return domainRepository.findAll()
                // remove disabled domains
                .filter(Domain::isEnabled)
                // Can the security domain be deployed ?
                .filter(this::canHandle)
                .map(domain -> {
                    securityDomainManager.deploy(domain);
                    return domain;
                }).doOnComplete(() -> logger.info("Security domains initialization done"));
    }

    private Maybe<Event> computeEvent(Event event) {
        logger.debug("Compute event id : {}, with type : {} and timestamp : {} and payload : {}", event.getId(), event.getType(), event.getCreatedAt(), event.getPayload());
        switch (event.getType()) {
            case DOMAIN:
                return synchronizeDomain(event);
            default:
                return Maybe.fromCallable(() -> {
                    eventManager.publishEvent(io.gravitee.am.common.event.Event.valueOf(event.getType(), event.getPayload().getAction()), event.getPayload());
                    return event;
                });
        }
    }

    private Maybe<Event> synchronizeDomain(Event event) {
        final String domainId = event.getPayload().getId();
        final Action action = event.getPayload().getAction();
        switch (action) {
            case CREATE:
            case UPDATE:
                return domainRepository.findById(domainId).map(domain ->
                {   // Get deployed domain
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
                    return event;
                });
            case DELETE:
                return Maybe.fromCallable(() -> {
                    securityDomainManager.undeploy(domainId);
                    return event;
                });
        }
        return Maybe.just(event);
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
