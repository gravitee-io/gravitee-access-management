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
import io.gravitee.am.gateway.handler.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.granter.CompositeTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.extensiongrant.ExtensionGrantGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.extensiongrant.ExtensionGrantManager;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.plugins.extensiongrant.core.ExtensionGrantPluginManager;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExtensionGrantManagerImpl implements ExtensionGrantManager, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionGrantManagerImpl.class);

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

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing extension grants for domain {}", domain.getName());
        extensionGrantRepository.findByDomain(domain.getId())
                .flatMapObservable(extensionGrants -> Observable.fromIterable(extensionGrants))
                .flatMapSingle(extensionGrant -> {
                    if (extensionGrant.getIdentityProvider() != null) {
                        logger.info("\tLooking for extension grant identity provider: {}", extensionGrant.getIdentityProvider());
                        return identityProviderManager.get(extensionGrant.getIdentityProvider())
                                .map(authenticationProvider -> Optional.of(authenticationProvider))
                                .defaultIfEmpty(Optional.empty())
                                .toSingle()
                                .map(optAuthenticationProvider -> {
                                    if (optAuthenticationProvider.isPresent()) {
                                        logger.info("\tExtension grant identity provider: {}, loaded", extensionGrant.getIdentityProvider());
                                    }
                                    ExtensionGrantProvider extensionGrantProvider = extensionGrantPluginManager.create(extensionGrant.getType(), extensionGrant.getConfiguration(), optAuthenticationProvider.get());
                                    return new ExtensionGrantData(extensionGrant, extensionGrantProvider);
                                });
                    } else {
                        ExtensionGrantProvider extensionGrantProvider = extensionGrantPluginManager.create(extensionGrant.getType(), extensionGrant.getConfiguration(), null);
                        return Single.just(new ExtensionGrantData(extensionGrant, extensionGrantProvider));
                    }
                })
                .toList()
                .subscribe(
                        extensionGrantsData -> {
                                extensionGrantsData.forEach(extensionGrantData -> {
                                    ExtensionGrant extensionGrant = extensionGrantData.getExtensionGrant();
                                    ExtensionGrantProvider extensionGrantProvider = extensionGrantData.getExtensionGrantProvider();
                                    logger.info("\tInitializing extension grant: {} [{}]", extensionGrant.getName(), extensionGrant.getType());
                                    ExtensionGrantGranter extensionGrantGranter =
                                            new ExtensionGrantGranter(extensionGrantProvider, extensionGrant, userService, tokenService);
                                    ((CompositeTokenGranter) tokenGranter).addTokenGranter(extensionGrantGranter);
                                });
                                logger.info("Extension grants loaded for domain {}", domain.getName());
                            },
                        error -> logger.error("Unable to initialize extension grants for domain {}", domain.getName(), error)
                );
    }

    private class ExtensionGrantData {
        private ExtensionGrant extensionGrant;
        private ExtensionGrantProvider extensionGrantProvider;

        public ExtensionGrantData(ExtensionGrant extensionGrant, ExtensionGrantProvider extensionGrantProvider) {
            this.extensionGrant = extensionGrant;
            this.extensionGrantProvider = extensionGrantProvider;
        }

        public ExtensionGrant getExtensionGrant() {
            return extensionGrant;
        }

        public ExtensionGrantProvider getExtensionGrantProvider() {
            return extensionGrantProvider;
        }
    }
}
