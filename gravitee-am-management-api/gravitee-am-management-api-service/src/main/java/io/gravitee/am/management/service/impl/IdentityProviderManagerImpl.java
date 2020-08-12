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

import io.gravitee.am.common.event.IdentityProviderEvent;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.model.IdentityProvider;
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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class IdentityProviderManagerImpl extends AbstractService<IdentityProviderManager>  implements IdentityProviderManager, EventListener<IdentityProviderEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(IdentityProviderManagerImpl.class);
    private static final String DEFAULT_IDP_PREFIX = "default-idp-";
    private static final String DEFAULT_IDP_NAME = "Default Identity Provider";
    private static final String DEFAULT_IDP_TYPE = "mongo-am-idp";
    private ConcurrentMap<String, UserProvider> userProviders = new ConcurrentHashMap<>();

    @Value("${management.mongodb.uri:mongodb://localhost:27017}")
    private String mongoUri;

    @Value("${management.mongodb.host:localhost}")
    private String mongoHost;

    @Value("${management.mongodb.port:27017}")
    private String mongoPort;

    @Value("${management.mongodb.dbname:gravitee-am}")
    private String mongoDBName;

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
        List<IdentityProvider> identities = identityProviderService.findAll().blockingGet();
        identities.forEach(identityProvider -> {
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
    public Single<IdentityProvider> create(String domain) {
        NewIdentityProvider newIdentityProvider = new NewIdentityProvider();
        newIdentityProvider.setId(DEFAULT_IDP_PREFIX + domain);
        newIdentityProvider.setName(DEFAULT_IDP_NAME);
        newIdentityProvider.setType(DEFAULT_IDP_TYPE);
        newIdentityProvider.setConfiguration("{\"uri\":\"" + mongoUri + "\",\"host\":\""+ mongoHost + "\",\"port\":" + mongoPort + ",\"enableCredentials\":false,\"database\":\"" + mongoDBName + "\",\"usersCollection\":\"idp_users_" + domain + "\",\"findUserByUsernameQuery\":\"{username: ?}\",\"usernameField\":\"username\",\"passwordField\":\"password\",\"passwordEncoder\":\"BCrypt\"}");

        return identityProviderService.create(domain, newIdentityProvider);
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
        userProviders.remove(identityProviderId);
    }

    private void loadUserProvider(IdentityProvider identityProvider) {
        try {
            UserProvider userProvider = identityProviderPluginManager.create(identityProvider.getType(), identityProvider.getConfiguration());
            if (userProvider != null) {
                logger.info("Initializing user provider : {}", identityProvider.getId());
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
