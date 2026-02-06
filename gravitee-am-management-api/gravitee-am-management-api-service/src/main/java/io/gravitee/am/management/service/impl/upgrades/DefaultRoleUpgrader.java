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
import io.gravitee.am.service.RoleService;
import io.gravitee.node.api.upgrader.Upgrader;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@RequiredArgsConstructor
@ManagementRepositoryScope
public class DefaultRoleUpgrader implements Upgrader {

    private static final Logger logger = LoggerFactory.getLogger(DefaultRoleUpgrader.class);

    private final RoleService roleService;

    // bump every time system roles are modified
    private static final String VERSION = "4_11_0_a";

    @Override
    public boolean upgrade() {
        logger.info("Applying default roles upgrade");
        try {
            // create or update system roles
            roleService.createOrUpdateSystemRoles().blockingAwait();
            logger.info("Default roles upgrade, done.");
        } catch (Throwable e) {
            logger.error("An error occurs while updating default roles", e);
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
        return UpgraderOrder.DEFAULT_ROLE_UPGRADER;
    }
}
