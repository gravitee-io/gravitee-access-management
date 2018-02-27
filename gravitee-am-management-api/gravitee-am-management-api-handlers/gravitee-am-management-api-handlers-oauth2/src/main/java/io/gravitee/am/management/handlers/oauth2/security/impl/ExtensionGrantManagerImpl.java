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
package io.gravitee.am.management.handlers.oauth2.security.impl;

import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.management.extensiongrant.core.ExtensionGrantPluginManager;
import io.gravitee.am.management.handlers.oauth2.security.ExtensionGrantManager;
import io.gravitee.am.management.handlers.oauth2.security.IdentityProviderManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.service.ExtensionGrantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExtensionGrantManagerImpl implements ExtensionGrantManager, InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(ExtensionGrantManagerImpl.class);

    @Autowired
    private Domain domain;

    @Autowired
    private ExtensionGrantPluginManager extensionGrantPluginManager;

    @Autowired
    private ExtensionGrantService extensionGrantService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    private Map<String, ExtensionGrantProvider> providers = new HashMap<>();

    private Map<String, ExtensionGrant> grantTypes = new HashMap<>();

    @Override
    public ExtensionGrantProvider get(String id) {
        return providers.get(id);
    }

    @Override
    public ExtensionGrant getTokenGranter(String id) {
        return grantTypes.get(id);
    }

    @Override
    public Map<String, ExtensionGrantProvider> providers() {
        return providers;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        logger.info("Initializing extension grants for domain {}", domain.getName());
        // TODO async call
        List<ExtensionGrant> extensionGrants = extensionGrantService.findByDomain(domain.getId()).blockingGet();

        extensionGrants.forEach(extensionGrant -> {
            logger.info("\tInitializing extension grant : {} [{}]", extensionGrant.getName(), extensionGrant.getType());

            AuthenticationProvider authenticationProvider = null;
            if (extensionGrant.getIdentityProvider() != null) {
                logger.info("\tLooking for extension grant identity provider: {}", extensionGrant.getIdentityProvider());
                authenticationProvider = identityProviderManager.get(extensionGrant.getIdentityProvider());
                if (authenticationProvider != null) {
                    logger.info("\tExtension grant identity provider: {}, loaded", extensionGrant.getIdentityProvider());
                }
            }

            ExtensionGrantProvider extensionGrantProvider =
                    extensionGrantPluginManager.create(extensionGrant.getType(), extensionGrant.getConfiguration(), authenticationProvider);
            grantTypes.put(extensionGrant.getId(), extensionGrant);
            providers.put(extensionGrant.getId(), extensionGrantProvider);
        });
    }
}
