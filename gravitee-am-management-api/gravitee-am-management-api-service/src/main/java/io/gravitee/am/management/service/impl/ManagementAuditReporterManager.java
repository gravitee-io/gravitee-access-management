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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.common.event.ReporterEvent;
import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.reporter.core.ReporterPluginManager;
import io.gravitee.am.plugins.reporter.core.ReporterProviderConfiguration;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.provider.NoOpReporter;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.reporter.impl.AuditReporterVerticle;
import io.gravitee.am.service.reporter.vertx.EventBusReporterWrapper;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.rxjava3.core.RxHelper;
import io.vertx.rxjava3.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ManagementAuditReporterManager extends AbstractService<AuditReporterManager> implements AuditReporterManager, EventListener<ReporterEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(ManagementAuditReporterManager.class);
    private String deploymentId;

    @Autowired
    private ReporterPluginManager reporterPluginManager;

    @Autowired
    private ReporterService reporterService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private Vertx vertx;

    @Autowired
    private EventManager eventManager;

    private final ConcurrentMap<io.gravitee.am.model.Reporter, Reporter> auditReporters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, io.gravitee.am.model.Reporter> reporters = new ConcurrentHashMap<>();

    private Reporter internalReporter;

    private Reporter noOpReporter;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for reporter events for the management API");
        eventManager.subscribeForEvents(this, ReporterEvent.class);

        // init noOpReporter
        noOpReporter = new NoOpReporter();

        logger.info("Initializing audit reporters");
        reporterService.findAll().blockingForEach(reporter -> {
            logger.info("Initializing audit reporter : {} for {}", reporter.getName(), reporter.getReference());
            try {
                loadReporter(reporter);
            } catch (Exception ex) {
                logger.error("An error has occurred while loading audit reporter: {} [{}]", reporter.getName(), reporter.getType(), ex);
                removeReporter(reporter.getId());
            }
        });

        // init internal reporter (platform reporter)
        NewReporter newInternalReporter = reporterService.createInternal();
        logger.info("Initializing internal {} audit reporter", newInternalReporter.getType());
        var providerConfiguration = new ReporterProviderConfiguration(newInternalReporter.getType(), newInternalReporter.getConfiguration());
        this.internalReporter = reporterPluginManager.create(providerConfiguration);
        logger.info("Internal audit {} reporter initialized", newInternalReporter.getType());
        // deploy internal reporter verticle
        deployReporterVerticle(List.of(new EventBusReporterWrapper(vertx, this.internalReporter)));
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (deploymentId != null) {
            vertx.rxUndeploy(deploymentId)
                    .doFinally(() -> {
                        for (io.gravitee.reporter.api.Reporter reporter : auditReporters.values()) {
                            try {
                                logger.info("Stopping reporter: {}", reporter);
                                reporter.stop();
                            } catch (Exception ex) {
                                logger.error("Unexpected error while stopping reporter", ex);
                            }
                        }
                    })
                    .subscribe();
        }
    }

    @Override
    public void onEvent(Event<ReporterEvent, Payload> event) {
        switch (event.type()) {
            case DEPLOY:
                deployReporter(event.content().getId());
                break;
            case UPDATE:
                reloadReporter(event.content().getId(), EventBusReporterWrapper.ChildReporterAction.of(event.content()));
                break;
            case UNDEPLOY:
                removeReporter(event.content().getId());
                break;
        }
    }

    @Override
    protected String name() {
        return "AM Management API Reporter service";
    }

    @Override
    public Maybe<Reporter> getReporter(Reference domain) {
        if (domain.type() == ReferenceType.DOMAIN || domain.type() == ReferenceType.ORGANIZATION) {
            return doGetReporter(domain);
        } else {
            // Internal reporter must be use for all other resources.
            return Maybe.just(internalReporter);
        }
    }

    private Maybe<Reporter> doGetReporter(Reference reference) {
        Optional<Reporter> optionalReporter = auditReporters
                .entrySet()
                .stream()
                .filter(entry -> reference.equals(entry.getKey().getReference()))
                .map(Entry::getValue)
                .filter(Reporter::canSearch)
                .findFirst();

        // reporter can be missing as it can take sometime for the reporter events
        // to propagate across the cluster so if there are at least one reporter for the domain, return the NoOpReporter to avoid
        // too long waiting time that may lead to unexpected even on the UI.
        return optionalReporter.map(Maybe::just).orElseGet(() -> reporterService.findByReference(reference)
                .toList()
                .flatMapMaybe(reporterConfigs -> {
                    if (reporterConfigs.isEmpty()) {
                        logger.warn("No reporter exists for {}", reference);
                        return Maybe.empty();
                    }
                    logger.warn("Reporter for domain {} isn't bootstrapped yet", reference);
                    return Maybe.just(noOpReporter);
                })
                .onErrorResumeNext(error -> {
                    logger.error("Error occurred when fetching reporter for domain {}", reference, error);
                    return Maybe.empty();
                }));
    }

    private void deployReporter(String reporterId) {
        logger.info("Management API has received a deploy reporter event for {}", reporterId);
        reporterService.findById(reporterId)
                .subscribe(
                        reporter -> {
                            if (needDeployment(reporter)) {
                                loadReporter(reporter);
                            } else {
                                logger.info("Reporter already deployed event for {} ignored", reporterId);
                            }
                        },
                        error -> logger.error("Unable to deploy reporter {}", reporterId, error),
                        () -> logger.error("No reporter found with id {}", reporterId));
    }

    private void reloadReporter(String reporterId, EventBusReporterWrapper.ChildReporterAction referenceChange) {
        logger.info("Management API has received an update reporter event for {}", reporterId);
        reporterService.findById(reporterId)
                .subscribe(reporter -> {
                            if (needDeployment(reporter)) {
                                reloadWithNewConfig(reporter);
                            } else if (referenceChange != null) {
                                updateReferences(referenceChange, reporter);
                            }
                        },
                        error -> logger.error("Unable to reload reporter {}", reporterId, error),
                        () -> logger.error("No reporter found with id {}", reporterId));
    }

    private void reloadWithNewConfig(io.gravitee.am.model.Reporter reporter) {
        logger.debug("Reload reporter: {} after configuration update", reporter.getName());
        getReporterInstanceById(reporter.getId())
                .ifPresentOrElse(auditReporter -> {
                            try {
                                // reload the provider if it's enabled
                                if (reporter.isEnabled()) {
                                    auditReporter.stop();
                                    auditReporters.entrySet().removeIf(entry -> entry.getKey().getId().equals(reporter.getId()));
                                    loadReporter(reporter);
                                } else {
                                    logger.info("Reporter: {} has been disabled", reporter.getName());
                                    // unregister event bus consumer
                                    // we do not stop the underlying reporter if it manages search because it can be used to fetch reportable
                                    ((EventBusReporterWrapper) auditReporter).unregister();
                                    if (!auditReporter.canSearch()) {
                                        auditReporter.stop();
                                    }
                                }
                            } catch (Exception e) {
                                logger.error("An error occurs while reloading reporter: {}", reporter.getName(), e);
                            }
                        },
                        () -> logger.info("There is no reporter to reload"));
    }

    private void updateReferences(EventBusReporterWrapper.ChildReporterAction referenceChange, io.gravitee.am.model.Reporter reporter) {
        logger.debug("Update references for reporter: {}", reporter.getName());
        getReporterInstanceById(reporter.getId())
                .ifPresentOrElse(auditReporter -> {
                            if (auditReporter instanceof EventBusReporterWrapper<?, ?> wrapper) {
                                wrapper.updateReferences(referenceChange);
                            } else {
                                // this should never happen, so let's have a warning if it does
                                logger.warn("Reporter {} doesn't support updating references", auditReporter);
                            }
                        },
                        () -> logger.info("There is no reporter to reload"));
    }

    private void loadReporter(io.gravitee.am.model.Reporter reporter) {

        var isOrganizationReporter = reporter.getReference().type() == ReferenceType.ORGANIZATION;
        var launcher = new AuditReporterLauncher(reporter);
        if (isOrganizationReporter) {
            var orgId = reporter.getReference().id();

            getAdditionalReferences(reporter)
                    .subscribeOn(Schedulers.io())
                    .subscribe(references -> {
                        launcher.addReferences(references);
                        try {
                            launcher.accept(new GraviteeContext(orgId, null, null));
                        } catch (Exception ex) {
                            logUnableToLoad(reporter, ex);
                        }
                    }, throwable -> logUnableToLoad(reporter, throwable));


        } else {
            var domainId = reporter.getReference().id();
            domainService
                    .findById(domainId)
                    .flatMapSingle(domain -> {
                        if (ReferenceType.ENVIRONMENT.equals(domain.getReferenceType())) {
                            return environmentService.findById(domain.getReferenceId()).map(env -> new GraviteeContext(env.getOrganizationId(), env.getId(), domain.getId()));
                        } else {
                            // currently domain is only linked to domainEnv
                            return Single.error(new EnvironmentNotFoundException("Domain " + domainId + " should be lined to an Environment"));
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .subscribe(launcher, throwable -> logUnableToLoad(reporter, throwable));
        }

    }

    private static void logUnableToLoad(io.gravitee.am.model.Reporter reporter, Throwable ex) {
        logger.error("Unable to load reporter '{}'", reporter.getId(), ex);
    }

    private Optional<Reporter> getReporterInstanceById(String reporterId) {
        return auditReporters
                .entrySet()
                .stream()
                .filter(entry -> reporterId.equals(entry.getKey().getId()))
                .map(Entry::getValue)
                .findFirst();
    }

    private Single<List<Reference>> getAdditionalReferences(io.gravitee.am.model.Reporter reporter) {
        if (!reporter.isInherited() || reporter.getReference().type() != ReferenceType.ORGANIZATION) {
            return Single.just(List.of());
        }
        var orgId = reporter.getReference().id();
        return environmentService.findAll(orgId)
                .flatMap(env -> domainService.findAllByEnvironment(orgId, env.getId()))
                .map(domain -> Reference.domain(domain.getId()))
                .toList();

    }

    private class AuditReporterLauncher implements Consumer<GraviteeContext> {
        private final io.gravitee.am.model.Reporter reporter;
        private List<Reference> additionalReferences = new ArrayList<>();

        private AuditReporterLauncher(io.gravitee.am.model.Reporter reporter) {
            this.reporter = reporter;
        }

        private AuditReporterLauncher addReferences(Collection<Reference> references) {
            this.additionalReferences.addAll(references);
            return this;
        }


        @Override
        public void accept(GraviteeContext graviteeContext) throws Exception {
            if (graviteeContext != null) {
                if (reporter.isEnabled()) {
                    var providerConfig = new ReporterProviderConfiguration(reporter, graviteeContext);
                    var auditReporter = reporterPluginManager.create(providerConfig);
                    if (auditReporter == null) {
                        logger.warn("Couldn't create a {} reporter for context {}", providerConfig.getType(), providerConfig.getGraviteeContext());
                        return;
                    }

                    logger.info("Initializing audit reporter: {} for {}", reporter.getName(), reporter.getReference());
                    if (reporter.isInherited()) {
                        logger.info("{} is inheritable. It will also report events for: {}", reporter.getName(), additionalReferences);
                    }
                    var eventBusReporter = createWrapper(auditReporter, reporter);
                    auditReporters.put(reporter, eventBusReporter);
                    reporters.put(reporter.getId(), reporter);
                    try {
                        eventBusReporter.start();
                    } catch (Exception e) {
                        logger.error("Unexpected error while loading reporter", e);
                    }
                } else {
                    // initialize NoOpReporter in order to allow to reload this reporter with valid implementation if it is enabled through the UI
                    auditReporters.put(reporter, new EventBusReporterWrapper<>(vertx, new NoOpReporter(), reporter.getReference()));
                    reporters.put(reporter.getId(), reporter);
                }
            }
        }

        private Reporter<?, ?> createWrapper(AuditReporter auditReporter, io.gravitee.am.model.Reporter reporterConfig) {
            if (additionalReferences.isEmpty()) {
                return new EventBusReporterWrapper<>(vertx, auditReporter, reporterConfig.getReference());
            }
            var allReferences = new ArrayList<>(additionalReferences);
            allReferences.add(0, reporterConfig.getReference());
            return new EventBusReporterWrapper<>(vertx, auditReporter, allReferences);

        }
    }

    private void removeReporter(String reporterId) {
        try {
            Optional<Reporter> optionalReporter = getReporterInstanceById(reporterId);

            if (optionalReporter.isPresent()) {
                optionalReporter.get().stop();
                auditReporters.entrySet().removeIf(entry -> reporterId.equals(entry.getKey().getId()));
                reporters.remove(reporterId);
            }
        } catch (Exception e) {
            logger.error("Unexpected error while removing reporter", e);
        }

    }

    private void deployReporterVerticle(Collection<Reporter> reporters) {
        Single<String> deployment = RxHelper.deployVerticle(vertx, applicationContext.getBean(AuditReporterVerticle.class));

        deployment.subscribe(id -> {
                    // Deployed
                    deploymentId = id;
                    if (!reporters.isEmpty()) {
                        for (io.gravitee.reporter.api.Reporter reporter : reporters) {
                            try {
                                logger.info("Starting reporter: {}", reporter);
                                reporter.start();
                            } catch (Exception ex) {
                                logger.error("Unexpected error while starting reporter", ex);
                            }
                        }
                    } else {
                        logger.info("\tThere is no reporter to start");
                    }
                }, err ->
                        // Could not deploy
                        logger.error("Reporter service can not be started", err)
        );
    }

    /**
     * @param reporter
     * @return true if the Reporter has never been deployed or if the deployed version is not up to date
     */
    private boolean needDeployment(io.gravitee.am.model.Reporter reporter) {
        final io.gravitee.am.model.Reporter deployedReporter = this.reporters.get(reporter.getId());
        return (deployedReporter == null || deployedReporter.getUpdatedAt().before(reporter.getUpdatedAt()));
    }
}
