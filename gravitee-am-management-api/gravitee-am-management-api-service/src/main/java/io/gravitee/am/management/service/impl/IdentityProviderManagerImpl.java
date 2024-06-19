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
import io.gravitee.am.common.event.IdentityProviderEvent;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.InMemoryIdentityProviderListener;
import io.gravitee.am.management.service.impl.utils.InlineOrganizationProviderConfiguration;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoderOptions;
import io.gravitee.am.service.exception.PluginNotDeployedException;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static io.gravitee.am.management.service.impl.utils.InlineOrganizationProviderConfiguration.MEMORY_TYPE;
import static io.gravitee.am.service.utils.BackendConfigurationUtils.getMongoDatabaseName;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class IdentityProviderManagerImpl extends AbstractService<IdentityProviderManager> implements IdentityProviderManager, EventListener<IdentityProviderEvent, Payload> {
    private static final Set<String> SUPPORTED_PASSWORD_ENCODER = Set.of("BCrypt", "SHA-256", "SHA-384", "SHA-512", "SHA-256+MD5");
    public static final String IDP_GRAVITEE = "gravitee";

    private static final Logger logger = LoggerFactory.getLogger(IdentityProviderManagerImpl.class);
    private static final String DEFAULT_IDP_PREFIX = "default-idp-";
    private static final String DEFAULT_IDP_NAME = "Default Identity Provider";
    private static final String DEFAULT_MONGO_IDP_TYPE = "mongo-am-idp";
    private static final String DEFAULT_JDBC_IDP_TYPE = "jdbc-am-idp";
    // For postgres table name length is 63 (MySQL : 64 / SQL Server : 128) but the domain is prefixed by 'idp_users_' of length 10
    // set to 50 in order to also check the length of the ID field (max 64 with prefix of 12)
    private static final int TABLE_NAME_MAX_LENGTH = 50;
    public static final String PASSWORD = "password";

    private final ConcurrentMap<String, UserProvider> userProviders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, IdentityProvider> identityProviders = new ConcurrentHashMap<>();

    @Autowired
    private IdentityProviderPluginManager identityProviderPluginManager;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private Environment environment;

    @Autowired
    private RoleService roleService;

    private InMemoryIdentityProviderListener listener;

    public void setListener(InMemoryIdentityProviderListener listener) {
        this.listener = listener;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for identity provider events for the management API");
        eventManager.subscribeForEvents(this, IdentityProviderEvent.class);

        logger.info("Initializing user providers");

        identityProviderService.findAll()
                .flatMapMaybe(identityProvider -> {
                    logger.info("\tInitializing user provider: {} [{}]", identityProvider.getName(), identityProvider.getType());
                    return loadUserProvider(identityProvider);
                }).ignoreElements()
                .andThen(Completable.defer(() -> loadIdentityProviders()))
                .blockingAwait();

    }

    @Override
    public void onEvent(Event<IdentityProviderEvent, Payload> event) {
        if (Objects.requireNonNull(event.type()) == IdentityProviderEvent.UNDEPLOY) {
            removeUserProvider(event.content().getId());
        } else {
            logger.debug("{} event received for IdentityProvider {}, ignore it as it will be loaded on demand", event.type(), event.content().getId());
        }
    }

    @Override
    public Completable loadIdentityProviders() {
        if (this.listener != null) {
            final IdentityProvider graviteeIdp = buildOrganizationUserIdentityProvider();

            final List<IdentityProvider> providers = loadProvidersFromConfig();
            // add the Gravitee provider to allow addition of OrganizationUser through the console
            providers.add(graviteeIdp);
            providers.forEach(listener::registerAuthenticationProvider);

            // load gravitee idp into this component to allow user creation and update
            return loadUserProvider(graviteeIdp).ignoreElement();
        }
        return Completable.complete();
    }

    private List<IdentityProvider> loadProvidersFromConfig() {
        List<IdentityProvider> providers = new ArrayList<>();
        boolean found = true;
        int idx = 0;

        while (found) {
            String type = environment.getProperty("security.providers[" + idx + "].type");
            found = (type != null);
            if (found) {
                if (type.equals(MEMORY_TYPE)) {
                    InlineOrganizationProviderConfiguration providerConfig = new InlineOrganizationProviderConfiguration(roleService, environment, idx);
                    if (providerConfig.isEnabled()) {
                        providers.add(providerConfig.buildIdentityProvider());
                    }
                } else {
                    logger.warn("Unsupported provider with type '{}'", type);
                }
            }
            idx++;
        }

        return providers;
    }

    private IdentityProvider buildOrganizationUserIdentityProvider() {
        IdentityProvider provider = new IdentityProvider();
        provider.setId(IDP_GRAVITEE);
        provider.setExternal(false);
        provider.setType("gravitee-am-idp");
        provider.setName(IDP_GRAVITEE);
        provider.setReferenceId(Organization.DEFAULT);
        provider.setReferenceType(ReferenceType.ORGANIZATION);
        provider.setConfiguration("{}");
        return provider;
    }

    @Override
    public Maybe<UserProvider> getUserProvider(String userProvider) {
        if (userProvider == null) {
            return Maybe.empty();
        }

        if (IDP_GRAVITEE.equals(userProvider) && userProviders.containsKey(userProvider)) {
            // The gravitee idp isn't persisted so before continuing,
            // we try to get it from the map of providers
            // if missing we switch to the default behaviour just in case
            return Maybe.just(userProviders.get(userProvider));
        }

        // Since https://github.com/gravitee-io/issues/issues/6590 we have to read the record in Identity Provider repository
        return identityProviderService.findById(userProvider)
                .flatMap(persistedUserProvider -> {
                    UserProvider localUserProvider = userProviders.get(userProvider);
                    if (localUserProvider != null &&
                            identityProviders.containsKey(userProvider) &&
                            identityProviders.get(userProvider).getUpdatedAt().getTime() >= persistedUserProvider.getUpdatedAt().getTime()) {
                        return Maybe.just(localUserProvider);
                    } else {
                        this.removeUserProvider(userProvider);
                        return this.loadUserProvider(persistedUserProvider);
                    }
                });
    }

    @Override
    public Optional<IdentityProvider> getIdentityProvider(String providerId) {
        return ofNullable(identityProviders.get(providerId));
    }

    @Override
    public Single<IdentityProvider> create(ReferenceType referenceType, String referenceId) {
        NewIdentityProvider newIdentityProvider = new NewIdentityProvider();

        String lowerCaseId = referenceId.toLowerCase();
        newIdentityProvider.setId(DEFAULT_IDP_PREFIX + lowerCaseId);
        newIdentityProvider.setName(DEFAULT_IDP_NAME);
        if (useMongoRepositories()) {
            newIdentityProvider.setType(DEFAULT_MONGO_IDP_TYPE);
            newIdentityProvider.setConfiguration(createProviderConfig(referenceId, null));
        } else if (useJdbcRepositories()) {
            newIdentityProvider.setType(DEFAULT_JDBC_IDP_TYPE);
            newIdentityProvider.setConfiguration(createProviderConfig(referenceId, newIdentityProvider));
        } else {
            return Single.error(new IllegalStateException("Unable to create Default IdentityProvider with " + managementBackend() + " backend"));
        }
        return identityProviderService.create(referenceType, referenceId, newIdentityProvider, null, true);
    }

    private String createProviderConfig(String referenceId, NewIdentityProvider identityProvider) {
        final Map<String, Object> configMap = createProviderConfiguration(referenceId, identityProvider);
        try {
            return new ObjectMapper().writeValueAsString(configMap);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize the default idp configuration for domain '" + referenceId + "'", e);
        }
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
            configMap.put("port",  mongoPort);
            configMap.put("enableCredentials",  false);
            configMap.put("database",  mongoDBName);
            configMap.put("usersCollection",  "idp_users_" + lowerCaseId);
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
                    logger.info("Table name 'idp_users_{}' will be too long, compute shortest unique name", tableSuffix);
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
            configMap.put(PASSWORD, (jdbcPassword() == null ? null :  jdbcPassword()));
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

    private void updatePasswordEncoderOptions(Map<String, Object> configMap, String encoder) {
        if ("bcrypt".equalsIgnoreCase(encoder)) {
            String rounds = environment.getProperty("domains.identities.default.passwordEncoder.properties.rounds", "10");
            configMap.put("passwordEncoderOptions", new PasswordEncoderOptions(Integer.parseInt(rounds)));
        } else if (encoder.toLowerCase().startsWith("sha")){
            String rounds = environment.getProperty("domains.identities.default.passwordEncoder.properties.rounds", "1");
            configMap.put("passwordEncoderOptions", new PasswordEncoderOptions(Integer.parseInt(rounds)));
        }
    }

    private Optional<String> getMongoServers() {
        logger.debug("Looking for MongoDB server configuration...");

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

        return endpoints.isEmpty() ? Optional.empty() : Optional.of(endpoints.stream().collect(Collectors.joining(",")));
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

    protected boolean useMongoRepositories() {
        return "mongodb".equalsIgnoreCase(managementBackend());
    }

    protected boolean useJdbcRepositories() {
        return "jdbc".equalsIgnoreCase(managementBackend());
    }

    @Override
    public Single<IdentityProvider> create(String domain) {
        return create(ReferenceType.DOMAIN, domain);
    }

    private void removeUserProvider(String identityProviderId) {
        logger.info("Management API has received a undeploy identity provider event for {}", identityProviderId);
        UserProvider userProvider = userProviders.remove(identityProviderId);
        identityProviders.remove(identityProviderId);
        if (userProvider != null) {
            // stop the user provider
            try {
                userProvider.stop();
            } catch (Exception e) {
                logger.error("An error has occurred while stopping the user provider : {}", identityProviderId, e);
            }
        }
    }

    private Maybe<UserProvider> loadUserProvider(IdentityProvider identityProvider) {
        return identityProviderPluginManager.create(identityProvider.getType(), identityProvider.getConfiguration(), identityProvider)
                .flatMapMaybe(userProviderOpt -> {
                    if (userProviderOpt.isPresent()) {
                        userProviders.put(identityProvider.getId(), userProviderOpt.get());
                        identityProviders.put(identityProvider.getId(), identityProvider);
                        return Maybe.just(userProviderOpt.get());
                    } else {
                        userProviders.remove(identityProvider.getId());
                        identityProviders.remove(identityProvider.getId());
                        return Maybe.empty();
                    }
                }).onErrorResumeNext(ex -> {
                    logger.error("An error has occurred while loading user provider: {} [{}]", identityProvider.getName(), identityProvider.getType(), ex);
                    userProviders.remove(identityProvider.getId());
                    identityProviders.remove(identityProvider.getId());
                    return Maybe.empty();
                });
    }

    public Completable checkPluginDeployment(String type) {
        if (!this.identityProviderPluginManager.isPluginDeployed(type)) {
            logger.debug("Plugin {} not deployed", type);
            return Completable.error(PluginNotDeployedException.forType(type));
        }
        return Completable.complete();
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
