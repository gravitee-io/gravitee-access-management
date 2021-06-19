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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.reporter.core.ReporterPluginManager;
import io.gravitee.am.reporter.api.provider.NoOpReporter;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.am.service.exception.ReporterNotFoundForDomainException;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.reporter.impl.AuditReporterVerticle;
import io.gravitee.am.service.reporter.vertx.EventBusReporterWrapper;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;
import io.reactivex.schedulers.Schedulers;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
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

    private ConcurrentMap<io.gravitee.am.model.Reporter, Reporter> auditReporters = new ConcurrentHashMap<>();

    private Reporter internalReporter;

    private Reporter noOpReporter;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for reporter events for the management API");
        eventManager.subscribeForEvents(this, ReporterEvent.class);

        // init noOpReporter
        noOpReporter = new NoOpReporter();

        // init internal reporter (organization reporter)
        NewReporter organizationReporter = reporterService.createInternal();
        logger.info("Initializing internal " + organizationReporter.getType() + " audit reporter");
        internalReporter = reporterPluginManager.create(organizationReporter.getType(), organizationReporter.getConfiguration(), null);
        logger.info("Internal audit " + organizationReporter.getType() + " reporter initialized");

        logger.info("Initializing audit reporters");
        reporterService.findAll().blockingForEach(reporter -> {
            logger.info("Initializing audit reporter : {} for domain {}", reporter.getName(), reporter.getDomain());
            try {
                AuditReporterLauncher launcher = new AuditReporterLauncher(reporter);
                domainService
                        .findById(reporter.getDomain())
                        .flatMapSingle(domain -> {
                            if (ReferenceType.ENVIRONMENT.equals(domain.getReferenceType())) {
                                return environmentService.findById(domain.getReferenceId()).map(env -> new GraviteeContext(env.getOrganizationId(), env.getId(), domain.getId()));
                            } else {
                                // currently domain is only linked to domainEnv
                                return Single.error(new EnvironmentNotFoundException("Domain " + reporter.getDomain() +" should be lined to an Environment"));
                            }
                        })
                        .subscribeOn(Schedulers.io())
                        .subscribe(launcher);
            } catch (Exception ex) {
                logger.error("An error has occurred while loading audit reporter: {} [{}]", reporter.getName(), reporter.getType(), ex);
                removeReporter(reporter.getId());
            }
        });

        // deploy internal reporter verticle
        deployReporterVerticle(asList(new EventBusReporterWrapper(vertx, internalReporter)));
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (deploymentId != null) {
            vertx.undeploy(deploymentId, event -> {
                for (io.gravitee.reporter.api.Reporter reporter : auditReporters.values()) {
                    try {
                        logger.info("Stopping reporter: {}", reporter);
                        reporter.stop();
                    } catch (Exception ex) {
                        logger.error("Unexpected error while stopping reporter", ex);
                    }
                }
            });
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

        if (referenceType == ReferenceType.DOMAIN) {
            return doGetReporter(referenceId);
        } else {
            // Internal reporter must be use for all other resources.
            return internalReporter;
        }
    }

    @Override
    public Reporter getReporter(String domain) {
        return doGetReporter(domain);
    }

    private Reporter doGetReporter(String domain) {
        Optional<Reporter> optionalReporter = auditReporters
                .entrySet()
                .stream()
                .filter(entry -> domain.equals(entry.getKey().getDomain()))
                .map(entry -> entry.getValue())
                .filter(reporter -> reporter.canSearch())
                .findFirst();

        if (optionalReporter.isPresent()) {
            return optionalReporter.get();
        }

        // reporter can be missing as it can take sometime for the reporter events
        // to propagate across the cluster so if there are at least one reporter for the domain, return the NoOpReporter to avoid
        // too long waiting time that may lead to unexpected even on the UI.
        try {
            List<io.gravitee.am.model.Reporter> reporters = reporterService.findByDomain(domain).toList().blockingGet();
            if (reporters.isEmpty()) {
                throw new ReporterNotFoundForDomainException(domain);
            }
            logger.warn("Reporter for domain {} isn't bootstrapped yet", domain);
            return noOpReporter;
        } catch (Exception ex) {
            logger.error("An error has occurred while fetching reporter for domain {}", domain, ex);
            throw new IllegalStateException(ex);
        }
    }

    private void deployReporter(String reporterId) {
        logger.info("Management API has received a deploy reporter event for {}", reporterId);
        reporterService.findById(reporterId)
                .subscribe(
                        reporter -> loadReporter(reporter),
                        error -> logger.error("Unable to deploy reporter {}", reporterId, error),
                        () -> logger.error("No reporter found with id {}", reporterId));
    }

    private void reloadReporter(String reporterId) {
        logger.info("Management API has received an update reporter event for {}", reporterId);
        reporterService.findById(reporterId)
                .subscribe(
                        reporter -> {
                            logger.debug("Reload reporter: {} after configuration update", reporter.getName());
                            Optional<Reporter> optionalAuditReporter = auditReporters
                                    .entrySet()
                                    .stream()
                                    .filter(entry -> reporter.getId().equals(entry.getKey().getId()))
                                    .map(entry -> entry.getValue())
                                    .findFirst();

                            if (optionalAuditReporter.isPresent()) {
                                try {
                                    Reporter auditReporter = optionalAuditReporter.get();
                                    // reload the provider if it's enabled
                                    if (reporter.isEnabled()) {
                                        auditReporter.stop();
                                        auditReporters.entrySet().removeIf(entry -> entry.getKey().getId().equals(reporter.getId()));
                                        loadReporter(reporter);
                                    } else {
                                        logger.info("Reporter: {} has been disabled", reporter.getName());
                                        // unregister event bus consumer
                                        // we do not stop the underlying reporter because it can be used to fetch reportable
                                        ((EventBusReporterWrapper) auditReporter).unregister();
                                    }
                                } catch (Exception e) {
                                    logger.error("An error occurs while reloading reporter: {}", reporter.getName(), e);
                                }
                            } else {
                                logger.info("There is no reporter to reload");
                            }
                        },
                        error -> logger.error("Unable to reload reporter {}", reporterId, error),
                        () -> logger.error("No reporter found with id {}", reporterId));
    }

    private void loadReporter(io.gravitee.am.model.Reporter reporter) {
        AuditReporterLauncher launcher = new AuditReporterLauncher(reporter);
        domainService
                .findById(reporter.getDomain())
                .flatMapSingle(domain -> {
                    if (ReferenceType.ENVIRONMENT.equals(domain.getReferenceType())) {
                        return environmentService.findById(domain.getReferenceId()).map(env -> new GraviteeContext(env.getOrganizationId(), env.getId(), domain.getId()));
                    } else {
                        // currently domain is only linked to domainEnv
                        return Single.error(new EnvironmentNotFoundException("Domain " + reporter.getDomain() +" should be lined to an Environment"));
                    }
                })
                .subscribeOn(Schedulers.io())
                .subscribe(launcher);
    }

    public class AuditReporterLauncher implements BiConsumer<GraviteeContext, Throwable> {
        private io.gravitee.am.model.Reporter reporter;

        private Throwable error;

        public AuditReporterLauncher(io.gravitee.am.model.Reporter reporter) {
            this.reporter = reporter;
        }

        @Override
        public void accept(GraviteeContext graviteeContext, Throwable throwable) throws Exception {
            if (graviteeContext != null) {
                Reporter auditReporter = reporterPluginManager.create(reporter.getType(), reporter.getConfiguration(), graviteeContext);
                if (auditReporter != null) {
                    logger.info("Initializing audit reporter : {} for domain {}", reporter.getName(), reporter.getDomain());
                    Reporter eventBusReporter = new EventBusReporterWrapper(vertx, reporter.getDomain(), auditReporter);
                    auditReporters.put(reporter, eventBusReporter);
                    try {
                        eventBusReporter.start();
                    } catch (Exception e) {
                        logger.error("Unexpected error while loading reporter", e);
                    }
                }
            }

            if (throwable != null) {
                logger.error("Unable to load reporter '{}'", reporter.getId(), throwable);
                this.error = throwable;
            }
        }

        public boolean failed() {
            return error != null;
        }

        public Throwable getError() {
            return error;
        }
    }

    private void removeReporter(String reporterId) {
        try {
            Optional<Reporter> optionalReporter = auditReporters
                    .entrySet()
                    .stream()
                    .filter(entry -> reporterId.equals(entry.getKey().getId()))
                    .map(entry -> entry.getValue())
                    .findFirst();

            if (optionalReporter.isPresent()) {
                optionalReporter.get().stop();
                auditReporters.entrySet().removeIf(entry -> reporterId.equals(entry.getKey().getId()));
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
}
