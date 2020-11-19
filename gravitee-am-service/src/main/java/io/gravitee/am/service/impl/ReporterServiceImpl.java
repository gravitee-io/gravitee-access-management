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

import com.google.common.io.BaseEncoding;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ReporterServiceImpl implements ReporterService {

    public static final int TABLE_SUFFIX_MAX_LENGTH = 30;
    public static final String REPORTER_AM_JDBC = "reporter-am-jdbc";
    private final Logger LOGGER = LoggerFactory.getLogger(ReporterServiceImpl.class);
    public static final String ADMIN_DOMAIN = "admin";

    @Autowired
    private Environment environment;

    @Lazy
    @Autowired
    private ReporterRepository reporterRepository;

    @Autowired
    private EventService eventService;

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

    protected boolean useMongoReporter() {
        String managementBackend = this.environment.getProperty("management.type", "mongodb");
        return "mongodb".equalsIgnoreCase(managementBackend);
    }

    protected boolean useJdbcReporter() {
        String managementBackend = this.environment.getProperty("management.type", "mongodb");
        return "jdbc".equalsIgnoreCase(managementBackend);
    }

    @Override
    public Single<Reporter> createDefault(String domain) {
        // get env configuration
        NewReporter newReporter = null;
        if (useMongoReporter()) {
            newReporter = createMongoReporter(domain);
        } else if (useJdbcReporter()) {
            // jdbc
            newReporter = createJdbcReporter(domain);
        } else {
            return Single.error(new ReporterNotFoundException("Reporter type " + this.environment.getProperty("management.type") + " not found"));
        }
        LOGGER.debug("Create default reporter for domain {}", domain);
        return create(domain, newReporter);
    }

    protected NewReporter createMongoReporter(String domain) {
        String mongoHost = environment.getProperty("management.mongodb.host", "localhost");
        String mongoPort = environment.getProperty("management.mongodb.port", "27017");
        String mongoDBName = environment.getProperty("management.mongodb.dbname", "gravitee-am");
        String mongoUri = environment.getProperty("management.mongodb.uri", "mongodb://" + mongoHost + ":" + mongoPort + "/" + mongoDBName);

        NewReporter newReporter = new NewReporter();
        newReporter.setId(RandomString.generate());
        newReporter.setEnabled(true);
        newReporter.setName("MongoDB Reporter");
        newReporter.setType("mongodb");
        newReporter.setConfiguration("{\"uri\":\"" + mongoUri + "\",\"host\":\"" + mongoHost + "\",\"port\":" + mongoPort + ",\"enableCredentials\":false,\"database\":\"" + mongoDBName + "\",\"reportableCollection\":\"reporter_audits_" + domain + "\",\"bulkActions\":1000,\"flushInterval\":5}");

        return newReporter;
    }

    protected NewReporter createJdbcReporter(String domain) {
        String jdbcHost = environment.getProperty("management.jdbc.host");
        String jdbcPort = environment.getProperty("management.jdbc.port");
        String jdbcDatabase = environment.getProperty("management.jdbc.database");
        String jdbcDriver = environment.getProperty("management.jdbc.driver");
        String jdbcUser = environment.getProperty("management.jdbc.username");
        String jdbcPwd = environment.getProperty("management.jdbc.password");

        // dash are forbidden in table name, replace them in domainName by underscore
        String tableSuffix = domain.replaceAll("-", "_");
        if (tableSuffix.length() > TABLE_SUFFIX_MAX_LENGTH) {
            try {
                LOGGER.info("Table name 'reporter_audits_access_points_{}' will be too long, compute shortest unique name", tableSuffix);
                byte[] hash = MessageDigest.getInstance("sha-256").digest(tableSuffix.getBytes());
                tableSuffix = BaseEncoding.base16().encode(hash).substring(0, 30).toLowerCase();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Unable to compute digest of '" + domain + "' due to unknown sha-256 algorithm", e);
            }
        }

        NewReporter newReporter = new NewReporter();
        newReporter.setId(RandomString.generate());
        newReporter.setEnabled(true);
        newReporter.setName("JDBC Reporter");
        newReporter.setType(REPORTER_AM_JDBC);
        newReporter.setConfiguration("{\"host\":\"" + jdbcHost + "\"," +
                "\"port\":" + jdbcPort + "," +
                "\"database\":\"" + jdbcDatabase + "\"," +
                "\"driver\":\"" + jdbcDriver + "\"," +
                "\"username\":\"" + jdbcUser+ "\"," +
                "\"password\":\"" + jdbcPwd + "\"," +
                "\"tableSuffix\":\"" + tableSuffix + "\"," +
                "\"initialSize\":5," +
                "\"maxSize\":10," +
                "\"maxIdleTime\":180000," +
                "\"bulkActions\":1000," +
                "\"flushInterval\":5}");

        return newReporter;
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
                    // create event for sync process
                    Event event = new Event(Type.REPORTER, new Payload(reporter1.getId(), ReferenceType.DOMAIN, reporter1.getDomain(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(reporter1));
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
                                // create event for sync process
                                // except for admin domain
                                if (!ADMIN_DOMAIN.equals(domain)) {
                                    Event event = new Event(Type.REPORTER, new Payload(reporter1.getId(), ReferenceType.DOMAIN, reporter1.getDomain(), Action.UPDATE));
                                    return eventService.create(event).flatMap(__ -> Single.just(reporter1));
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
                    // create event for sync process
                    Event event = new Event(Type.REPORTER, new Payload(reporterId, ReferenceType.DOMAIN, reporter.getDomain(), Action.DELETE));
                    return reporterRepository.delete(reporterId)
                            .andThen(eventService.create(event))
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
