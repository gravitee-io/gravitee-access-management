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

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.permissions.ManagementPermission;
import io.gravitee.am.model.permissions.RoleScope;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.PatchOrganization;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DefaultOrganizationUpgrader implements Upgrader, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(DefaultOrganizationUpgrader.class);

    private final OrganizationService organizationService;

    private final RoleService roleService;

    private final IdentityProviderService identityProviderService;

    public DefaultOrganizationUpgrader(OrganizationService organizationService, RoleService roleService, IdentityProviderService identityProviderService) {
        this.organizationService = organizationService;
        this.roleService = roleService;
        this.identityProviderService = identityProviderService;
    }

    @Override
    public boolean upgrade() {

        try {
            Organization organization = organizationService.createDefault().blockingGet();

            if (organization != null) {
                logger.info("Default organization successfully created");

                // Create default admin role
                Role adminRole = roleService.createSystemRole(SystemRole.ADMIN, RoleScope.MANAGEMENT, ManagementPermission.permissions()).blockingGet();

                // FIXME: **HACK** --> begin : admin idp is temporary reused for default organization (this will be soon completely removed).
                // Create an inline identity provider
                logger.info("Create an user-inline provider");
                NewIdentityProvider adminIdentityProvider = new NewIdentityProvider();
                adminIdentityProvider.setType("inline-am-idp");
                adminIdentityProvider.setName("Inline users");
                adminIdentityProvider.setConfiguration("{\"users\":[{\"firstname\":\"Administrator\",\"lastname\":\"\",\"username\":\"admin\",\"password\":\"adminadmin\"}]}");

                IdentityProvider createdIdentityProvider = identityProviderService.create(ReferenceType.ORGANIZATION, Organization.DEFAULT, adminIdentityProvider, null).blockingGet();

                // Update inline identity provider to apply default role mapping
                UpdateIdentityProvider updateIdentityProvider = new UpdateIdentityProvider();
                updateIdentityProvider.setName(createdIdentityProvider.getName());
                updateIdentityProvider.setConfiguration(createdIdentityProvider.getConfiguration());
                updateIdentityProvider.setRoleMapper(Collections.singletonMap(adminRole.getId(), new String[]{"username=admin"}));
                identityProviderService.update(ReferenceType.ORGANIZATION, Organization.DEFAULT, createdIdentityProvider.getId(), updateIdentityProvider, null).blockingGet();

                logger.info("Associate user-inline provider to default organization");
                PatchOrganization patchOrganization = new PatchOrganization();
                patchOrganization.setIdentities(Collections.singletonList(createdIdentityProvider.getId()));
                organizationService.update(Organization.DEFAULT, patchOrganization, null).blockingGet();
            } else {
                logger.info("One or more organizations already exist. Skip");
            }
        } catch (Exception e) {
            logger.error("An error occurred trying to initialize default organization", e);
            return false;
        }

        return true;
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

}
