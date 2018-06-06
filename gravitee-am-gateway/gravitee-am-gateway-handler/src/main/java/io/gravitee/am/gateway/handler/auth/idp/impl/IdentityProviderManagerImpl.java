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

import io.gravitee.am.gateway.handler.auth.idp.IdentityProviderManager;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.plugins.idp.core.IdentityProviderPluginManager;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderManagerImpl implements IdentityProviderManager, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(IdentityProviderManagerImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private IdentityProviderPluginManager identityProviderPluginManager;

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    private Map<String, AuthenticationProvider> providers = new HashMap<>();
    private Map<String, IdentityProvider> identities = new HashMap<>();

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
    public void afterPropertiesSet() {
        logger.info("Initializing identity providers for domain {}", domain.getName());

        identityProviderRepository.findByDomain(domain.getId())
                .doOnSuccess(identityProviders -> {
                    identityProviders.forEach(identityProvider -> {
                        logger.info("\tInitializing identity provider: {} [{}]", identityProvider.getName(), identityProvider.getType());
                        AuthenticationProvider authenticationProvider =
                                identityProviderPluginManager.create(identityProvider.getType(), identityProvider.getConfiguration(),
                                        identityProvider.getMappers(), identityProvider.getRoleMapper());
                        providers.put(identityProvider.getId(), authenticationProvider);
                        identities.put(identityProvider.getId(), identityProvider);
                    });
                })
                .subscribe(
                        result -> logger.info("Identity providers loaded for domain {}", domain.getName()),
                        error -> logger.error("Unable to initialize identity providers for domain {}", domain.getName(), error)
                );
    }
}
