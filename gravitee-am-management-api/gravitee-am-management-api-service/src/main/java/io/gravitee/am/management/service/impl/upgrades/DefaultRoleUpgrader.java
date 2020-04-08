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

import io.gravitee.am.management.service.impl.upgrades.helpers.MembershipHelper;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DefaultRoleUpgrader implements Upgrader, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(DefaultRoleUpgrader.class);
    static final int PAGE_SIZE = 10;

    private final RoleService roleService;
    private final UserService userService;
    private final MembershipHelper membershipHelper;

    public DefaultRoleUpgrader(RoleService roleService,
                               UserService userService,
                               MembershipHelper membershipHelper) {
        this.roleService = roleService;
        this.userService = userService;
        this.membershipHelper = membershipHelper;
    }

    @Override
    public boolean upgrade() {

        logger.info("Applying default roles upgrade");

        try {
            Boolean organizationOwnerRoleNotExists = roleService.findDefaultRole(Organization.DEFAULT, DefaultRole.ORGANIZATION_OWNER, ReferenceType.ORGANIZATION).isEmpty().blockingGet();
            Throwable throwable = roleService.createOrUpdateSystemRoles().blockingGet();

            if (throwable != null) {
                throw throwable;
            } else if (organizationOwnerRoleNotExists) {
                Role organizationOwnerRole = roleService.findDefaultRole(Organization.DEFAULT, DefaultRole.ORGANIZATION_OWNER, ReferenceType.ORGANIZATION).blockingGet();

                // Must grant owner power to all existing users to be iso-functional with v2 where all users could do everything.
                Page<User> userPage;
                int page = 0;

                do {
                    userPage = userService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT, page, PAGE_SIZE).blockingGet();
                    userPage.getData().forEach(user -> membershipHelper.setRole(user, organizationOwnerRole));
                    page++;
                } while (userPage.getData().size() == PAGE_SIZE);
            }
            logger.info("Default roles upgrade, done.");
        } catch (Throwable e) {
            logger.error("An error occurs while updating default roles", e);
            return false;
        }

        return true;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}