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

package io.gravitee.am.gateway.core.reporter;


import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.ReporterEvent;
import io.gravitee.am.common.utils.GraviteeContext;
import io.gravitee.am.common.utils.GraviteeContextHolder;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.reporter.core.ReporterPluginManager;
import io.gravitee.am.plugins.reporter.core.ReporterProviderConfiguration;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.service.reporter.impl.AuditReporterVerticle;
import io.gravitee.am.service.reporter.vertx.EventBusReporterWrapper;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.gravitee.common.service.Service;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.vertx.rxjava3.core.RxHelper;
import io.vertx.rxjava3.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.gravitee.node.api.Node.META_ORGANIZATIONS;

/**
 * Reporter manager responsible to load global reporters
 * (reporter defined at Organization level with the inherit flag set to true)
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GatewayGlobalReporterManager extends AbstractService<GatewayGlobalReporterManager> implements Service<GatewayGlobalReporterManager>, EventListener<ReporterEvent, Payload>, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(GatewayGlobalReporterManager.class);
    private String deploymentId;

    @Autowired
    @Lazy
    private ReporterRepository reporterRepository;

    @Autowired
    private ReporterPluginManager reporterPluginManager;

    @Autowired
    private Vertx vertx;

    @Autowired
    private EventManager eventManager;

    @Lazy
    @Autowired
    private Node node;

    @Autowired
    private GraviteeContextHolder contextHolder;

    private final ConcurrentMap<String, io.gravitee.am.reporter.api.provider.Reporter> reporterPlugins = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Reporter> reporters = new ConcurrentHashMap<>();

    private Set<String> organizationIds;
    
    @Override
    public void onEvent(Event<ReporterEvent, Payload> event) {
        var content = event.content();
        var affectedReporterIsFromManagedOrganization = content.getReferenceType() == ReferenceType.ORGANIZATION && isOrgManaged(content.getReferenceId());
        if (affectedReporterIsFromManagedOrganization) {
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
        logger.info("Register event listener for global reporter events");
        eventManager.subscribeForEvents(this, ReporterEvent.class);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for global reporter events");
        eventManager.unsubscribeForEvents(this, ReporterEvent.class, null);

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
        logger.info("{} reporter event for {}", eventType, reporterId);
        reporterRepository.findById(reporterId)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        reporter -> {
                            updateReporterProvider(reporter);
                            logger.info("Reporter {} {}d", reporterId, eventType);
                        },
                        error -> logger.error("Unable to {} reporter", eventType, error));
    }

    private void deployReporter(String reporterId, ReporterEvent reporterEvent) {
        final String eventType = reporterEvent.toString().toLowerCase();
        logger.info("{} reporter event for {}", eventType, reporterId);
        reporterRepository.findById(reporterId)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        reporter -> {
                            if (reporterPlugins.containsKey(reporterId)) {
                                updateReporterProvider(reporter);
                            } else {
                                startReporterProvider(reporter);
                            }
                            logger.info("Reporter {} {}d", reporterId, eventType);
                        },
                        error -> logger.error("Unable to {} reporter", eventType, error));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Initializing global reporters");
        logger.debug("\t Starting global reporter verticle");

        this.organizationIds = Optional.ofNullable((Set<String>)this.node.metadata().get(META_ORGANIZATIONS)).orElse(Set.of());
        
        Single<String> deployment = RxHelper.deployVerticle(vertx, applicationContext.getBean(AuditReporterVerticle.class));
        deployment.subscribe(id -> {
            // Deployed
            deploymentId = id;

            // Start reporters
            findGlobalReporters()
                    .subscribeOn(Schedulers.io())
                    .subscribe(reporter -> {
                                if (reporter.isInherited()) {
                                    startReporterProvider(reporter);
                                    logger.info("Reporters loaded for organization {}", reporter.getName());
                                } else {
                                    logger.info("\tReporter {} is not inherited, no reporter to start", reporter.getName());
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

     private Flowable<Reporter> findGlobalReporters() {
        return reporterRepository.findByReferenceType(ReferenceType.ORGANIZATION)
                .filter(Reporter::isInherited)
                .filter(reporter -> isOrgManaged(reporter.getId()) );
    }

    private boolean isOrgManaged(String orgId) {
        return organizationIds.isEmpty() || organizationIds.contains(orgId);
    }

    private void startReporterProvider(Reporter reporter) {
        if (!reporter.isEnabled()) {
            logger.info("\tReporter disabled: {} [{}]", reporter.getName(), reporter.getType());
            return;
        }
        if (!needDeployment(reporter)) {
            logger.info("Reporter {} already up to date", reporter.getId());
            return;
        }
        logger.info("\tInitializing reporter: {} [{}]", reporter.getName(), reporter.getType());
        var providerConfiguration = new ReporterProviderConfiguration(reporter, new GraviteeContext(reporter.getReference().id(), null, null));
        io.gravitee.am.reporter.api.provider.Reporter reporterProvider = reporterPluginManager.create(providerConfiguration);

        if (reporterProvider != null) {
            try {
                logger.info("Starting reporter: {}", reporter.getName());
                io.gravitee.am.reporter.api.provider.Reporter eventBusReporter = new EventBusReporterWrapper(vertx, reporterProvider, reporter.getReference(), contextHolder);
                eventBusReporter.start();
                reporters.put(reporter.getId(), reporter);
                reporterPlugins.put(reporter.getId(), eventBusReporter);
                AuditReporterVerticle.incrementActiveReporter();
            } catch (Exception ex) {
                logger.error("Unexpected error while starting reporter", ex);
            }
        }
    }

    private void stopReporterProvider(String reporterId, io.gravitee.am.reporter.api.provider.Reporter reporter) {
        if (reporter != null) {
            try {
                reporter.stop();
                AuditReporterVerticle.decrementActiveReporter();
            } catch (Exception ex) {
                logger.error("Unable to stop reporter: {}", reporterId, ex);
            }
        }
    }

    private void updateReporterProvider(Reporter reporter) {
        if (this.reporters.containsKey(reporter.getId())) {
            if (needDeployment(reporter)) {
                stopReporterProvider(reporter.getId(), reporterPlugins.get(reporter.getId()));
                startReporterProvider(reporter);
            } else {
                logger.info("Reporter {} already up to date", reporter.getId());
            }
        } else {
            logger.info("Reporter {} is no managed by global reporter manager, ignore the update command", reporter.getId());
        }
    }


    private void removeReporter(String reporterId) {
        if (this.reporters.containsKey(reporterId)) {
            logger.info("Received global reporter event, delete reporter {}", reporterId);
            reporters.remove(reporterId);
            io.gravitee.am.reporter.api.provider.Reporter reporter = reporterPlugins.remove(reporterId);
            stopReporterProvider(reporterId, reporter);
        } else {
            logger.info("Reporter {} is no managed by global reporter manager, ignore the remove command", reporterId);
        }
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
