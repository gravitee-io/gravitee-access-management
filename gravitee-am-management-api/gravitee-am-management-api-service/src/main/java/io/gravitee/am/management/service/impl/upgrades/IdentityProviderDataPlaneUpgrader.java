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

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.IDP_DATA_PLANE_UPGRADER;

import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.IdentityProviderService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IdentityProviderDataPlaneUpgrader extends AsyncUpgrader{

    @Autowired
    private IdentityProviderService identityProviderService;

    @Override
    Completable doUpgrade() {
        log.debug("Applying Data Plane for IdentityProvider upgrade");
        return Completable.fromPublisher(identityProviderService.findAll(ReferenceType.DOMAIN)
                .flatMapMaybe(this::upgradeDomain));
    }

    private Maybe<IdentityProvider> upgradeDomain(IdentityProvider idp) {
        if (idp.getDataPlaneId() == null) {
            return Maybe.fromSingle(identityProviderService.assignDataPlane(idp, DataPlaneDescription.DEFAULT_DATA_PLANE_ID));
        }
        return Maybe.empty();
    }

    @Override
    public int getOrder() {
        return IDP_DATA_PLANE_UPGRADER;
    }
}
