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

import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.plugins.reporter.core.ReporterPluginManager;
import io.gravitee.am.reporter.api.provider.Reporter;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.exception.ReporterNotFoundForDomainException;
import io.gravitee.am.service.reporter.impl.AuditReporterVerticle;
import io.gravitee.am.service.reporter.vertx.EventBusReporterWrapper;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Single;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AuditReporterManagerImpl extends AbstractService<AuditReporterManager>  implements AuditReporterManager {

    public static final Logger logger = LoggerFactory.getLogger(AuditReporterManagerImpl.class);
    private String deploymentId;

    @Autowired
    private ReporterPluginManager reporterPluginManager;

    @Autowired
    private ReporterService reporterService;

    @Autowired
    private Vertx vertx;

    @Autowired
    private ApplicationContext applicationContext;

    private ConcurrentMap<io.gravitee.am.model.Reporter, Reporter> auditReporters = new ConcurrentHashMap<>();

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        reporterService.findAll()
                .subscribe(reporters -> {
                    reporters.forEach(reporter -> {
                        Reporter auditReporter = reporterPluginManager.create(reporter.getType(), reporter.getConfiguration());
                        if (auditReporter != null) {
                            logger.info("Initializing audit reporter provider : {} for domain {}", reporter.getName(), reporter.getDomain());
                            auditReporters.put(reporter, new EventBusReporterWrapper(vertx, reporter.getDomain(), auditReporter));
                        }
                    });
                    deployReporterVerticle(auditReporters.values());
                });
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (deploymentId != null) {
            vertx.undeploy(deploymentId, event -> {
                for(io.gravitee.reporter.api.Reporter reporter: auditReporters.values()) {
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
    protected String name() {
        return "AM Management API Reporter service";
    }

    @Override
    public Reporter getReporter(String domain) {
        return auditReporters
                .entrySet()
                .stream()
                .filter(entry -> domain.equals(entry.getKey().getDomain()))
                .map(entry -> entry.getValue())
                .findFirst()
                .orElseThrow(() -> new ReporterNotFoundForDomainException(domain));
    }

    @Override
    public void reloadReporter(io.gravitee.am.model.Reporter reporter) {
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
    }

    @Override
    public void loadReporter(io.gravitee.am.model.Reporter reporter) {
        Reporter auditReporter = reporterPluginManager.create(reporter.getType(), reporter.getConfiguration());
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

    @Override
    public void removeReporter(String domain) {
        try {
            Reporter reporter = getReporter(domain);
            reporter.stop();
            auditReporters.entrySet().removeIf(entry -> entry.getKey().getDomain().equals(((EventBusReporterWrapper) reporter).getDomain()));
        } catch (Exception e) {
            logger.error("Unexpected error while removing reporter", e);
        }

    }

    private void deployReporterVerticle(Collection<Reporter> reporters) {
        Single<String> deployment = RxHelper.deployVerticle(vertx, applicationContext.getBean(AuditReporterVerticle.class));

        deployment.subscribe(id -> {
            // Deployed
            deploymentId = id;
            if (! reporters.isEmpty()) {
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
