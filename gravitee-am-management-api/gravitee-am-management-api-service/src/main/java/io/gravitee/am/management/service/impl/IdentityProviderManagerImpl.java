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

import com.google.common.io.BaseEncoding;
import io.gravitee.am.common.event.IdentityProviderEvent;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class IdentityProviderManagerImpl extends AbstractService<IdentityProviderManager> implements IdentityProviderManager, EventListener<IdentityProviderEvent, Payload> {
    private static final Logger logger = LoggerFactory.getLogger(IdentityProviderManagerImpl.class);
    private static final String DEFAULT_IDP_PREFIX = "default-idp-";
    private static final String DEFAULT_IDP_NAME = "Default Identity Provider";
    private static final String DEFAULT_MONGO_IDP_TYPE = "mongo-am-idp";
    private static final String DEFAULT_JDBC_IDP_TYPE = "jdbc-am-idp";
    // For postgres table name length is 63 (MySQL : 64 / SQL Server : 128) but the domain is prefixed by 'idp_users_' of length 10
    // set to 50 in order to also check the length of the ID field (max 64 with prefix of 12)
    public static final int TABLE_NAME_MAX_LENGTH = 50;
    private ConcurrentMap<String, UserProvider> userProviders = new ConcurrentHashMap<>();

    @Value("${management.mongodb.uri:mongodb://localhost:27017}")
    private String mongoUri;

    @Value("${management.mongodb.host:localhost}")
    private String mongoHost;

    @Value("${management.mongodb.port:27017}")
    private String mongoPort;

    @Value("${management.mongodb.dbname:gravitee-am}")
    private String mongoDBName;

    @Value("${management.type:mongodb}")
    private String managementBackend;

    @Value("${management.jdbc.host:localhost}")
    private String jdbcHost;

    @Value("${management.jdbc.port:#{null}}")
    private String jdbcPort;

    @Value("${management.jdbc.driver:postgresql}")
    private String jdbcDriver;

    @Value("${management.jdbc.database:gravitee_am}")
    private String jdbcDatabase;

    @Value("${management.jdbc.username:postgres}")
    private String jdbcUser;

    @Value("${management.jdbc.password:#{null}}")
    private String jdbcPassword;

    @Value("${management.jdbc.identityProvider.provisioning:true}")
    private boolean idpProvisioning;

    @Autowired
    private IdentityProviderPluginManager identityProviderPluginManager;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private EventManager eventManager;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for identity provider events for the management API");
        eventManager.subscribeForEvents(this, IdentityProviderEvent.class);

        logger.info("Initializing user providers");

        identityProviderService.findAll().blockingForEach(identityProvider -> {
            logger.info("\tInitializing user provider: {} [{}]", identityProvider.getName(), identityProvider.getType());
            loadUserProvider(identityProvider);
        });
    }

    @Override
    public void onEvent(Event<IdentityProviderEvent, Payload> event) {
        switch (event.type()) {
            case DEPLOY:
            case UPDATE:
                deployUserProvider(event.content().getId());
                break;
            case UNDEPLOY:
                removeUserProvider(event.content().getId());
                break;
        }
    }

    @Override
    public Maybe<UserProvider> getUserProvider(String userProvider) {
        if (userProvider == null) {
            return Maybe.empty();
        }
        UserProvider userProvider1 = userProviders.get(userProvider);
        return (userProvider1 != null) ? Maybe.just(userProvider1) : Maybe.empty();
    }

    @Override
    public Single<IdentityProvider> create(ReferenceType referenceType, String referenceId) {
        NewIdentityProvider newIdentityProvider = new NewIdentityProvider();

        String lowerCaseId = referenceId.toLowerCase();
        newIdentityProvider.setId(DEFAULT_IDP_PREFIX + lowerCaseId);
        newIdentityProvider.setName(DEFAULT_IDP_NAME);
        if (useMongoRepositories()) {
            newIdentityProvider.setType(DEFAULT_MONGO_IDP_TYPE);
            newIdentityProvider.setConfiguration("{\"uri\":\"" + mongoUri + "\",\"host\":\"" + mongoHost + "\",\"port\":" + mongoPort + ",\"enableCredentials\":false,\"database\":\"" + mongoDBName + "\",\"usersCollection\":\"idp_users_" + lowerCaseId + "\",\"findUserByUsernameQuery\":\"{username: ?}\",\"findUserByEmailQuery\":\"{email: ?}\",\"usernameField\":\"username\",\"passwordField\":\"password\",\"passwordEncoder\":\"BCrypt\"}");
        } else if (useJdbcRepositories()) {
            newIdentityProvider.setType(DEFAULT_JDBC_IDP_TYPE);
            String tableSuffix = lowerCaseId.replaceAll("-", "_");
            if ((tableSuffix).length() > TABLE_NAME_MAX_LENGTH) {
                try {
                    logger.info("Table name 'idp_users_{}' will be too long, compute shortest unique name", tableSuffix);
                    byte[] hash = MessageDigest.getInstance("sha-256").digest(tableSuffix.getBytes());
                    tableSuffix = BaseEncoding.base16().encode(hash).substring(0, 40).toLowerCase();
                    newIdentityProvider.setId(DEFAULT_IDP_PREFIX + tableSuffix);
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("Unable to compute digest of '" + lowerCaseId + "' due to unknown sha-256 algorithm", e);
                }
            }

            String providerConfig = "{\"host\":\""+jdbcHost+"\"," +
                    "\"port\":"+jdbcPort+"," +
                    "\"protocol\":\""+jdbcDriver+"\"," +
                    "\"database\":\""+jdbcDatabase+"\"," +
                    // dash are forbidden in table name, replace them in domainName by underscore
                    "\"usersTable\":\"idp_users_"+ tableSuffix +"\"," +
                    "\"user\":\""+ jdbcUser +"\"," +
                    "\"password\":"+ (jdbcPassword == null ? null : "\"" + jdbcPassword + "\"") + "," +
                    "\"autoProvisioning\":"+ idpProvisioning +"," +
                    "\"selectUserByUsernameQuery\":\"SELECT * FROM idp_users_"+ tableSuffix +" WHERE username = %s\"," +
                    "\"selectUserByEmailQuery\":\"SELECT * FROM idp_users_"+ tableSuffix +" WHERE email = %s\"," +
                    "\"identifierAttribute\":\"id\"," +
                    "\"usernameAttribute\":\"username\"," +
                    "\"passwordAttribute\":\"password\"," +
                    "\"passwordEncoder\":\"BCrypt\"}";
            newIdentityProvider.setConfiguration(providerConfig);
        } else {
            return Single.error(new IllegalStateException("Unable to create Default IdentityProvider with " + managementBackend + " backend"));
        }
        return identityProviderService.create(referenceType, referenceId, newIdentityProvider, null);
    }

    protected boolean useMongoRepositories() {
        return "mongodb".equalsIgnoreCase(managementBackend);
    }

    protected boolean useJdbcRepositories() {
        return "jdbc".equalsIgnoreCase(managementBackend);
    }

    @Override
    public Single<IdentityProvider> create(String domain) {
        return create(ReferenceType.DOMAIN, domain);
    }

    @Override
    public boolean userProviderExists(String identityProviderId) {
        return userProviders.containsKey(identityProviderId);
    }

    private void deployUserProvider(String identityProviderId) {
        logger.info("Management API has received a deploy identity provider event for {}", identityProviderId);
        identityProviderService.findById(identityProviderId)
                .subscribe(
                        identityProvider -> loadUserProvider(identityProvider),
                        error -> logger.error("Unable to deploy user provider  {}", identityProviderId, error),
                        () -> logger.error("No identity provider found with id {}", identityProviderId));
    }

    private void removeUserProvider(String identityProviderId) {
        logger.info("Management API has received a undeploy identity provider event for {}", identityProviderId);
        UserProvider userProvider = userProviders.remove(identityProviderId);
        if (userProvider != null) {
            // stop the user provider
            try {
                userProvider.stop();
            } catch (Exception e) {
                logger.error("An error has occurred while stopping the user provider : {}", identityProviderId, e);
            }
        }
    }

    private void loadUserProvider(IdentityProvider identityProvider) {
        try {
            UserProvider userProvider = identityProviderPluginManager.create(identityProvider.getType(), identityProvider.getConfiguration());
            if (userProvider != null) {
                logger.info("Initializing user provider : {}", identityProvider.getId());
                userProvider.start();
                userProviders.put(identityProvider.getId(), userProvider);
            } else {
                userProviders.remove(identityProvider.getId());
            }
        } catch (Exception ex) {
            logger.error("An error has occurred while loading user provider: {} [{}]", identityProvider.getName(), identityProvider.getType(), ex);
            userProviders.remove(identityProvider.getId());
        }
    }
}
