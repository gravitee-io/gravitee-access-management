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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.impl.IdentityProviderManagerImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DefaultIdentityProviderUpgrader implements Upgrader, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(DefaultIdentityProviderUpgrader.class);

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private IdentityProviderManagerImpl identityProviderManager;

    @Override
    public boolean upgrade() {
        logger.info("Applying domain idp upgrade");
        identityProviderService.findAll()
                .flatMapSingle(this::updateDefaultIdp)
                .subscribe();
        return true;
    }

    private Single<IdentityProvider> updateDefaultIdp(IdentityProvider identityProvider) {
        UpdateIdentityProvider updateIdentityProvider = new UpdateIdentityProvider();
        updateIdentityProvider.setDomainWhitelist(identityProvider.getDomainWhitelist());
        updateIdentityProvider.setMappers(identityProvider.getMappers());
        updateIdentityProvider.setName(identityProvider.getName());
        updateIdentityProvider.setRoleMapper(identityProvider.getRoleMapper());
        updateIdentityProvider.setConfiguration(identityProvider.isSystem()
                ? identityProvider.getConfiguration() : identityProviderManager.createProviderConfiguration(identityProvider.getReferenceId(), null));

        return identityProviderService.update(identityProvider.getReferenceId(), identityProvider.getId(), updateIdentityProvider, true);
    }

    @Override
    public int getOrder() {
        return UpgraderOrder.DEFAULT_IDP_UPGRADER;
    }
}
