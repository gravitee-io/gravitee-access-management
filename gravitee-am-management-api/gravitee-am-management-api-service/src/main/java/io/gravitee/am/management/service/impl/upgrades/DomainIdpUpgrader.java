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

import io.gravitee.am.management.service.DefaultIdentityProviderService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.IdentityProviderService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static io.gravitee.am.management.service.impl.DefaultIdentityProviderServiceImpl.DEFAULT_IDP_PREFIX;
import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.DOMAIN_IDP_UPGRADER;

/**
 * Create default mongo IDP for each domain for user management
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@RequiredArgsConstructor
public class DomainIdpUpgrader extends AsyncUpgrader {

    private static final Logger logger = LoggerFactory.getLogger(DomainIdpUpgrader.class);

    private final DomainService domainService;
    private final IdentityProviderService identityProviderService;
    private final DefaultIdentityProviderService defaultIdentityProviderService;

    @Override
    public Completable doUpgrade() {
        logger.info("Applying domain idp upgrade");
        return Completable.fromPublisher(domainService.listAll()
                .flatMapSingle(this::updateDefaultIdp));

    }

    private Single<IdentityProvider> updateDefaultIdp(Domain domain) {
        return identityProviderService.findById(DEFAULT_IDP_PREFIX + domain.getId())
                .isEmpty()
                .flatMap
                        (isEmpty -> {
                    if (isEmpty) {
                        logger.info("No default idp found for domain {}, update domain", domain.getName());
                        return defaultIdentityProviderService.create(domain.getId());
                    }
                    return Single.just(new IdentityProvider());
                });
    }

    @Override
    public int getOrder() {
        return DOMAIN_IDP_UPGRADER;
    }

}
