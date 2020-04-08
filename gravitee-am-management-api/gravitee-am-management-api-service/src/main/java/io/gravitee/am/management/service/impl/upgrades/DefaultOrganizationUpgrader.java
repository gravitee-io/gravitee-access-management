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
import io.gravitee.am.model.*;
import io.gravitee.am.service.*;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.PatchOrganization;
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

    public static String ADMIN_USERNAME = "admin";
    public static String DEFAULT_INLINE_IDP_CONFIG = "{\"users\":[{\"firstname\":\"Administrator\",\"lastname\":\"\",\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"adminadmin\"}]}";

    private final OrganizationService organizationService;

    private final IdentityProviderService identityProviderService;

    private final UserService userService;

    private final MembershipHelper membershipHelper;

    public DefaultOrganizationUpgrader(OrganizationService organizationService, IdentityProviderService identityProviderService, UserService userService, MembershipHelper membershipHelper) {
        this.organizationService = organizationService;
        this.identityProviderService = identityProviderService;
        this.userService = userService;
        this.membershipHelper = membershipHelper;
    }

    @Override
    public boolean upgrade() {

        try {
            Organization organization = organizationService.createDefault().blockingGet();

            if (organization != null) {
                logger.info("Default organization successfully created");

                // Need to create an inline provider and an admin user for this newly created default organization.
                IdentityProvider inlineProvider = createInlineProvider();
                User adminUser = createAdminUser(inlineProvider);
                membershipHelper.setOrganizationPrimaryOwnerRole(adminUser);
            } else {
                logger.info("One or more organizations already exist. Check if default organization is up to date");

                // If default organization exist (and only if), need to check that inline idp and default admin user has 'admin' role.
                organization = organizationService.findById(Organization.DEFAULT).blockingGet();

                if (organization != null) {
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
                        if (inlineIdp.getConfiguration().contains(",\"username\":\"" + ADMIN_USERNAME + "\",") && inlineIdp.getRoleMapper().isEmpty()) {

                            // Check the user admin exists.
                            User adminUser = userService.findByUsernameAndSource(ReferenceType.ORGANIZATION, Organization.DEFAULT, ADMIN_USERNAME, inlineIdp.getId()).blockingGet();

                            if (adminUser == null) {
                                // Create the admin user with organization primary owner role on the default organization.
                                adminUser = createAdminUser(inlineIdp);
                                membershipHelper.setOrganizationPrimaryOwnerRole(adminUser);
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

    private IdentityProvider createInlineProvider() {
        // Create an inline identity provider
        logger.info("Create an user-inline provider");
        NewIdentityProvider adminIdentityProvider = new NewIdentityProvider();
        adminIdentityProvider.setType("inline-am-idp");
        adminIdentityProvider.setName("Inline users");
        adminIdentityProvider.setConfiguration(DEFAULT_INLINE_IDP_CONFIG);

        IdentityProvider createdIdentityProvider = identityProviderService.create(ReferenceType.ORGANIZATION, Organization.DEFAULT, adminIdentityProvider, null).blockingGet();

        logger.info("Associate user-inline provider to default organization");
        PatchOrganization patchOrganization = new PatchOrganization();
        patchOrganization.setIdentities(Collections.singletonList(createdIdentityProvider.getId()));
        organizationService.update(Organization.DEFAULT, patchOrganization, null).blockingGet();

        return createdIdentityProvider;
    }

    private User createAdminUser(IdentityProvider inlineIdp) {

        final User newUser = new User();
        newUser.setInternal(false);
        newUser.setUsername(ADMIN_USERNAME);
        newUser.setSource(inlineIdp.getId());
        newUser.setReferenceType(ReferenceType.ORGANIZATION);
        newUser.setReferenceId(Organization.DEFAULT);

        return userService.create(newUser).blockingGet();
    }

    @Override
    public int getOrder() {
        return 1;
    }
}