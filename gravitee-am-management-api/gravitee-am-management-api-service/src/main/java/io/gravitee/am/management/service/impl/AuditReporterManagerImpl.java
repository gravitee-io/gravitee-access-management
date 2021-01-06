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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.reporter.core.ReporterPluginManager;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.am.service.exception.ReporterNotFoundForDomainException;
import io.gravitee.am.service.impl.ReporterServiceImpl;
import io.gravitee.am.service.reporter.impl.AuditReporterVerticle;
import io.gravitee.am.service.reporter.vertx.EventBusReporterWrapper;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Single;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AuditReporterManagerImpl extends AbstractService<AuditReporterManager> implements AuditReporterManager, EventListener<ReporterEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(AuditReporterManagerImpl.class);
    private static final long retryTimeout = 10000;
    private String deploymentId;

    @Autowired
    private Environment environment;

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

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for reporter events for the management API");
        eventManager.subscribeForEvents(this, ReporterEvent.class);

        if (useMongoReporter()) {
            logger.info("Initializing internal audit mongodb reporter");
            String mongoHost = environment.getProperty("management.mongodb.host", "localhost");
            String mongoPort = environment.getProperty("management.mongodb.port", "27017");
            String mongoDBName = environment.getProperty("management.mongodb.dbname", "gravitee-am");
            String mongoUri = environment.getProperty("management.mongodb.uri", "mongodb://" + mongoHost + ":" + mongoPort + "/" + mongoDBName);
            String configuration = "{\"uri\":\"" + mongoUri + "\",\"host\":\"" + mongoHost + "\",\"port\":" + mongoPort + ",\"enableCredentials\":false,\"database\":\"" + mongoDBName + "\",\"reportableCollection\":\"reporter_audits" + "\",\"bulkActions\":1000,\"flushInterval\":5}";
            internalReporter = reporterPluginManager.create("mongodb", configuration, null);
            logger.info("Internal audit mongodb reporter initialized");
        } else if (useJdbcReporter()) {
            logger.info("Initializing internal audit jdbc reporter");
            String jdbcHost = environment.getProperty("management.jdbc.host");
            String jdbcPort = environment.getProperty("management.jdbc.port");
            String jdbcDatabase = environment.getProperty("management.jdbc.database");
            String jdbcDriver = environment.getProperty("management.jdbc.driver");
            String jdbcUser = environment.getProperty("management.jdbc.username");
            String jdbcPwd = environment.getProperty("management.jdbc.password");

            String configuration = "{\"host\":\"" + jdbcHost + "\"," +
                    "\"port\":" + jdbcPort + "," +
                    "\"database\":\"" + jdbcDatabase + "\"," +
                    "\"driver\":\"" + jdbcDriver + "\"," +
                    "\"username\":\"" + jdbcUser+ "\"," +
                    "\"password\":\"" + jdbcPwd + "\"," +
                    "\"tableSuffix\":\"\"," + // empty domain
                    "\"initialSize\":5," +
                    "\"maxSize\":10," +
                    "\"maxIdleTime\":180000," +
                    "\"bulkActions\":1000," +
                    "\"flushInterval\":5}";

            internalReporter = reporterPluginManager.create(ReporterServiceImpl.REPORTER_AM_JDBC, configuration, null);
            logger.info("Internal audit jdbc reporter initialized");
        }

        logger.info("Initializing audit reporters");
        List<io.gravitee.am.model.Reporter> reporters = reporterService.findAll().blockingGet();
        Map<String, Domain> localDomainCache = new HashMap<>();
        Map<String, io.gravitee.am.model.Environment> localEnvCache = new HashMap<>();
        reporters.forEach(reporter -> {
            logger.info("Initializing audit reporter : {} for domain {}", reporter.getName(), reporter.getDomain());
            try {
                Domain domain = localDomainCache.get(reporter.getDomain());
                if (domain == null) {
                    domain = domainService.findById(reporter.getDomain()).blockingGet();
                    localDomainCache.put(reporter.getDomain(), domain);
                }

                io.gravitee.am.model.Environment domainEnv = localEnvCache.get(domain.getReferenceId());
                if (ReferenceType.ENVIRONMENT.equals(domain.getReferenceType())) {
                    if (domainEnv == null) {
                        domainEnv = environmentService.findById(domain.getReferenceId()).blockingGet();
                        localEnvCache.put(domain.getReferenceId(), domainEnv);
                    }
                } else {
                    // currently domain is only linked to domainEnv
                    throw new EnvironmentNotFoundException("Domain " + reporter.getDomain() +" should be lined to an Environment");
                }

                Reporter auditReporter = reporterPluginManager.create(
                        reporter.getType(),
                        reporter.getConfiguration(),
                        new GraviteeContext(domainEnv.getOrganizationId(), domainEnv.getId(), domain.getId()));
                if (auditReporter != null) {
                    logger.info("Initializing audit reporter : {} for domain {}", reporter.getName(), reporter.getDomain());
                    auditReporters.put(reporter, new EventBusReporterWrapper(vertx, reporter.getDomain(), auditReporter));
                }
            } catch (Exception ex) {
                logger.error("An error has occurred while loading audit reporter: {} [{}]", reporter.getName(), reporter.getType(), ex);
                removeReporter(reporter.getId());
            }
        });

        // deploy verticle
        List<Reporter> allReporters = new ArrayList<>(auditReporters.values());
        if (internalReporter != null) {
            allReporters.add(new EventBusReporterWrapper(vertx, internalReporter));
        }
        deployReporterVerticle(allReporters);
    }

    protected boolean useMongoReporter() {
        String managementBackend = this.environment.getProperty("management.type", "mongodb");
        return "mongodb".equalsIgnoreCase(managementBackend);
    }

    protected boolean useJdbcReporter() {
        String managementBackend = this.environment.getProperty("management.type", "mongodb");
        return "jdbc".equalsIgnoreCase(managementBackend);
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
            return doGetReporter(referenceId, System.currentTimeMillis());
        } else {
            // Internal reporter must be use for all other resources.
            return internalReporter;
        }
    }

    @Override
    public Reporter getReporter(String domain) {
        return doGetReporter(domain, System.currentTimeMillis());
    }

    private Reporter doGetReporter(String domain, long startTime) {
        Optional<Reporter> optionalReporter = doGetReporter0(domain);
        if (optionalReporter.isPresent()) {
            return optionalReporter.get();
        }

        // reporter can be missing as it can take sometime for the reporter events
        // to propagate across the cluster so if the next call comes
        // in quickly at a different node there is a possibility it isn't available yet.
        try {
            List<io.gravitee.am.model.Reporter> reporters = reporterService.findByDomain(domain).blockingGet();
            if (reporters.isEmpty()) {
                throw new ReporterNotFoundForDomainException(domain);
            }
            // retry
            while (!optionalReporter.isPresent() && System.currentTimeMillis() - startTime < retryTimeout) {
                optionalReporter = doGetReporter0(domain);
            }
            if (optionalReporter.isPresent()) {
                return optionalReporter.get();
            } else {
                throw new ReporterNotFoundForDomainException(domain);
            }
        } catch (Exception ex) {
            logger.error("An error has occurred while fetching reporter for domain {}", domain, ex);
            throw new IllegalStateException(ex);
        }
    }

    private Optional<Reporter> doGetReporter0(String domain) {
        return auditReporters
                .entrySet()
                .stream()
                .filter(entry -> domain.equals(entry.getKey().getDomain()))
                .map(entry -> entry.getValue())
                .filter(reporter -> reporter.canSearch() )
                .findFirst();
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
        Domain domain = domainService.findById(reporter.getDomain()).blockingGet();
        io.gravitee.am.model.Environment domainEnv;
        if (ReferenceType.ENVIRONMENT.equals(domain.getReferenceType())) {
            domainEnv = environmentService.findById(domain.getReferenceId()).blockingGet();
        } else {
            // currently domain is only linked to domainEnv
            throw new EnvironmentNotFoundException("Domain " + reporter.getDomain() +" should be lined to an Environment");
        }

        Reporter auditReporter = reporterPluginManager.create(reporter.getType(), reporter.getConfiguration(), new GraviteeContext(domainEnv.getOrganizationId(), domainEnv.getId(), domain.getId()));
        if (auditReporter != null) {
            Reporter eventBusReporter = new EventBusReporterWrapper(vertx, reporter.getDomain(), auditReporter);
            auditReporters.put(reporter, eventBusReporter);
            try {
                eventBusReporter.start();
            } catch (Exception e) {
                logger.error("Unexpected error while loading reporter", e);
            }
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
