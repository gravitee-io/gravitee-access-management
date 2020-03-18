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

import io.gravitee.am.model.*;
import io.gravitee.am.model.permissions.RoleScope;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.PatchOrganization;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DefaultOrganizationUpgrader implements Upgrader, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(DefaultOrganizationUpgrader.class);

    public static String DEFAULT_INLINE_IDP_CONFIG = "{\"users\":[{\"firstname\":\"Administrator\",\"lastname\":\"\",\"username\":\"admin\",\"password\":\"adminadmin\"}]}";

    private final OrganizationService organizationService;

    private final RoleService roleService;

    private final IdentityProviderService identityProviderService;

    private final UserService userService;

    public DefaultOrganizationUpgrader(OrganizationService organizationService, RoleService roleService, IdentityProviderService identityProviderService, UserService userService) {
        this.organizationService = organizationService;
        this.roleService = roleService;
        this.identityProviderService = identityProviderService;
        this.userService = userService;
    }

    @Override
    public boolean upgrade() {

        try {
            Role adminRole = roleService.findSystemRole(SystemRole.ADMIN, RoleScope.MANAGEMENT).blockingGet();
            Organization organization = organizationService.createDefault().blockingGet();

            if (organization != null) {
                logger.info("Default organization successfully created");

                // Need to create an inline provider for this newly created default organization.
                createInlineProvider(adminRole);
            } else {
                logger.info("One or more organizations already exist. Check if default organization is up to date");

                // If default organization exist (and only if), need to check that inline idp and default admin user has 'admin' role.
                organization = organizationService.findById(Organization.DEFAULT).blockingGet();
                final List<String> identities = organization.getIdentities();

                IdentityProvider inlineIdp = identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)
                        .flattenAsFlowable(identityProviders -> identityProviders)
                        .filter(identityProvider -> identityProvider.getType().equals("inline-am-idp")
                                && !identityProvider.isExternal()
                                && identities.contains(identityProvider.getId()))
                        .firstElement().blockingGet();

                // If inline idp doesn't exist or is not enabled, it is probably an administrator choice. So do not go further.
                if (inlineIdp != null) {
                    // If inline idp doesn't have "admin" user in its configuration, it is probably an administrator choice. So do not go further.
                    if (inlineIdp.getConfiguration().contains(",\"username\":\"admin\",") && inlineIdp.getRoleMapper().isEmpty()) {

                        // Inline is still enabled and admin user is still configured. Update inlineIdp with admin role mapper.
                        updateIdentityProviderRoleMapper(inlineIdp, adminRole);

                        // The check the user admin as admin role.
                        User adminUser = userService.findByUsernameAndSource(ReferenceType.ORGANIZATION, Organization.DEFAULT, "admin", inlineIdp.getId()).blockingGet();

                        // If adminUser is null or has 0 login count, it means he never logged in. 'admin' role will be set at first connection. So do not go further.
                        if (adminUser != null && adminUser.getLoginsCount() > 0) {
                            boolean hasAdminRole = adminUser.getRoles().stream().anyMatch(roleId -> adminRole.getId().equals(roleId));

                            // Add 'admin' role only if he hasn't already.
                            if (!hasAdminRole) {
                                adminUser.getRoles().add(adminRole.getId());
                                userService.update(adminUser);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("An error occurred trying to initialize default organization", e);
            return false;
        }

        return true;
    }

    private void createInlineProvider(Role adminRole) {
        // Create an inline identity provider
        logger.info("Create an user-inline provider");
        NewIdentityProvider adminIdentityProvider = new NewIdentityProvider();
        adminIdentityProvider.setType("inline-am-idp");
        adminIdentityProvider.setName("Inline users");
        adminIdentityProvider.setConfiguration(DEFAULT_INLINE_IDP_CONFIG);

        IdentityProvider createdIdentityProvider = identityProviderService.create(ReferenceType.ORGANIZATION, Organization.DEFAULT, adminIdentityProvider, null).blockingGet();

        // Update inline identity provider to apply default role mapping
        updateIdentityProviderRoleMapper(createdIdentityProvider, adminRole);

        logger.info("Associate user-inline provider to default organization");
        PatchOrganization patchOrganization = new PatchOrganization();
        patchOrganization.setIdentities(Collections.singletonList(createdIdentityProvider.getId()));
        organizationService.update(Organization.DEFAULT, patchOrganization, null).blockingGet();
    }

    private void updateIdentityProviderRoleMapper(IdentityProvider currentIdp, Role adminRole) {

        // Update inline identity provider to apply default role mapping
        UpdateIdentityProvider updateIdentityProvider = new UpdateIdentityProvider();
        updateIdentityProvider.setName(currentIdp.getName());
        updateIdentityProvider.setConfiguration(currentIdp.getConfiguration());
        updateIdentityProvider.setRoleMapper(Collections.singletonMap(adminRole.getId(), new String[]{"username=admin"}));

        identityProviderService.update(ReferenceType.ORGANIZATION, Organization.DEFAULT, currentIdp.getId(), updateIdentityProvider, null).blockingGet();
    }

    @Override
    public int getOrder() {
        return 1;
    }

}
