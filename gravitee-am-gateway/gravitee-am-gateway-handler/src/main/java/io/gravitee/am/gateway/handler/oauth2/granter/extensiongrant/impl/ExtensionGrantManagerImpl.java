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
package io.gravitee.am.gateway.handler.oauth2.granter.extensiongrant.impl;

import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.gateway.core.event.ExtensionGrantEvent;
import io.gravitee.am.gateway.handler.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.granter.CompositeTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.extensiongrant.ExtensionGrantGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.extensiongrant.ExtensionGrantManager;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.service.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.extensiongrant.core.ExtensionGrantPluginManager;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExtensionGrantManagerImpl extends AbstractService implements ExtensionGrantManager, InitializingBean, EventListener<ExtensionGrantEvent, Payload> {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionGrantManagerImpl.class);
    private TokenRequestResolver tokenRequestResolver = new TokenRequestResolver();

    @Autowired
    private Domain domain;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenGranter tokenGranter;

    @Autowired
    private ExtensionGrantPluginManager extensionGrantPluginManager;

    @Autowired
    private ExtensionGrantRepository extensionGrantRepository;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private EventManager eventManager;

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing extension grants for domain {}", domain.getName());
        extensionGrantRepository.findByDomain(domain.getId())
                .subscribe(
                        extensionGrants -> {
                            extensionGrants.forEach(extensionGrant -> updateExtensionGrantProvider(extensionGrant));
                            logger.info("Extension grants loaded for domain {}", domain.getName());
                        },
                        error -> logger.error("Unable to initialize extension grants for domain {}", domain.getName(), error));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        logger.info("Register event listener for extension grant events");
        eventManager.subscribeForEvents(this, ExtensionGrantEvent.class);
    }

    @Override
    public void onEvent(Event<ExtensionGrantEvent, Payload> event) {
        if (domain.getId().equals(event.content().getDomain())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    updateExtensionGrant(event.content().getId(), event.type());
                    break;
                case UNDEPLOY:
                    removeExtensionGrant(event.content().getId());
                    break;
            }
        }
    }

    private void updateExtensionGrant(String extensionGrantId, ExtensionGrantEvent extensionGrantEvent) {
        final String eventType = extensionGrantEvent.toString().toLowerCase();
        logger.info("Domain {} has received {} extension grant event for {}", domain.getName(), eventType, extensionGrantId);
        extensionGrantRepository.findById(extensionGrantId)
                .subscribe(
                        extensionGrant -> {
                            updateExtensionGrantProvider(extensionGrant);
                            logger.info("Extension grant {} {}d for domain {}", extensionGrantId, eventType, domain.getName());
                        },
                        error -> logger.error("Unable to {} extension grant for domain {}", eventType, domain.getName(), error),
                        () -> logger.error("No extension grant found with id {}", extensionGrantId));
    }

    private void removeExtensionGrant(String extensionGrantId) {
        logger.info("Domain {} has received extension grant event, delete extension grant {}", domain.getName(), extensionGrantId);
        ((CompositeTokenGranter) tokenGranter).removeTokenGranter(extensionGrantId);
    }

    private void updateExtensionGrantProvider(ExtensionGrant extensionGrant) {
        AuthenticationProvider authenticationProvider = null;
        if (extensionGrant.getIdentityProvider() != null) {
            logger.info("\tLooking for extension grant identity provider: {}", extensionGrant.getIdentityProvider());
            authenticationProvider = identityProviderManager.get(extensionGrant.getIdentityProvider()).blockingGet();
            if (authenticationProvider != null) {
                logger.info("\tExtension grant identity provider: {}, loaded", extensionGrant.getIdentityProvider());
            }
        }

        ExtensionGrantProvider extensionGrantProvider = extensionGrantPluginManager.create(extensionGrant.getType(), extensionGrant.getConfiguration(), authenticationProvider);
        ExtensionGrantGranter extensionGrantGranter = new ExtensionGrantGranter(extensionGrantProvider, extensionGrant, userService, tokenService, tokenRequestResolver, domain);
        ((CompositeTokenGranter) tokenGranter).addTokenGranter(extensionGrant.getId(), extensionGrantGranter);
    }
}
