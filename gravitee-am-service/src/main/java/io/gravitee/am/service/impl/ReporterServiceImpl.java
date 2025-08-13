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

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.PluginConfigurationValidationService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.ReporterConfigurationException;
import io.gravitee.am.service.exception.ReporterDeleteException;
import io.gravitee.am.service.exception.ReporterNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.model.UpdateReporter;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ReporterAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.gravitee.am.service.utils.BackendConfigurationUtils.getMongoDatabaseName;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Primary
public class ReporterServiceImpl implements ReporterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReporterServiceImpl.class);
    private static final int TABLE_SUFFIX_MAX_LENGTH = 30;
    private static final String REPORTER_AM_JDBC = "reporter-am-jdbc";
    public static final String REPORTER_AM_FILE = "reporter-am-file";
    public static final String REPORTER_CONFIG_FILENAME = "filename";
    public static final String REPORTER_CONFIG_RETAIN_DAYS = "retainDays";
    public static final String MANAGEMENT_TYPE = Scope.MANAGEMENT.getRepositoryPropertyKey() + ".type";
    public static final String MONGODB = "mongodb";
    // Regex as defined into the Reporter plugin schema in order to apply the same validation rule
    // when a REST call is performed and not only check on the UI
    private final Pattern filenamePattern = Pattern.compile("^([A-Za-z0-9][A-Za-z0-9\\-_.]*)$");
    private final Pattern retainDaysPattern = Pattern.compile("^[1-9]\\d*$");

    private RepositoriesEnvironment environment;

    private ReporterRepository reporterRepository;

    private EventService eventService;

    private AuditService auditService;

    private PluginConfigurationValidationService validationService;

    public ReporterServiceImpl(RepositoriesEnvironment environment, @Lazy ReporterRepository reporterRepository, EventService eventService, AuditService auditService, PluginConfigurationValidationService validationService) {
        this.environment = environment;
        this.reporterRepository = reporterRepository;
        this.eventService = eventService;
        this.auditService = auditService;
        this.validationService = validationService;
    }


    @Override
    public Flowable<Reporter> findAll() {
        LOGGER.debug("Find all reporters");
        return reporterRepository.findAll()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all reporter", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find all reporters", ex));
                });
    }

    @Override
    public Flowable<Reporter> findByReference(Reference reference) {
        LOGGER.debug("Find reporters for: {}", reference);
        return reporterRepository.findByReference(reference)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find reporters by domain: {}", reference, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find reporters by domain: %s", reference), ex));
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
    public Single<Reporter> createDefault(Reference reference) {
        LOGGER.debug("Create default reporter for  {}", reference);
        NewReporter newReporter = createInternal(reference);
        if (newReporter == null) {
            return Single.error(new ReporterNotFoundException("Reporter type " + this.environment.getProperty(MANAGEMENT_TYPE) + " not found"));
        }
        return create(reference, newReporter);
    }

    @Override
    public NewReporter createInternal(Reference reference) {
        NewReporter newReporter = null;
        if (useMongoReporter()) {
            newReporter = createMongoReporter(reference);
        } else if (useJdbcReporter()) {
            newReporter = createJdbcReporter(reference);
        }
        return newReporter;
    }

    @Override
    public Single<Reporter> create(Reference reference, NewReporter newReporter, User principal, boolean system) {
        LOGGER.debug("Create a new reporter {} for {}", newReporter, reference);

        var now = new Date();
        if (reference.type() != ReferenceType.ORGANIZATION && newReporter.isInherited()) {
            return Single.error(new ReporterConfigurationException("Only organization reporters can be inherited"));
        }
        Reporter reporter = Reporter.builder()
                .id(Objects.requireNonNullElseGet(newReporter.getId(), RandomString::generate))
                .enabled(newReporter.isEnabled())
                .reference(reference)
                .name(newReporter.getName())
                .system(system)
                .type(newReporter.getType())
                .inherited(reference.type() == ReferenceType.ORGANIZATION && newReporter.isInherited())
                .dataType("AUDIT")
                .configuration(newReporter.getConfiguration())
                .createdAt(now)
                .updatedAt(now)
                .build();


        return checkReporterConfiguration(reporter)
                .flatMap(ignore -> reporterRepository.create(reporter))
                .flatMap(createdReporter -> {
                    // create event for sync process
                    Event event = new Event(Type.REPORTER, new Payload(createdReporter.getId(), createdReporter.getReference(), Action.CREATE));
                    return eventService.create(event).flatMap(e -> Single.just(createdReporter));
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to create a reporter", ex);
                    String message = "An error occurs while trying to create a reporter. ";
                    if (ex instanceof ReporterConfigurationException) {
                        message += ex.getMessage();
                    }
                    return Single.error(new TechnicalManagementException(message, ex));
                });
    }


    @Override
    public Single<Reporter> update(Reference reference, String reporterId, UpdateReporter updateReporter, User principal, boolean isUpgrader) {
        LOGGER.debug("Update a reporter {} for {}", reporterId, reference);

        return reporterRepository.findById(reporterId)
                .switchIfEmpty(Single.error(new ReporterNotFoundException(reporterId)))
                .flatMap(oldReporter -> {
                    Reporter reporterToUpdate = new Reporter(oldReporter);
                    reporterToUpdate.setEnabled(updateReporter.isEnabled());

                    reporterToUpdate.setName(updateReporter.getName());
                    if (!oldReporter.isSystem() || isUpgrader) {
                        reporterToUpdate.setConfiguration(updateReporter.getConfiguration());
                    }
                    if (updateReporter.isInherited() && (oldReporter.getReference().type() != ReferenceType.ORGANIZATION || oldReporter.isSystem())) {
                        return Single.error(new ReporterConfigurationException("Only organization reporters can be inherited"));
                    }

                    reporterToUpdate.setInherited(updateReporter.isInherited());
                    reporterToUpdate.setUpdatedAt(new Date());

                    // for update validate config against schema here instead of the resource
                    // as reporter may be system reporter so on the UI config is empty.
                    validationService.validate(reporterToUpdate.getType(), reporterToUpdate.getConfiguration());

                    return checkReporterConfiguration(reporterToUpdate)
                            .flatMap(ignore -> reporterRepository.update(reporterToUpdate)
                                    .flatMap(reporter -> {
                                        Event event = new Event(Type.REPORTER, new Payload(reporter.getId(), reporter.getReference(), Action.UPDATE));
                                        return eventService.create(event).flatMap(e -> Single.just(reporter));

                                    }));
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to update a reporter", ex);
                    String message = "An error occurs while trying to update a reporter. ";
                    if (ex instanceof ReporterConfigurationException) {
                        message += ex.getMessage();
                    }
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    return Single.error(new TechnicalManagementException(message, ex));
                });
    }

    @Override
    public Completable delete(String reporterId, User principal, boolean removeSystemReporter) {
        LOGGER.debug("Delete reporter {}", reporterId);
        return reporterRepository.findById(reporterId)
                .switchIfEmpty(Maybe.error(new ReporterNotFoundException(reporterId)))
                .flatMapCompletable(reporter -> {
                    // cannot remove system reporter
                    if (reporter.isSystem() && !removeSystemReporter) {
                        return Completable.error(new ReporterDeleteException("System reporter cannot be deleted."));
                    }
                    // create event for sync process
                    Event event = new Event(Type.REPORTER, new Payload(reporterId, reporter.getReference(), Action.DELETE));
                    return Completable.fromSingle(reporterRepository.delete(reporterId)
                                    .andThen(eventService.create(event)))
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_DELETED).reporter(reporter)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(ReporterAuditBuilder.class).principal(principal).type(EventType.REPORTER_DELETED).reporter(reporter).throwable(throwable)));
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

    /**
     * This method check if the configuration attribute of a Reporter is valid
     *
     * @param reporter to check
     * @return
     */
    private Single<Reporter> checkReporterConfiguration(Reporter reporter) {
        Single<Reporter> result = Single.just(reporter);

        if (REPORTER_AM_FILE.equalsIgnoreCase(reporter.getType())) {
            // for FileReporter we have to check if the filename isn't used by another reporter
            final JsonObject configuration = (JsonObject) Json.decodeValue(reporter.getConfiguration());
            final String reporterId = reporter.getId();
            final String reportFilename = configuration.getString(REPORTER_CONFIG_FILENAME);
            if (Strings.isNullOrEmpty(reportFilename) || !filenamePattern.matcher(reportFilename).matches()) {
                return Single.error(new ReporterConfigurationException("Filename is invalid"));
            }

            // Need to ensure there are no negative or 0 values provided for the 'retainDays' attribute.
            final String retainDaysValue = configuration.getString(REPORTER_CONFIG_RETAIN_DAYS);
            if (!Strings.isNullOrEmpty(retainDaysValue) && (!retainDaysPattern.matcher(retainDaysValue).matches())) {
                return Single.error(new ReporterConfigurationException("Retain days must be greater than 0"));
            }

            result = reporterRepository.findByReference(reporter.getReference())
                    .filter(r -> r.getType().equalsIgnoreCase(REPORTER_AM_FILE))
                    .filter(r -> reporterId == null || !r.getId().equals(reporterId)) // exclude 'self' in case of update
                    .map(r -> (JsonObject) Json.decodeValue(r.getConfiguration()))
                    .filter(cfg ->
                            cfg.containsKey(REPORTER_CONFIG_FILENAME) &&
                                    cfg.getString(REPORTER_CONFIG_FILENAME).equals(reportFilename))
                    .count()
                    .flatMap(reporters -> {
                        if (reporters > 0) {
                            // more than one reporter use the same filename
                            return Single.error(new ReporterConfigurationException("Filename already defined"));
                        } else {
                            return Single.just(reporter);
                        }
                    });
        }

        return result;
    }

    private NewReporter createMongoReporter(Reference reference) {
        NewReporter newReporter = new NewReporter();
        newReporter.setId(RandomString.generate());
        newReporter.setEnabled(true);
        newReporter.setName("MongoDB Reporter");
        newReporter.setType(MONGODB);
        newReporter.setConfiguration(createReporterConfig(reference));

        return newReporter;
    }

    private NewReporter createJdbcReporter(Reference reference) {
        NewReporter newReporter = new NewReporter();
        newReporter.setId(RandomString.generate());
        newReporter.setEnabled(true);
        newReporter.setName("JDBC Reporter");
        newReporter.setType(REPORTER_AM_JDBC);
        newReporter.setConfiguration(createReporterConfig(reference));

        return newReporter;
    }

    @Override
    public String createReporterConfig(Reference reference) {
        String reporterConfig = null;
        if (useMongoReporter()) {
            Optional<String> mongoServers = getMongoServers(environment);
            String mongoHost = null;
            String mongoPort = null;
            if (mongoServers.isEmpty()) {
                mongoHost = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.host", "localhost");
                mongoPort = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.port", "27017");
            }

            final String username = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.username");
            final String password = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.password");
            String mongoDBName = getMongoDatabaseName(environment);

            String defaultMongoUri = "mongodb://";
            if (StringUtils.hasLength(username) && StringUtils.hasLength(password)) {
                defaultMongoUri += username + ":" + password + "@";
            }
            defaultMongoUri += mongoServers.orElse(mongoHost + ":" + mongoPort) + "/" + mongoDBName;
            String mongoUri = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.uri", addOptionsToURI(environment, defaultMongoUri));

            var collectionSuffix = (reference == null || reference.matches(ReferenceType.ORGANIZATION, Organization.DEFAULT))
                    ? ""
                    : ("_" + reference.id());
            reporterConfig = "{\"uri\":\"" + mongoUri
                    + ((mongoHost != null) ? "\",\"host\":\"" + mongoHost : "")
                    + "\",\"port\":" + Integer.parseInt(mongoPort)
                    + ",\"enableCredentials\":false,\"database\":\"" + mongoDBName
                    + "\",\"reportableCollection\":\"reporter_audits" + collectionSuffix
                    + "\",\"bulkActions\":1000,\"flushInterval\":5}";
        } else if (useJdbcReporter()) {
            String jdbcHost = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.host");
            String jdbcPort = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.port");
            String jdbcDatabase = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.database");
            String jdbcDriver = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.driver");
            String jdbcUser = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.username");
            String jdbcPwd = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.password");

            reporterConfig = "{\"host\":\"" + jdbcHost + "\"," +
                    "\"port\":" + Integer.parseInt(jdbcPort) + "," +
                    "\"database\":\"" + jdbcDatabase + "\"," +
                    "\"driver\":\"" + jdbcDriver + "\"," +
                    "\"username\":\"" + jdbcUser + "\"," +
                    "\"password\":" + (jdbcPwd == null ? null : "\"" + jdbcPwd + "\"") + "," +
                    "\"tableSuffix\":\"" + getReporterTableSuffix(reference) + "\"," +
                    "\"initialSize\":0," +
                    "\"maxSize\":10," +
                    "\"maxIdleTime\":30000," +
                    "\"maxLifeTime\":30000," +
                    "\"bulkActions\":1000," +
                    "\"flushInterval\":5}";
        }
        return reporterConfig;
    }

    @Override
    public Completable notifyInheritedReporters(Reference parentReference, Reference childReporterReference, Action childReporterAction) {
        return reporterRepository.findByReference(parentReference)
                .filter(Reporter::isInherited)
                .flatMapSingle(reporter -> {
                    Event event = new Event(Type.REPORTER, new Payload(reporter.getId(), reporter.getReference(), Action.UPDATE));
                    event.getPayload().put("childReporterAction", childReporterAction);
                    event.getPayload().put("childReporterReference", childReporterReference);
                    return eventService.create(event);
                })
                .ignoreElements();
    }

    private static String getReporterTableSuffix(Reference reference) {
        if (reference == null || reference.matches(ReferenceType.ORGANIZATION, Organization.DEFAULT)) {
            return "";
        }
        // dashes are forbidden in table names, replace them in domainName by underscore
        var tableSuffix = reference.id().replace("-", "_");
        if (tableSuffix.length() <= TABLE_SUFFIX_MAX_LENGTH) {
            return tableSuffix;
        }
        try {
            LOGGER.info("Table name 'reporter_audits_access_points_{}' will be too long, compute shortest unique name", tableSuffix);
            byte[] hash = MessageDigest.getInstance("sha-256").digest(tableSuffix.getBytes());
            return BaseEncoding.base16().encode(hash).substring(0, 30).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to compute digest of '" + reference.id() + "' due to unknown sha-256 algorithm", e);
        }
    }

    private String addOptionsToURI(RepositoriesEnvironment environment, String mongoUri) {
        Integer connectTimeout = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.connectTimeout", Integer.class, 5000);
        Integer socketTimeout = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.socketTimeout", Integer.class, 5000);
        Integer maxConnectionIdleTime = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.maxConnectionIdleTime", Integer.class);
        Integer heartbeatFrequency = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.heartbeatFrequency", Integer.class);
        Boolean sslEnabled = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.sslEnabled", Boolean.class);
        String authSource = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.authSource", String.class);

        mongoUri += "?connectTimeoutMS=" + connectTimeout + "&socketTimeoutMS=" + socketTimeout;
        if (authSource != null) {
            mongoUri += "&authSource=" + authSource;
        }
        if (maxConnectionIdleTime != null) {
            mongoUri += "&maxIdleTimeMS=" + maxConnectionIdleTime;
        }
        if (heartbeatFrequency != null) {
            mongoUri += "&heartbeatFrequencyMS=" + heartbeatFrequency;
        }
        if (sslEnabled != null) {
            mongoUri += "&ssl=" + sslEnabled;
        }

        return mongoUri;
    }

    private Optional<String> getMongoServers(RepositoriesEnvironment env) {
        LOGGER.debug("Looking for MongoDB server configuration...");
        boolean found = true;
        int idx = 0;
        List<String> endpoints = new ArrayList<>();

        while (found) {
            String serverHost = env.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.servers[" + (idx++) + "].host");
            int serverPort = env.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.servers[" + (idx++) + "].port", int.class, 27017);
            found = (serverHost != null);
            if (found) {
                endpoints.add(serverHost + ":" + serverPort);
            }
        }
        return endpoints.isEmpty() ? Optional.empty() : Optional.of(endpoints.stream().collect(Collectors.joining(",")));
    }

    private boolean useMongoReporter() {
        String managementBackend = this.environment.getProperty(MANAGEMENT_TYPE, MONGODB);
        return MONGODB.equalsIgnoreCase(managementBackend);
    }

    private boolean useJdbcReporter() {
        String managementBackend = this.environment.getProperty(MANAGEMENT_TYPE, MONGODB);
        return "jdbc".equalsIgnoreCase(managementBackend);
    }
}
