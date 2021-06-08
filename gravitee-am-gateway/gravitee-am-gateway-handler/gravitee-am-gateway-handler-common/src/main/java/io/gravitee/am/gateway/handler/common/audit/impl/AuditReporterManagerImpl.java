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
package io.gravitee.am.gateway.handler.common.audit.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.ReporterEvent;
import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.gateway.handler.common.audit.AuditReporterManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.reporter.core.ReporterPluginManager;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.reporter.impl.AuditReporterVerticle;
import io.gravitee.am.service.reporter.vertx.EventBusReporterWrapper;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.util.function.Tuples;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuditReporterManagerImpl extends AbstractService implements AuditReporterManager, EventListener<ReporterEvent, Payload>, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(AuditReporterManagerImpl.class);
    private String deploymentId;

    @Autowired
    private Domain domain;

    @Autowired
    private ReporterRepository reporterRepository;

    @Autowired
    private ReporterPluginManager reporterPluginManager;

    @Autowired
    private Vertx vertx;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private EnvironmentService environmentService;

    private ConcurrentMap<String, io.gravitee.am.reporter.api.provider.Reporter> reporters = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Initializing reporters for domain {}", domain.getName());
        logger.info("\t Starting reporter verticle for domain {}", domain.getName());

        Single<String> deployment = RxHelper.deployVerticle(vertx, applicationContext.getBean(AuditReporterVerticle.class));
        deployment.subscribe(id -> {
            // Deployed
            deploymentId = id;

            // Start reporters
            reporterRepository.findByDomain(domain.getId()).toList()
                    .flatMap(reporters ->
                            environmentService
                                    .findById(domain.getReferenceId())
                                    .map(env -> new GraviteeContext(env.getOrganizationId(), env.getId(), domain.getId()))
                                    .map(ctx -> Tuples.of(reporters, ctx)))
                    .subscribeOn(Schedulers.io())
                    .subscribe(tupleReportersContext -> {
                                if (!tupleReportersContext.getT1().isEmpty()) {
                                    tupleReportersContext.getT1().forEach(reporter -> {
                                        startReporterProvider(reporter, tupleReportersContext.getT2());
                                    });
                                    logger.info("Reporters loaded for domain {}", domain.getName());
                                } else {
                                    logger.info("\tThere is no reporter to start");
                                }
                            },
                            err -> {
                                logger.error("Reporter service can not be started", err);
                            });
        }, err -> {
            // Could not deploy
            logger.error("Reporter service can not be started", err);
        });
    }

    @Override
    public void onEvent(Event<ReporterEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                    deployReporter(event.content().getId(), event.type());
                    break;
                case UPDATE:
                    updateReporter(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeReporter(event.content().getId());
                    break;
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for reporter events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, ReporterEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for reporter events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, ReporterEvent.class, domain.getId());

        if (deploymentId != null) {
            vertx.undeploy(deploymentId, event -> {
                for(io.gravitee.am.reporter.api.provider.Reporter reporter : reporters.values()) {
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

    private void updateReporter(String reporterId, ReporterEvent reporterEvent) {
        final String eventType = reporterEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} reporter event for {}", domain.getName(), eventType, reporterId);
        reporterRepository.findById(reporterId)
                .flatMapSingle(reporter ->
                        environmentService
                                .findById(domain.getReferenceId())
                                .map(env -> new GraviteeContext(env.getOrganizationId(), env.getId(), domain.getId()))
                                .map(ctx -> Tuples.of(reporter, ctx)))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        tupleReporterContext -> {
                            updateReporterProvider(tupleReporterContext.getT1(), tupleReporterContext.getT2());
                            logger.info("Reporter {} {}d for domain {}", reporterId, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} reporter for domain {}", eventType, domain.getName(), error));
    }

    private void deployReporter(String reporterId, ReporterEvent reporterEvent) {
        final String eventType = reporterEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} reporter event for {}", domain.getName(), eventType, reporterId);
        reporterRepository.findById(reporterId)
                .flatMapSingle(reporter ->
                        environmentService
                                .findById(domain.getReferenceId())
                                .map(env -> new GraviteeContext(env.getOrganizationId(), env.getId(), domain.getId()))
                                .map(ctx -> Tuples.of(reporter, ctx)))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        tupleReporterContext -> {
                            if (reporters.containsKey(reporterId)) {
                                updateReporterProvider(tupleReporterContext.getT1(), tupleReporterContext.getT2());
                            } else {
                                startReporterProvider(tupleReporterContext.getT1(), tupleReporterContext.getT2());
                            }
                            logger.info("Reporter {} {}d for domain {}", reporterId, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} reporter for domain {}", eventType, domain.getName(), error));
    }

    private void removeReporter(String reporterId) {
        logger.info("Domain {} has received reporter event, delete reporter {}", domain.getName(), reporterId);
        io.gravitee.am.reporter.api.provider.Reporter reporter = reporters.remove(reporterId);
        stopReporterProvider(reporterId, reporter);
    }

    private void startReporterProvider(Reporter reporter, GraviteeContext context) {
        if (reporter.isEnabled()) {
            logger.info("\tInitializing reporter: {} [{}]", reporter.getName(), reporter.getType());
            io.gravitee.am.reporter.api.provider.Reporter reporterProvider = reporterPluginManager.create(reporter.getType(), reporter.getConfiguration(), context);

            if (reporterProvider != null) {
                try {
                    logger.info("Starting reporter: {}", reporter.getName());
                    io.gravitee.am.reporter.api.provider.Reporter eventBusReporter = new EventBusReporterWrapper(vertx, domain.getId(), reporterProvider);
                    eventBusReporter.start();
                    reporters.put(reporter.getId(), eventBusReporter);
                } catch (Exception ex) {
                    logger.error("Unexpected error while starting reporter", ex);
                }

            }
        } else {
            logger.info("\tReporter disabled: {} [{}]", reporter.getName(), reporter.getType());
        }
    }

    private void stopReporterProvider(String reporterId, io.gravitee.am.reporter.api.provider.Reporter reporter) {
        if (reporter != null) {
            try {
                reporter.stop();
            } catch (Exception ex) {
                logger.error("Unable to stop reporter: {}", reporterId, ex);
            }
        }
    }

    private void updateReporterProvider(Reporter reporter, GraviteeContext context) {
        stopReporterProvider(reporter.getId(), reporters.get(reporter.getId()));
        startReporterProvider(reporter, context);
    }

    @Override
    public io.gravitee.am.reporter.api.provider.Reporter getReporter() {
       return reporters.entrySet()
                .stream()
                .map(entry -> entry.getValue())
                .filter(reporter -> reporter.canSearch())
                .findFirst()
                .orElse(null);
    }
}
