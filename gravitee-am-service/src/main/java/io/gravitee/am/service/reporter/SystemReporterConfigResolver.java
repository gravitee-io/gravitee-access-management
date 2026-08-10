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
package io.gravitee.am.service.reporter;

import com.google.common.io.BaseEncoding;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.service.model.NewReporter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.gravitee.am.service.utils.BackendConfigurationUtils.getMongoDatabaseName;

/**
 * @author GraviteeSource Team
 */
@Component
@RequiredArgsConstructor
public class SystemReporterConfigResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemReporterConfigResolver.class);
    private static final int TABLE_SUFFIX_MAX_LENGTH = 30;
    private static final String REPORTER_AM_JDBC = "reporter-am-jdbc";
    private static final String MONGODB = "mongodb";
    private static final String JDBC = "jdbc";
    public static final String MANAGEMENT_TYPE = Scope.MANAGEMENT.getRepositoryPropertyKey() + ".type";

    private final RepositoriesEnvironment environment;

    public Reporter resolve(Reporter reporter) {
        if (!reporter.isSystem()) {
            return reporter;
        }
        final String configuration = createReporterConfig(reporter.getReference());
        if (configuration == null) {
            LOGGER.warn("Unsupported backend {}, system reporter {} keeps its persisted configuration", managementBackend(), reporter.getId());
            return reporter;
        }
        Reporter resolved = new Reporter(reporter);
        resolved.setConfiguration(configuration);
        return resolved;
    }

    public NewReporter createInternal(Reference reference) {
        NewReporter newReporter = null;
        if (useMongoReporter()) {
            newReporter = createMongoReporter(reference);
        } else if (useJdbcReporter()) {
            newReporter = createJdbcReporter(reference);
        }
        return newReporter;
    }

    public String managementBackend() {
        return environment.getProperty(MANAGEMENT_TYPE, MONGODB);
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

            reporterConfig = """
                        {
                          "uri": "%s",
                          "host": "%s",
                          "port": %d,
                          "enableCredentials": false,
                          "database": "%s",
                          "reportableCollection": "reporter_audits%s",
                          "bulkActions": 1000,
                          "flushInterval": 5
                        }
                        """.formatted(
                    mongoUri,
                    (mongoHost != null) ? mongoHost : "",
                    (mongoPort != null) ? Integer.parseInt(mongoPort) : null,
                    mongoDBName,
                    collectionSuffix
            );
        } else if (useJdbcReporter()) {
            String jdbcHost = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.host");
            String jdbcPort = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.port");
            String jdbcDatabase = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.database");
            String jdbcDriver = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.driver");
            String jdbcUser = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.username");
            String jdbcPwd = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.password");
            String jdbcSchema = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.schema");

            String options = jdbcSchema == null || jdbcSchema.isEmpty() ? "[]" : """
                    [{"option":"currentSchema","value":"%s"}]
                    """.formatted(jdbcSchema);

            reporterConfig = """
                        {
                          "host": "%s",
                          "port": %d,
                          "database": "%s",
                          "driver": "%s",
                          "username": "%s",
                          "password": %s,
                          "tableSuffix": "%s",
                          "options": %s,
                          "initialSize": 0,
                          "maxSize": 10,
                          "maxIdleTime": 30000,
                          "maxLifeTime": 30000,
                          "bulkActions": 1000,
                          "flushInterval": 5
                        }
                        """.formatted(
                    jdbcHost,
                    Integer.parseInt(jdbcPort),
                    jdbcDatabase,
                    jdbcDriver,
                    jdbcUser,
                    jdbcPwd == null ? null : "\"" + jdbcPwd + "\"",
                    getReporterTableSuffix(reference),
                    options
            );

        }
        return reporterConfig;
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
            String serverHost = env.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.servers[" + (idx) + "].host");
            int serverPort = env.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".mongodb.servers[" + (idx) + "].port", int.class, 27017);
            idx++;
            found = (serverHost != null);
            if (found) {
                endpoints.add(serverHost + ":" + serverPort);
            }
        }
        return endpoints.isEmpty() ? Optional.empty() : Optional.of(endpoints.stream().collect(Collectors.joining(",")));
    }

    private boolean useMongoReporter() {
        return MONGODB.equalsIgnoreCase(managementBackend());
    }

    private boolean useJdbcReporter() {
        return JDBC.equalsIgnoreCase(managementBackend());
    }
}
