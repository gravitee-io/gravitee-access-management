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
package io.gravitee.am.service.impl;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.common.event.Action;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.common.event.Type;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.ReporterNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.model.UpdateReporter;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ReporterAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ReporterServiceImpl implements ReporterService {

    private final Logger LOGGER = LoggerFactory.getLogger(ReporterServiceImpl.class);
    public static final String ADMIN_DOMAIN = "admin";

    @Autowired
    private Environment environment;

    @Autowired
    private ReporterRepository reporterRepository;

    @Autowired
    private DomainService domainService;

    @Autowired
    private AuditService auditService;

    @Override
    public Single<List<Reporter>> findAll() {
        LOGGER.debug("Find all reporters");
        return reporterRepository.findAll()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all reporter", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find all reporters", ex));
                });
    }

    @Override
    public Single<List<Reporter>> findByDomain(String domain) {
        LOGGER.debug("Find reporters by domain: {}", domain);
        return reporterRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find reporters by domain: {}", domain, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find reporters by domain: %s", domain), ex));
                });
    }

    @Override
    public Maybe<Reporter> findById(String id) {
        LOGGER.debug("Find reporter by id: {}", id);
        return reporterRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find reporters by id: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(String.format("An error occurs while trying to find reporters by id: %s", id), ex));
                });
    }

    @Override
    public Single<Reporter> createDefault(String domain) {
        // get env configuration
        String mongoHost = environment.getProperty("management.mongodb.host", "localhost");
        String mongoPort =  environment.getProperty("management.mongodb.port", "27017");
        String mongoDBName = environment.getProperty("management.mongodb.dbname", "gravitee-am");
        String mongoUri = environment.getProperty("management.mongodb.uri", "mongodb://"+ mongoHost + ":" + mongoPort + "/" + mongoDBName);

        NewReporter newReporter = new NewReporter();
        newReporter.setId(RandomString.generate());
        newReporter.setEnabled(true);
        newReporter.setName("MongoDB Reporter");
        newReporter.setType("mongodb");
        newReporter.setConfiguration("{\"uri\":\"" + mongoUri + "\",\"host\":\""+ mongoHost + "\",\"port\":" + mongoPort + ",\"enableCredentials\":false,\"database\":\"" + mongoDBName + "\",\"reportableCollection\":\"reporter_audits_" + domain + "\",\"bulkActions\":1000,\"flushInterval\":5}");

        LOGGER.debug("Create default reporter for domain {}", domain);
        return create(domain, newReporter);
    }

    @Override
    public Single<Reporter> create(String domain, NewReporter newReporter, User principal) {
        LOGGER.debug("Create a new reporter {} for domain {}", newReporter, domain);

        Reporter reporter = new Reporter();
        reporter.setId(newReporter.getId() == null ? RandomString.generate() : newReporter.getId());
        reporter.setEnabled(newReporter.isEnabled());
        reporter.setDomain(domain);
        reporter.setName(newReporter.getName());
        reporter.setType(newReporter.getType());
        // currently only audit logs
        reporter.setDataType("AUDIT");
        reporter.setConfiguration(newReporter.getConfiguration());
        reporter.setCreatedAt(new Date());
        reporter.setUpdatedAt(reporter.getCreatedAt());

        return reporterRepository.create(reporter)
                .flatMap(reporter1 -> {
                    // Reload domain to take care about reporter creation
                    Event event = new Event(Type.REPORTER, new Payload(reporter1.getId(), reporter1.getDomain(), Action.CREATE));
                    return domainService.reload(domain, event).flatMap(domain1 -> Single.just(reporter1));
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to create a reporter", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a reporter", ex));
                })
                .doOnSuccess(reporter1 -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_CREATED).reporter(reporter1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_CREATED).throwable(throwable)));
    }

    @Override
    public Single<Reporter> update(String domain, String id, UpdateReporter updateReporter, User principal) {
        LOGGER.debug("Update a reporter {} for domain {}", id, domain);

        return reporterRepository.findById(id)
                .switchIfEmpty(Maybe.error(new ReporterNotFoundException(id)))
                .flatMapSingle(oldReporter -> {
                    Reporter reporterToUpdate = new Reporter(oldReporter);
                    reporterToUpdate.setEnabled(updateReporter.isEnabled());
                    reporterToUpdate.setName(updateReporter.getName());
                    reporterToUpdate.setConfiguration(updateReporter.getConfiguration());
                    reporterToUpdate.setUpdatedAt(new Date());

                    return reporterRepository.update(reporterToUpdate)
                            .flatMap(reporter1 -> {
                                // Reload domain to take care about reporter update
                                // except for admin domain
                                if (!ADMIN_DOMAIN.equals(domain)) {
                                    Event event = new Event(Type.REPORTER, new Payload(reporter1.getId(), reporter1.getDomain(), Action.UPDATE));
                                    return domainService.reload(domain, event).flatMap(domain1 -> Single.just(reporter1));
                                } else {
                                    return Single.just(reporter1);
                                }
                            })
                            .doOnSuccess(reporter1 -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_UPDATED).oldValue(oldReporter).reporter(reporter1)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_UPDATED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update a reporter", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a reporter", ex));
                });
    }

    @Override
    public Completable delete(String reporterId, User principal) {
        LOGGER.debug("Delete reporter {}", reporterId);
        return reporterRepository.findById(reporterId)
                .switchIfEmpty(Maybe.error(new ReporterNotFoundException(reporterId)))
                .flatMapCompletable(reporter -> {
                    // Reload domain to take care about delete reporter
                    Event event = new Event(Type.REPORTER, new Payload(reporterId, reporter.getDomain(), Action.DELETE));
                    return reporterRepository.delete(reporterId)
                            .andThen(domainService.reload(reporter.getDomain(), event))
                            .toCompletable()
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_DELETED).reporter(reporter)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete reporter: {}", reporterId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete reporter: %s", reporterId), ex));
                });
    }
}
