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
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.RoleService;
import io.gravitee.node.api.upgrader.Upgrader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ManagementRepositoryScope
@Slf4j
public class OrganizationRolesUpgrader implements Upgrader {


    private final OrganizationService organizationService;
    private final RoleService roleService;

    private static final String VERSION = "4_11_0_a";

    @Override
    public boolean upgrade() {
        try {
            log.info("Applying default roles upgrade for all organizations");
            organizationService.findAll()
                    .map(Organization::getId)
                    .doOnNext(orgId -> log.info("Default roles upgrade start for org={}", orgId))
                    .flatMapCompletable(roleService::createDefaultRoles)
                    .doOnComplete(() -> log.info("All default roles upgraded."))
                    .blockingAwait();

        } catch (Throwable e) {
            log.error("An error occurs while updating default roles for organizations", e);
            return false;
        }

        return true;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public int getOrder() {
        return UpgraderOrder.ORGANIZATION_STANDARD_ROLE_UPGRADER;
    }
}
