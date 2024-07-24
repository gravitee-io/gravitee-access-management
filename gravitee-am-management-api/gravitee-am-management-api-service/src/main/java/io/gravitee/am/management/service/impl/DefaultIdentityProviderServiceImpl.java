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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.BaseEncoding;
import io.gravitee.am.management.service.DefaultIdentityProviderService;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoderOptions;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


import static io.gravitee.am.service.utils.BackendConfigurationUtils.getMongoDatabaseName;

@Component
@Slf4j
public class DefaultIdentityProviderServiceImpl implements DefaultIdentityProviderService {

    public static final String DEFAULT_IDP_PREFIX = "default-idp-";
    private static final String DEFAULT_IDP_NAME = "Default Identity Provider";
    private static final String DEFAULT_MONGO_IDP_TYPE = "mongo-am-idp";
    private static final String DEFAULT_JDBC_IDP_TYPE = "jdbc-am-idp";
    private static final int TABLE_NAME_MAX_LENGTH = 50;
    public static final String PASSWORD = "password";

    private static final Set<String> SUPPORTED_PASSWORD_ENCODER = Set.of("BCrypt", "SHA-256", "SHA-384", "SHA-512", "SHA-256+MD5");

    private final IdentityProviderService identityProviderService;

    private final Environment environment;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DefaultIdentityProviderServiceImpl(IdentityProviderService identityProviderService, Environment environment) {
        this.identityProviderService = identityProviderService;
        this.environment = environment;
    }

    @Override
    public Single<IdentityProvider> create(String domainId) {
        NewIdentityProvider newIdentityProvider = new NewIdentityProvider();
        newIdentityProvider.setId(DEFAULT_IDP_PREFIX + domainId.toLowerCase());
        newIdentityProvider.setName(DEFAULT_IDP_NAME);
        if (useMongoRepositories()) {
            newIdentityProvider.setType(DEFAULT_MONGO_IDP_TYPE);
            newIdentityProvider.setConfiguration(createProviderConfig(domainId, null));
        } else if (useJdbcRepositories()) {
            newIdentityProvider.setType(DEFAULT_JDBC_IDP_TYPE);
            newIdentityProvider.setConfiguration(createProviderConfig(domainId, newIdentityProvider));
        } else {
            return Single.error(new IllegalStateException("Unable to create Default IdentityProvider with " + managementBackend() + " backend"));
        }
        return identityProviderService.create(ReferenceType.DOMAIN, domainId, newIdentityProvider, null, true);
    }


    @Override
    public Map<String, Object> createProviderConfiguration(String referenceId, NewIdentityProvider identityProvider) {
        final Map<String, Object> configMap = new LinkedHashMap<>();

        final String encoder = environment.getProperty("domains.identities.default.passwordEncoder.algorithm", "BCrypt");

        if (!SUPPORTED_PASSWORD_ENCODER.contains(encoder)) {
            throw new IllegalArgumentException("Invalid password encoder value '" + encoder + "'");
        }

        String lowerCaseId = referenceId.toLowerCase();
        if (useMongoRepositories()) {
            Optional<String> mongoServers = getMongoServers();
            String mongoHost = null;
            String mongoPort = null;
            if (mongoServers.isEmpty()) {
                mongoHost = environment.getProperty("management.mongodb.host", "localhost");
                mongoPort = environment.getProperty("management.mongodb.port", "27017");
            }

            final String username = environment.getProperty("management.mongodb.username");
            final String password = environment.getProperty("management.mongodb.password");
            final String mongoDBName = getMongoDatabaseName(environment);

            String defaultMongoUri = "mongodb://";
            if (StringUtils.hasLength(username) && StringUtils.hasLength(password)) {
                defaultMongoUri += username + ":" + password + "@";
            }
            defaultMongoUri += addOptionsToURI(mongoServers.orElse(mongoHost + ":" + mongoPort));

            String mongoUri = environment.getProperty("management.mongodb.uri", defaultMongoUri);

            configMap.put("uri", mongoUri);
            configMap.put("host", (mongoHost != null) ? mongoHost : "");
            configMap.put("port", mongoPort);
            configMap.put("enableCredentials", false);
            configMap.put("database", mongoDBName);
            configMap.put("usersCollection", "idp_users_" + lowerCaseId);
            configMap.put("findUserByUsernameQuery", "{username: ?}");
            configMap.put("findUserByEmailQuery", "{email: ?}");
            configMap.put("usernameField", "username");
            configMap.put("passwordField", PASSWORD);
            configMap.put("passwordEncoder", encoder);
            updatePasswordEncoderOptions(configMap, encoder);

        } else if (useJdbcRepositories()) {
            String tableSuffix = lowerCaseId.replace("-", "_");
            if ((tableSuffix).length() > TABLE_NAME_MAX_LENGTH) {
                try {
                    log.info("Table name 'idp_users_{}' will be too long, compute shortest unique name", tableSuffix);
                    byte[] hash = MessageDigest.getInstance("sha-256").digest(tableSuffix.getBytes());
                    tableSuffix = BaseEncoding.base16().encode(hash).substring(0, 40).toLowerCase();
                    if (identityProvider != null) {
                        identityProvider.setId(DEFAULT_IDP_PREFIX + tableSuffix);
                    }
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("Unable to compute digest of '" + lowerCaseId + "' due to unknown sha-256 algorithm", e);
                }
            }

            configMap.put("host", jdbcHost());
            configMap.put("port", jdbcPort());
            configMap.put("protocol", jdbcDriver());
            configMap.put("database", jdbcDatabase());
            // dash are forbidden in table name, replace them in domainName by underscore
            configMap.put("usersTable", "idp_users_" + tableSuffix);
            configMap.put("user", jdbcUser());
            configMap.put(PASSWORD, (jdbcPassword() == null ? null : jdbcPassword()));
            configMap.put("autoProvisioning", idpProvisioning());
            configMap.put("selectUserByUsernameQuery", "SELECT * FROM idp_users_" + tableSuffix + " WHERE username = %s");
            configMap.put("selectUserByEmailQuery", "SELECT * FROM idp_users_" + tableSuffix + " WHERE email = %s");
            configMap.put("identifierAttribute", "id");
            configMap.put("usernameAttribute", "username");
            configMap.put("passwordAttribute", PASSWORD);
            configMap.put("passwordEncoder", encoder);
            updatePasswordEncoderOptions(configMap, encoder);
        }
        return configMap;
    }

    public String addOptionsToURI(String mongoUri) {
        Integer connectTimeout = environment.getProperty("management.mongodb.connectTimeout", Integer.class, 1000);
        Integer socketTimeout = environment.getProperty("management.mongodb.socketTimeout", Integer.class, 1000);
        Integer maxConnectionIdleTime = environment.getProperty("management.mongodb.maxConnectionIdleTime", Integer.class);
        Integer heartbeatFrequency = environment.getProperty("management.mongodb.heartbeatFrequency", Integer.class);
        Boolean sslEnabled = environment.getProperty("management.mongodb.sslEnabled", Boolean.class);
        String authSource = environment.getProperty("management.mongodb.authSource", String.class);

        mongoUri += mongoUri.endsWith("/") ? "" : "/";
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


    private String createProviderConfig(String referenceId, NewIdentityProvider identityProvider) {
        final Map<String, Object> configMap = createProviderConfiguration(referenceId, identityProvider);
        try {
            return objectMapper.writeValueAsString(configMap);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize the default idp configuration for domain '" + referenceId + "'", e);
        }
    }

    private void updatePasswordEncoderOptions(Map<String, Object> configMap, String encoder) {
        if ("bcrypt".equalsIgnoreCase(encoder)) {
            String rounds = environment.getProperty("domains.identities.default.passwordEncoder.properties.rounds", "10");
            configMap.put("passwordEncoderOptions", new PasswordEncoderOptions(Integer.parseInt(rounds)));
        } else if (encoder.toLowerCase().startsWith("sha")) {
            String rounds = environment.getProperty("domains.identities.default.passwordEncoder.properties.rounds", "1");
            configMap.put("passwordEncoderOptions", new PasswordEncoderOptions(Integer.parseInt(rounds)));
        }
    }

    protected boolean useMongoRepositories() {
        return "mongodb".equalsIgnoreCase(managementBackend());
    }

    protected boolean useJdbcRepositories() {
        return "jdbc".equalsIgnoreCase(managementBackend());
    }

    private Optional<String> getMongoServers() {
        log.debug("Looking for MongoDB server configuration...");

        boolean found = true;
        int idx = 0;
        List<String> endpoints = new ArrayList<>();

        while (found) {
            String serverHost = environment.getProperty("management.mongodb.servers[" + (idx++) + "].host");
            int serverPort = environment.getProperty("management.mongodb.servers[" + (idx++) + "].port", int.class, 27017);
            found = (serverHost != null);
            if (found) {
                endpoints.add(serverHost + ":" + serverPort);
            }
        }

        return endpoints.isEmpty() ? Optional.empty() : Optional.of(String.join(",", endpoints));
    }

    private String managementBackend() {
        return environment.getProperty("management.type", "mongodb");
    }
    private String jdbcHost() {
        return environment.getProperty("management.jdbc.host", "localhost");
    }

    private String jdbcPort() {
        return environment.getProperty("management.jdbc.port");
    }

    private String jdbcDriver() {
        return environment.getProperty("management.jdbc.driver", "postgresql");
    }

    private String jdbcDatabase() {
        return environment.getProperty("management.jdbc.database", "gravitee_am");
    }

    private String jdbcUser() {
        return environment.getProperty("management.jdbc.username", "postgres");
    }

    private String jdbcPassword() {
        return environment.getProperty("management.jdbc.password");
    }

    private boolean idpProvisioning() {
        return environment.getProperty("management.jdbc.identityProvider.provisioning", Boolean.class, true);
    }
}
