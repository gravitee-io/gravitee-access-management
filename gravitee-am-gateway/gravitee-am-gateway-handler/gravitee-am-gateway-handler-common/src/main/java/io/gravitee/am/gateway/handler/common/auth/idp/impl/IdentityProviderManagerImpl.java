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
package io.gravitee.am.gateway.handler.common.auth.idp.impl;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.IdentityProviderEvent;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.context.provider.UserProperties;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
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

    @Autowired
    private CertificateManager certificateManager;

    private ConcurrentMap<String, AuthenticationProvider> providers = new ConcurrentHashMap<>();
    private ConcurrentMap<String, IdentityProvider> identities = new ConcurrentHashMap<>();
    private ConcurrentMap<String, UserProvider> userProviders = new ConcurrentHashMap<>();

    @Override
    public Maybe<AuthenticationProvider> get(String id) {
        AuthenticationProvider authenticationProvider = providers.get(id);
        return (authenticationProvider != null) ? Maybe.just(authenticationProvider) : Maybe.empty();
    }

    @Override
    public IdentityProvider getIdentityProvider(String id) {
        return identities.get(id);
    }

    @Override
    public Maybe<UserProvider> getUserProvider(String id) {
        UserProvider userProvider = userProviders.get(id);
        return (userProvider != null) ? Maybe.just(userProvider) : Maybe.empty();
    }

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing identity providers for domain {}", domain.getName());

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

        logger.info("Register event listener for identity provider events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, IdentityProviderEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        logger.info("Dispose event listener for identity provider events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, IdentityProviderEvent.class, domain.getId());
        clearProviders();
    }

    @Override
    public void onEvent(Event<IdentityProviderEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN && domain.getId().equals(event.content().getReferenceId())) {
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
        clearProvider(identityProviderId);
    }

    private void updateAuthenticationProvider(IdentityProvider identityProvider) {
        logger.info("\tInitializing identity provider: {} [{}]", identityProvider.getName(), identityProvider.getType());
        try {
            // stop existing provider, if any
            clearProvider(identityProvider.getId());
            // create and start the new provider
            AuthenticationProvider authenticationProvider =
                    identityProviderPluginManager.create(identityProvider.getType(), identityProvider.getConfiguration(),
                            identityProvider.getMappers(), identityProvider.getRoleMapper(), certificateManager);
            if (authenticationProvider != null) {
                // start the authentication provider
                authenticationProvider.start();
                // init the user provider
                UserProvider userProvider =
                        identityProviderPluginManager.create(identityProvider.getType(), identityProvider.getConfiguration());
                providers.put(identityProvider.getId(), authenticationProvider);
                identities.put(identityProvider.getId(), identityProvider);
                if (userProvider != null) {
                    // start the user provider
                    userProvider.start();
                    userProviders.put(identityProvider.getId(), userProvider);
                } else {
                    userProviders.remove(identityProvider.getId());
                }
            }
        } catch (Exception ex) {
            // failed to load the plugin
            logger.error("An error occurs while initializing the identity provider : {}", identityProvider.getName(), ex);
            clearProvider(identityProvider.getId());
        }
    }

    private void clearProviders() {
        providers.keySet().forEach(this::clearProvider);
    }

    private void clearProvider(String identityProviderId) {
        AuthenticationProvider authenticationProvider = providers.remove(identityProviderId);
        UserProvider userProvider = userProviders.remove(identityProviderId);
        identities.remove(identityProviderId);
        if (authenticationProvider != null) {
            // stop the authentication provider
            try {
                authenticationProvider.stop();
            } catch (Exception e) {
                logger.error("An error has occurred while stopping the authentication provider : {}", identityProviderId, e);
            }
        }
        if (userProvider != null) {
            // stop the user provider
            try {
                userProvider.stop();
            } catch (Exception e) {
                logger.error("An error has occurred while stopping the user provider : {}", identityProviderId, e);
            }
        }
    }
}
