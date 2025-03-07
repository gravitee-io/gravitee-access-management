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
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.Reference;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.ReporterService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
@ManagementRepositoryScope
public class OrganizationReporterUpgrader extends AsyncUpgrader {
    private final OrganizationService organizationService;
    private final ReporterService reporterService;

    @Override
    Completable doUpgrade() {

        var orgReference = Reference.organization(Organization.DEFAULT);
        return organizationService.findById(orgReference.id())
                .flatMapMaybe(org -> reporterService.findByReference(orgReference).firstElement())
                .doOnSuccess(r->log.info("{} already has a configured reporter", orgReference))
                .switchIfEmpty(Single.defer(() -> reporterService.createDefault(orgReference)))
                .ignoreElement();
    }

    @Override
    public int getOrder() {
        return UpgraderOrder.ORG_DEFAULT_REPORTER_UPGRADER;
    }
}
