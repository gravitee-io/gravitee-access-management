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

import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Domain;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.DOMAIN_DATA_PLANE_UPGRADER;

@Slf4j
@Component
@ManagementRepositoryScope
public class DomainDataPlaneUpgrader extends AsyncUpgrader{

    @Autowired
    private DomainService domainService;

    @Override
    Completable doUpgrade() {
        log.debug("Applying Data Plane for Domain upgrade");
        return Completable.fromPublisher(domainService.listAll()
                .flatMapMaybe(this::upgradeDomain));
    }

    private Maybe<Domain> upgradeDomain(Domain domain) {
        if (domain.getDataPlaneId() == null) {
            domain.setDataPlaneId("default");
            return Maybe.fromSingle(domainService.update(domain.getId(), domain));
        }
        return Maybe.empty();
    }

    @Override
    public int getOrder() {
        return DOMAIN_DATA_PLANE_UPGRADER;
    }
}
