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
package io.gravitee.am.gateway.handler.auth.idp.impl;

import io.gravitee.am.gateway.core.event.IdentityProviderEvent;
import io.gravitee.am.gateway.handler.auth.idp.IdentityProviderManager;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderManagerImpl extends AbstractService implements IdentityProviderManager, InitializingBean, EventListener<IdentityProviderEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(IdentityProviderManagerImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private IdentityProviderPluginManager identityProviderPluginManager;

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    @Autowired
    private EventManager eventManager;

    private ConcurrentMap<String, AuthenticationProvider> providers = new ConcurrentHashMap<>();
    private ConcurrentMap<String, IdentityProvider> identities = new ConcurrentHashMap<>();
    private ConcurrentMap<String, UserProvider> userProviders = new ConcurrentHashMap<>();

    @Override
    public Maybe<AuthenticationProvider> get(String id) {
        AuthenticationProvider authenticationProvider = providers.get(id);
        return (authenticationProvider != null) ? Maybe.just(authenticationProvider) : Maybe.empty();
    }

    @Override
    public Maybe<IdentityProvider> getIdentityProvider(String id) {
        IdentityProvider identityProvider = identities.get(id);
        return (identityProvider != null) ? Maybe.just(identityProvider) : Maybe.empty();
    }

    @Override
    public Maybe<UserProvider> getUserProvider(String id) {
        UserProvider userProvider = userProviders.get(id);
        return (userProvider != null) ? Maybe.just(userProvider) : Maybe.empty();
    }

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing identity providers for domain {}", domain.getName());

        // identity providers are required for extension grants bean creation
        // make blocking call to create them first
        try {
            Set<IdentityProvider> identityProviders = identityProviderRepository.findByDomain(domain.getId()).blockingGet();
            identityProviders.forEach(identityProvider -> updateAuthenticationProvider(identityProvider));
            logger.info("Identity providers loaded for domain {}", domain.getName());
        } catch (Exception e) {
            logger.error("Unable to initialize identity providers for domain {}", domain.getName(), e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for identity provider events");
        eventManager.subscribeForEvents(this, IdentityProviderEvent.class);
    }

    @Override
    public void onEvent(Event<IdentityProviderEvent, Payload> event) {
        if (domain.getId().equals(event.content().getDomain())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateIdentityProvider(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeIdentityProvider(event.content().getId());
                    break;
            }
        }
    }

    private void updateIdentityProvider(String identityProviderId, IdentityProviderEvent identityProviderEvent) {
        final String eventType = identityProviderEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} identity provider event for {}", domain.getName(), eventType, identityProviderId);
        identityProviderRepository.findById(identityProviderId)
                .subscribe(
                        identityProvider -> {
                            updateAuthenticationProvider(identityProvider);
                            logger.info("Identity provider {} {}d for domain {}", identityProviderId, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} identity provider for domain {}", eventType, domain.getName(), error),
                        () -> logger.error("No identity provider found with id {}", identityProviderId));
    }

    private void removeIdentityProvider(String identityProviderId) {
        logger.info("Domain {} has received identity provider event, delete identity provider {}", domain.getName(), identityProviderId);
        providers.remove(identityProviderId);
        identities.remove(identityProviderId);
        userProviders.remove(identityProviderId);
    }

    private void updateAuthenticationProvider(IdentityProvider identityProvider) {
        logger.info("\tInitializing identity provider: {} [{}]", identityProvider.getName(), identityProvider.getType());
        AuthenticationProvider authenticationProvider =
                identityProviderPluginManager.create(identityProvider.getType(), identityProvider.getConfiguration(),
                        identityProvider.getMappers(), identityProvider.getRoleMapper());
        UserProvider userProvider =
                identityProviderPluginManager.create(identityProvider.getType(), identityProvider.getConfiguration());
        providers.put(identityProvider.getId(), authenticationProvider);
        identities.put(identityProvider.getId(), identityProvider);
        if (userProvider != null) {
            userProviders.put(identityProvider.getId(), userProvider);
        }
    }
}
