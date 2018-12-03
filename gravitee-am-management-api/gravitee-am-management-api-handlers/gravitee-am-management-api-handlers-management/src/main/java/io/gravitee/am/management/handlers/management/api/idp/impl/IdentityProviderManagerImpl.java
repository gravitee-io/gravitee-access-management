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
package io.gravitee.am.management.handlers.management.api.idp.impl;

import io.gravitee.am.management.handlers.management.api.idp.IdentityProviderManager;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.IdentityProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderManagerImpl implements IdentityProviderManager, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(IdentityProviderManagerImpl.class);

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private io.gravitee.am.management.service.IdentityProviderManager identityProviderManager;

    @Override
    public void afterPropertiesSet() {
        logger.info("Initializing user providers");
        List<IdentityProvider> identities = identityProviderService.findAll().blockingGet();
        identities.forEach(this::loadUserProvider);
    }

    private void loadUserProvider(IdentityProvider identityProvider) {
        identityProviderManager.reloadUserProvider(identityProvider).subscribe();
    }
}
