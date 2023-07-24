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
package io.gravitee.am.management.handlers.management.api.authentication.manager.idp.impl;

import io.gravitee.am.common.event.IdentityProviderEvent;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.management.handlers.management.api.authentication.manager.idp.IdentityProviderManager;
import io.gravitee.am.management.service.InMemoryIdentityProviderListener;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.idp.core.AuthenticationProviderConfiguration;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("managementIdentityProviderManager")
public class IdentityProviderManagerImpl implements IdentityProviderManager, InitializingBean, EventListener<IdentityProviderEvent, Payload>, InMemoryIdentityProviderListener {

    private final Logger logger = LoggerFactory.getLogger(IdentityProviderManagerImpl.class);

    @Autowired
    private IdentityProviderPluginManager identityProviderPluginManager;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private io.gravitee.am.management.service.IdentityProviderManager commonIdentityProviderManager;

    private final ConcurrentMap<String, AuthenticationProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, IdentityProvider> identities = new ConcurrentHashMap<>();

    /*
     * Providers that are not persisted into the backend. ("gravitee" provider and the ones defined into the gravitee.yaml file)
     */
    private final Set<String> transientProviders = new HashSet<>();

    @Override
    public AuthenticationProvider get(String id) {
        return providers.get(id);
    }

    public IdentityProvider getIdentityProvider(String id) {
        return identities.get(id);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Register event listener for identity provider events for all organizations");
        eventManager.subscribeForEvents(this, IdentityProviderEvent.class);

        logger.info("Initializing identity providers for all organizations");
        try {
            identityProviderService.findAll(ReferenceType.ORGANIZATION).blockingForEach(this::updateAuthenticationProvider);
            logger.info("Identity providers loaded for all organizations");
        } catch (Exception e) {
            logger.error("Unable to initialize identity providers", e);
        }

        this.commonIdentityProviderManager.setListener(this);
    }

    @Override
    public void registerAuthenticationProvider(IdentityProvider provider) {
        updateAuthenticationProvider(provider);
        transientProviders.add(provider.getId());
    }

    @Override
    public Set<String> getTransientProviders() {
        return this.transientProviders;
    }

    @Override
    public void onEvent(Event<IdentityProviderEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.ORGANIZATION && event.content().getReferenceId() != null) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateIdentityProvider(event.content().getId(), event.content().getReferenceId(), event.type());
                    break;
                case UNDEPLOY:
                    removeIdentityProvider(event.content().getId());
                    break;
            }
        }
    }

    private void updateIdentityProvider(String identityProviderId, String organizationId, IdentityProviderEvent identityProviderEvent) {
        final String eventType = identityProviderEvent.toString().toLowerCase();
        logger.info("Organization {} has received {} identity provider event for {}", organizationId, eventType, identityProviderId);
        identityProviderService.findById(identityProviderId)
                .subscribe(
                        identityProvider -> {
                            updateAuthenticationProvider(identityProvider);
                            logger.info("Identity provider {} {}d for organization {}", identityProviderId, eventType, organizationId);
                        },
                        error -> logger.error("Unable to {} identity provider for organization {}", eventType, organizationId, error),
                        () -> logger.error("No identity provider found with id {}", identityProviderId));
    }

    private void updateAuthenticationProvider(IdentityProvider identityProvider) {
        try {
            if (needDeployment(identityProvider)) {
                logger.info("\tInitializing identity provider: {} for organization {} [{}]", identityProvider.getName(), identityProvider.getReferenceId(), identityProvider.getType());
                // stop existing provider, if any
                clearProvider(identityProvider.getId());
                // create and start the new provider
                var authProviderConfig = new AuthenticationProviderConfiguration(identityProvider);
                var authenticationProvider = identityProviderPluginManager.create(authProviderConfig);
                if (authenticationProvider != null) {
                    // start the authentication provider
                    authenticationProvider.start();
                    providers.put(identityProvider.getId(), authenticationProvider);
                    identities.put(identityProvider.getId(), identityProvider);
                }
                identities.put(identityProvider.getId(), identityProvider);
            } else {
                logger.info("\tIdentity provider already initialized: {} for organization {} [{}]", identityProvider.getName(), identityProvider.getReferenceId(), identityProvider.getType());
            }
        } catch (Exception ex) {
            // failed to load the plugin
            logger.error("An error occurs while initializing the identity provider : {}", identityProvider.getName(), ex);
            clearProvider(identityProvider.getId());
        }
    }

    private void removeIdentityProvider(String identityProviderId) {
        logger.info("Received identity provider event, delete identity provider {}", identityProviderId);
        clearProvider(identityProviderId);
    }

    private void clearProvider(String identityProviderId) {
        AuthenticationProvider authenticationProvider = providers.remove(identityProviderId);
        if (authenticationProvider != null) {
            // stop the authentication provider
            try {
                authenticationProvider.stop();
            } catch (Exception e) {
                logger.error("An error occurs while stopping the identity provider : {}", identityProviderId, e);
            }
        }
        identities.remove(identityProviderId);
    }

    /**
     * @param provider
     * @return true if the IDP has never been deployed or if the deployed version is not up to date
     */
    private boolean needDeployment(IdentityProvider provider) {
        final IdentityProvider deployedProvider = this.identities.get(provider.getId());
        return (deployedProvider == null || deployedProvider.getUpdatedAt().before(provider.getUpdatedAt()));
    }
}
