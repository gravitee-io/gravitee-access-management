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
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.reporter.core.ReporterPluginManager;
import io.gravitee.am.plugins.reporter.core.ReporterProviderConfiguration;
import io.gravitee.am.reporter.api.provider.NoOpReporter;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.am.service.exception.ReporterNotFoundForReferenceException;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.reporter.impl.AuditReporterVerticle;
import io.gravitee.am.service.reporter.vertx.EventBusReporterWrapper;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.rxjava3.core.RxHelper;
import io.vertx.rxjava3.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Arrays.asList;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AuditReporterManagerImpl extends AbstractService<AuditReporterManager> implements AuditReporterManager, EventListener<ReporterEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(AuditReporterManagerImpl.class);
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
    private ApplicationContext applicationContext;

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

        // init internal reporter (platform reporter)
        NewReporter internalReporter = reporterService.createInternal();
        logger.info("Initializing internal {} audit reporter", internalReporter.getType());
        var providerConfiguration = new ReporterProviderConfiguration(internalReporter.getType(), internalReporter.getConfiguration());
        this.internalReporter = reporterPluginManager.create(providerConfiguration);
        logger.info("Internal audit {} reporter initialized", internalReporter.getType());

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

        // deploy internal reporter verticle
        deployReporterVerticle(asList(new EventBusReporterWrapper(vertx, this.internalReporter)));
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
                reloadReporter(event.content().getId());
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
    public Reporter getReporter(ReferenceType referenceType, String referenceId) {

        if (referenceType == ReferenceType.DOMAIN || referenceType == ReferenceType.ORGANIZATION) {
            return doGetReporter(new Reference(referenceType, referenceId));
        } else {
            // Internal reporter must be use for all other resources.
            return internalReporter;
        }
    }

    @Override
    public Reporter getReporter(Reference domain) {
        return doGetReporter(domain);
    }

    private Reporter doGetReporter(Reference reference) {
        Optional<Reporter> optionalReporter = auditReporters
                .entrySet()
                .stream()
                .filter(entry -> reference.equals(entry.getKey().getReference()))
                .map(Entry::getValue)
                .filter(Reporter::canSearch)
                .findFirst();

        if (optionalReporter.isPresent()) {
            return optionalReporter.get();
        }

        // reporter can be missing as it can take sometime for the reporter events
        // to propagate across the cluster so if there are at least one reporter for the domain, return the NoOpReporter to avoid
        // too long waiting time that may lead to unexpected even on the UI.
        try {
            List<io.gravitee.am.model.Reporter> reporters = reporterService.findByReference(reference).toList().blockingGet();
            if (reporters.isEmpty()) {
                throw new ReporterNotFoundForReferenceException(reference);
            }
            logger.warn("Reporter for domain {} isn't bootstrapped yet", reference);
            return noOpReporter;
        } catch (Exception ex) {
            logger.error("An error has occurred while fetching reporter for domain {}", reference, ex);
            throw new IllegalStateException(ex);
        }
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

    private void reloadReporter(String reporterId) {
        logger.info("Management API has received an update reporter event for {}", reporterId);
        reporterService.findById(reporterId)
                .subscribe(
                        reporter -> {
                            if (needDeployment(reporter)) {
                                logger.debug("Reload reporter: {} after configuration update", reporter.getName());
                                auditReporters
                                        .entrySet()
                                        .stream()
                                        .filter(entry -> reporter.getId().equals(entry.getKey().getId()))
                                        .map(Entry::getValue)
                                        .findFirst()
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
                        },
                        error -> logger.error("Unable to reload reporter {}", reporterId, error),
                        () -> logger.error("No reporter found with id {}", reporterId));
    }

    private void loadReporter(io.gravitee.am.model.Reporter reporter) {
        AuditReporterLauncher launcher = new AuditReporterLauncher(reporter);
        var isOrganizationReporter = reporter.getReference().type() == ReferenceType.ORGANIZATION;
        if (isOrganizationReporter) {
            try {
                launcher.accept(new GraviteeContext(reporter.getReference().id(), null, null));
            } catch (Exception ex) {
                logger.error("Unable to load reporter '{}'", reporter.getId(), ex);
            }
        } else {
            var domainId = reporter.getReference().id();
            domainService
                    .findById(domainId)
                    .flatMapSingle(domain -> {
                        if (ReferenceType.ENVIRONMENT.equals(domain.getReferenceType())) {
                            return environmentService.findById(domain.getReferenceId()).map(env -> new GraviteeContext(env.getOrganizationId(), env.getId(), domain.getId()));
                        } else {
                            // currently domain is only linked to domainEnv
                            return Single.error(new EnvironmentNotFoundException("Domain " + domainId +" should be lined to an Environment"));
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .subscribe(launcher, throwable -> logger.error("Unable to load reporter '{}'", reporter.getId(), throwable));
        }

    }

    public class AuditReporterLauncher implements Consumer<GraviteeContext> {
        private io.gravitee.am.model.Reporter reporter;

        public AuditReporterLauncher(io.gravitee.am.model.Reporter reporter) {
            this.reporter = reporter;
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

                    logger.info("Initializing audit reporter : {} for {}", reporter.getName(), reporter.getReference());
                    Reporter eventBusReporter = new EventBusReporterWrapper(vertx, reporter.getReference(), auditReporter);
                    auditReporters.put(reporter, eventBusReporter);
                    reporters.put(reporter.getId(), reporter);
                    try {
                        eventBusReporter.start();
                        AuditReporterVerticle.incrementActiveReporter();
                    } catch (Exception e) {
                        logger.error("Unexpected error while loading reporter", e);
                    }
                } else {
                    // initialize NoOpReporter in order to allow to reload this reporter with valid implementation if it is enabled through the UI
                    auditReporters.put(reporter, new EventBusReporterWrapper(vertx, reporter.getReference(), new NoOpReporter()));
                    reporters.put(reporter.getId(), reporter);
                }
            }
        }
    }

    private void removeReporter(String reporterId) {
        try {
            Optional<Reporter> optionalReporter = auditReporters
                    .entrySet()
                    .stream()
                    .filter(entry -> reporterId.equals(entry.getKey().getId()))
                    .map(Entry::getValue)
                    .findFirst();

            if (optionalReporter.isPresent()) {
                optionalReporter.get().stop();
                auditReporters.entrySet().removeIf(entry -> reporterId.equals(entry.getKey().getId()));
                reporters.remove(reporterId);

                AuditReporterVerticle.decrementActiveReporter();
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
        }, err -> {
            // Could not deploy
            logger.error("Reporter service can not be started", err);
        });
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
