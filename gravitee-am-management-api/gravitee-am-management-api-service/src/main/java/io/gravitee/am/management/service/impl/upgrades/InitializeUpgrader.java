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
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.permissions.ManagementPermission;
import io.gravitee.am.model.permissions.RoleScope;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class InitializeUpgrader implements Upgrader, Ordered {

    public static final String ORGANIZATION_ID = "DEFAULT";
    public static final String ENVIRONMENT_ID = "DEFAULT";
    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(InitializeUpgrader.class);
    private final static String ADMIN_DOMAIN = "admin";
    private final static String ADMIN_CLIENT_ID = "admin";

    @Autowired
    private DomainService domainService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private OrganizationService organizationService;

    @Override
    public boolean upgrade() {
        logger.info("Looking for a registered {} domain", ADMIN_DOMAIN);

        // Initialize Upgrader must end before the others upgraders (i.e use blocking call)
        try {
            Domain adminDomain = domainService.findById(ADMIN_DOMAIN).blockingGet();
            if (adminDomain == null) {
                throw new DomainNotFoundException(ADMIN_DOMAIN);
            }

            logger.info("{} domain already exists. Apply required upgrades.", ADMIN_DOMAIN);
            if (!adminDomain.isMaster()) {
                logger.info("Set master flag for security domain {}", ADMIN_DOMAIN);
                adminDomain = domainService.setMasterDomain(adminDomain.getId(), true).blockingGet();
            }

            // New since AM v2
            // Move admin client identity providers to admin domain and remove the admin client
            Client adminClient = clientService.findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID).blockingGet();
            if (adminClient != null) {
                logger.info("Admin client found, move its identity providers to the admin domain");
                UpdateDomain updateDomain = new UpdateDomain();
                updateDomain.setName(adminDomain.getName());
                updateDomain.setPath(adminDomain.getPath());
                updateDomain.setDescription(adminDomain.getDescription());
                updateDomain.setEnabled(adminDomain.isEnabled());
                updateDomain.setIdentities(adminClient.getIdentities());
                domainService.update(ADMIN_DOMAIN, updateDomain).blockingGet();
                // remove admin client
                clientService.delete(adminClient.getId()).blockingGet();
            }
        } catch (Exception ex) {
            if (ex instanceof DomainNotFoundException) {
                // Admin domain is (for now) still created to handle authentication.
                domainNotFoundFallback();
            } else {
                throw new TechnicalManagementException(ex);
            }
        }

        return true;
    }

    private Domain domainNotFoundFallback() {
        // Create a new admin domain
        logger.info("{} domain does not exists. Creating it.", ADMIN_DOMAIN);
        NewDomain adminDomain = new NewDomain();
        adminDomain.setName("admin");
        adminDomain.setDescription("AM Admin domain");
        Domain createdDomain = domainService.create(ORGANIZATION_ID, ENVIRONMENT_ID, adminDomain).blockingGet();

        // Create default admin role
        Role adminRole = roleService.createSystemRole(SystemRole.ADMIN, RoleScope.MANAGEMENT, ManagementPermission.permissions()).blockingGet();

        // FIXME: **HACK** --> begin : admin idp is temporary reused for default organization (this will be soon completely removed).
        // Create an inline identity provider
        logger.info("Create an user-inline provider");
        NewIdentityProvider adminIdentityProvider = new NewIdentityProvider();
        adminIdentityProvider.setType("inline-am-idp");
        adminIdentityProvider.setName("Inline users");
        adminIdentityProvider.setConfiguration("{\"users\":[{\"firstname\":\"Administrator\",\"lastname\":\"\",\"username\":\"admin\",\"password\":\"adminadmin\"}]}");

        IdentityProvider createdIdentityProvider = identityProviderService.create(ReferenceType.ORGANIZATION, ORGANIZATION_ID, adminIdentityProvider, null).blockingGet();
        // Update inline identity provider to apply default role mapping
        UpdateIdentityProvider updateIdentityProvider = new UpdateIdentityProvider();
        updateIdentityProvider.setName(createdIdentityProvider.getName());
        updateIdentityProvider.setConfiguration(createdIdentityProvider.getConfiguration());
        updateIdentityProvider.setRoleMapper(Collections.singletonMap(adminRole.getId(), new String[]{"username=admin"}));
        identityProviderService.update(ReferenceType.ORGANIZATION, ORGANIZATION_ID, createdIdentityProvider.getId(), updateIdentityProvider, null).blockingGet();

        logger.info("Associate user-inline provider to previously created domain");
        UpdateDomain updateDomain = new UpdateDomain();
        updateDomain.setName(createdDomain.getName());
        updateDomain.setPath(createdDomain.getPath());
        updateDomain.setDescription(createdDomain.getDescription());
        updateDomain.setEnabled(createdDomain.isEnabled());
        updateDomain.setIdentities(Collections.singleton(createdIdentityProvider.getId()));
        updateDomain.setEnabled(true);
        domainService.update(createdDomain.getId(), updateDomain).blockingGet();

        logger.info("Associate user-inline provider to default organization");
        PatchOrganization patchOrganization = new PatchOrganization();
        patchOrganization.setIdentities(Collections.singleton(createdIdentityProvider.getId()));
        organizationService.update(Organization.DEFAULT, patchOrganization, null).blockingGet();
        // FIXME: **HACK** --> end.

        logger.info("Set master flag for security domain {}", ADMIN_DOMAIN);
        return domainService.setMasterDomain(createdDomain.getId(), true).blockingGet();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
