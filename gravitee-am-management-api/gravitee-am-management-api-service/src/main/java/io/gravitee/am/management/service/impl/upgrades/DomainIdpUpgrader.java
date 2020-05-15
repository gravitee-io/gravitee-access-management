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

import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.IdentityProviderService;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Create default mongo IDP for each domain for user management
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DomainIdpUpgrader implements Upgrader, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(DomainIdpUpgrader.class);
    private static final String DEFAULT_IDP_PREFIX = "default-idp-";

    @Autowired
    private DomainService domainService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Override
    public boolean upgrade() {
        logger.info("Applying domain idp upgrade");
        domainService.findAll()
                .flatMapObservable(Observable::fromIterable)
                .flatMapSingle(this::updateDefaultIdp)
                .subscribe();
        return true;
    }

    private Single<IdentityProvider> updateDefaultIdp(Domain domain) {
        return identityProviderService.findById(DEFAULT_IDP_PREFIX + domain.getId())
                .isEmpty()
                .flatMap(isEmpty -> {
                    if (isEmpty) {
                        logger.info("No default idp found for domain {}, update domain", domain.getName());
                        return identityProviderManager.create(domain.getId());
                    }
                    return Single.just(new IdentityProvider());
                });
    }

    @Override
    public int getOrder() {
        return 7;
    }
}
