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
import io.gravitee.am.gateway.handler.common.service.DomainAwareListener;
import io.gravitee.am.gateway.handler.common.utils.Tuple;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.reporter.core.ReporterPluginManager;
import io.gravitee.am.plugins.reporter.core.ReporterProviderConfiguration;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.reporter.impl.AuditReporterVerticle;
import io.gravitee.am.service.reporter.vertx.EventBusReporterWrapper;
import io.gravitee.common.event.Event;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.rxjava3.core.RxHelper;
import io.vertx.rxjava3.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GatewayAuditReporterManager extends AbstractService<AuditReporterManager> implements AuditReporterManager, DomainAwareListener<ReporterEvent, Payload>, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(GatewayAuditReporterManager.class);
    private String deploymentId;

    private String organizationId;
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

    @Autowired
    private io.gravitee.am.monitoring.DomainReadinessService domainReadinessService;

    private final ConcurrentMap<String, io.gravitee.am.reporter.api.provider.Reporter> reporterPlugins = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Reporter> reporters = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Initializing reporters for domain {}", domain.getName());
        logger.info("\t Starting reporter verticle for domain {}", domain.getName());

        Single<String> deployment = RxHelper.deployVerticle(vertx, applicationContext.getBean(AuditReporterVerticle.class));
        deployment.subscribe(id -> {
            // Deployed
            deploymentId = id;

            // Start reporters
            getEnvironment()
                    .flatMap(env -> findRelevantReporters(env)
                            .map(foundReporters -> Tuple.of(foundReporters, new GraviteeContext(env.getOrganizationId(), env.getId(), domain.getId()))))
                    .subscribeOn(Schedulers.io())
                    .subscribe(tupleReportersContext -> {
                                if (!tupleReportersContext.getT1().isEmpty()) {
                                    this.organizationId = tupleReportersContext.getT2().getOrganizationId();
                                    tupleReportersContext.getT1().forEach(reporter -> startReporterProvider(reporter, tupleReportersContext.getT2()));

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

    private Single<Environment> getEnvironment() {
        return environmentService.findById(domain.getReferenceId());
    }

    private Single<List<Reporter>> findRelevantReporters(Environment env) {
        return reporterRepository.findInheritedFrom(Reference.organization(env.getOrganizationId()))
                .mergeWith(reporterRepository.findByReference(Reference.domain(domain.getId())))
                .toList();
    }

    @Override
    public void onEvent(Event<ReporterEvent, Payload> event) {
        var content = event.content();
        var affectedReporterIsFromThisDomain = content.getReferenceType() == ReferenceType.DOMAIN && content.getReferenceId().equals(domain.getId());
        var affectedReporterIsFromThisOrganization = content.getReferenceType() == ReferenceType.ORGANIZATION && content.getReferenceId().equals(organizationId);
        if (affectedReporterIsFromThisDomain || affectedReporterIsFromThisOrganization) {
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
            vertx.rxUndeploy(deploymentId)
                    .doFinally(() -> {
                        for (io.gravitee.am.reporter.api.provider.Reporter reporter : reporterPlugins.values()) {
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

    private void updateReporter(String reporterId, ReporterEvent reporterEvent) {
        final String eventType = reporterEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} reporter event for {}", domain.getName(), eventType, reporterId);
        reporterRepository.findById(reporterId)
                .flatMapSingle(reporter ->
                        environmentService
                                .findById(domain.getReferenceId())
                                .map(env -> new GraviteeContext(env.getOrganizationId(), env.getId(), domain.getId()))
                                .map(ctx -> Tuple.of(reporter, ctx)))
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
                                .map(ctx -> Tuple.of(reporter, ctx)))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        tupleReporterContext -> {
                            boolean loaded = reporterPlugins.containsKey(reporterId) ?
                                    updateReporterProvider(tupleReporterContext.getT1(), tupleReporterContext.getT2()):
                                    startReporterProvider(tupleReporterContext.getT1(), tupleReporterContext.getT2());
                            if(loaded) {
                                logger.info("Reporter {} {}d for domain {}", reporterId, eventType, domain.getName());
                            }
                        },
                        error -> logger.error("Unable to {} reporter for domain {}", eventType, domain.getName(), error));
    }

    private void removeReporter(String reporterId) {
        logger.info("Domain {} has received reporter event, delete reporter {}", domain.getName(), reporterId);
        reporters.remove(reporterId);
        io.gravitee.am.reporter.api.provider.Reporter reporter = reporterPlugins.remove(reporterId);
        stopReporterProvider(reporterId, reporter);
    }

    private boolean startReporterProvider(Reporter reporter, GraviteeContext context) {
        if (!reporter.isEnabled()) {
            logger.info("\tReporter disabled: {} [{}]", reporter.getName(), reporter.getType());
            return false;
        }
        if (!needDeployment(reporter)) {
            logger.info("Reporter {} already up to date for Domain {}", reporter.getId(), domain.getName());
            domainReadinessService.updatePluginStatus(domain.getId(), reporter.getId(), reporter.getName(), true, null);
            return false;
        }
        if (reporter.getReference().type() == ReferenceType.ORGANIZATION && !reporter.isInherited()) {
            logger.info("Reporter {} [{}] is linked to the organization {} but is not inherited, won't be started", reporter.getId(), reporter.getType(), organizationId);
            return false;
        }
        logger.info("\tInitializing reporter: {} [{}]", reporter.getName(), reporter.getType());
        var providerConfiguration = new ReporterProviderConfiguration(reporter, context);
        io.gravitee.am.reporter.api.provider.Reporter reporterProvider = reporterPluginManager.create(providerConfiguration);

        if (reporterProvider != null) {
            try {
                logger.info("Starting reporter: {}", reporter.getName());
                io.gravitee.am.reporter.api.provider.Reporter eventBusReporter = new EventBusReporterWrapper(vertx, reporterProvider, Reference.domain(domain.getId()));
                eventBusReporter.start();
                reporters.put(reporter.getId(), reporter);
                reporterPlugins.put(reporter.getId(), eventBusReporter);
                domainReadinessService.updatePluginStatus(domain.getId(), reporter.getId(), reporter.getName(), true, null);
            } catch (Exception ex) {
                logger.error("Unexpected error while starting reporter", ex);
                domainReadinessService.updatePluginStatus(domain.getId(), reporter.getId(), reporter.getName(), false, ex.getMessage());
                return false;
            }

        }
        return true;
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

    private boolean updateReporterProvider(Reporter reporter, GraviteeContext context) {
        if (needDeployment(reporter)) {
            stopReporterProvider(reporter.getId(), reporterPlugins.get(reporter.getId()));
            return startReporterProvider(reporter, context);
        } else {
            logger.info("Reporter {} already up to date for Domain {}", reporter.getId(), domain.getName());
            domainReadinessService.updatePluginStatus(domain.getId(), reporter.getId(), reporter.getName(), true, null);
            return false;
        }
    }

    @Override
    public io.gravitee.am.reporter.api.provider.Reporter getReporter() {
        return reporterPlugins.values()
                .stream()
                .filter(io.gravitee.am.reporter.api.provider.Reporter::canSearch)
                .findFirst()
                .orElse(null);
    }

    @Override
    public String getDomainId() {
        return domain.getId();
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
