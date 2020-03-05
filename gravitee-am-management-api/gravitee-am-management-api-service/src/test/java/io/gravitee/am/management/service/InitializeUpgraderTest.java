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
package io.gravitee.am.management.service;

import io.gravitee.am.management.service.impl.upgrades.InitializeUpgrader;
import io.gravitee.am.model.*;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.permissions.ManagementPermission;
import io.gravitee.am.model.permissions.RoleScope;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.*;
import io.gravitee.am.service.model.*;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class InitializeUpgraderTest {

    private final static String ADMIN_DOMAIN = "admin";
    private final static String ADMIN_CLIENT_ID = "admin";
    public static final String ORGANIZATION_ID = "DEFAULT";
    public static final String ENVIRONMENT_ID = "DEFAULT";

    @InjectMocks
    private InitializeUpgrader initializeUpgrader = new InitializeUpgrader();

    @Mock
    private DomainService domainService;

    @Mock
    private ClientService clientService;

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private RoleService roleService;

    @Mock
    private OrganizationService organizationService;

    @Test
    public void shouldCreateAdminDomain() {
        Domain adminDomain = new Domain();
        adminDomain.setId(ADMIN_DOMAIN);
        adminDomain.setName("ADMIN");

        IdentityProvider idp = new IdentityProvider();
        idp.setId("test");

        final Role adminRole = new Role();
        adminRole.setId("role-id");

        when(domainService.create(eq(ORGANIZATION_ID), eq(ENVIRONMENT_ID), any(NewDomain.class))).thenReturn(Single.just(adminDomain));
        when(domainService.findById(ADMIN_DOMAIN)).thenReturn(Maybe.empty());
        when(roleService.createSystemRole(SystemRole.ADMIN, RoleScope.MANAGEMENT, ManagementPermission.permissions())).thenReturn(Single.just(new Role()));
        when(identityProviderService.create(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), any(NewIdentityProvider.class), isNull())).thenReturn(Single.just(idp));
        when(identityProviderService.update(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), eq(idp.getId()), any(UpdateIdentityProvider.class), isNull())).thenReturn(Single.just(new IdentityProvider()));
        when(domainService.update(eq(adminDomain.getId()), any(UpdateDomain.class))).thenReturn(Single.just(adminDomain));
        when(domainService.setMasterDomain(eq(adminDomain.getId()), eq(true))).thenReturn(Single.just(adminDomain));
        when(organizationService.update(eq(ORGANIZATION_ID), any(PatchOrganization.class), isNull())).thenReturn(Single.just(new Organization()));

        initializeUpgrader.upgrade();

        // Ultimately, admin domain will not be created anymore (replaces by default organization).
        verify(domainService, times(1)).findById(ADMIN_DOMAIN);
        verify(identityProviderService, times(1)).create(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), any(NewIdentityProvider.class), isNull());
        verify(domainService, times(1)).create(eq(ORGANIZATION_ID), eq(ENVIRONMENT_ID), any(NewDomain.class));
        verify(domainService, times(1)).update(eq(ADMIN_DOMAIN), any(UpdateDomain.class));
        verify(domainService, times(1)).setMasterDomain(ADMIN_DOMAIN, true);
        verify(clientService, never()).findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID);
    }

    @Test
    public void shouldUpdateAdminDomain_adminClientExists() {
        final Domain adminDomain = new Domain();
        adminDomain.setId(ADMIN_DOMAIN);
        adminDomain.setName("ADMIN");
        adminDomain.setMaster(true);

        final Client adminClient = new Client();
        adminClient.setId(ADMIN_CLIENT_ID);
        adminClient.setClientId(ADMIN_CLIENT_ID);

        when(domainService.findById(ADMIN_DOMAIN)).thenReturn(Maybe.just(adminDomain));
        when(domainService.update(eq(ADMIN_DOMAIN), any(UpdateDomain.class))).thenReturn(Single.just(adminDomain));
        when(clientService.findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID)).thenReturn(Maybe.just(adminClient));
        when(clientService.delete(ADMIN_CLIENT_ID)).thenReturn(Completable.complete());

        initializeUpgrader.upgrade();

        verify(domainService, times(1)).findById(ADMIN_DOMAIN);
        verify(clientService, times(1)).findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID);
        verify(domainService, times(1)).update(eq(ADMIN_DOMAIN), any(UpdateDomain.class));
        verify(clientService, times(1)).delete(ADMIN_CLIENT_ID);

        verify(identityProviderService, never()).create(eq(ADMIN_DOMAIN), any(NewIdentityProvider.class));
        verify(domainService, never()).create(anyString(), anyString(), any(NewDomain.class));
        verify(domainService, never()).setMasterDomain(ADMIN_DOMAIN, true);
    }

    @Test
    public void shouldUpdateAdminDomain_adminClientNotFound() {
        final Domain adminDomain = new Domain();
        adminDomain.setId(ADMIN_DOMAIN);
        adminDomain.setName("ADMIN");
        adminDomain.setMaster(true);

        final Client adminClient = new Client();
        adminClient.setId(ADMIN_CLIENT_ID);
        adminClient.setClientId(ADMIN_CLIENT_ID);

        when(domainService.findById(ADMIN_DOMAIN)).thenReturn(Maybe.just(adminDomain));
        when(clientService.findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID)).thenReturn(Maybe.empty());

        initializeUpgrader.upgrade();

        verify(domainService, times(1)).findById(ADMIN_DOMAIN);
        verify(clientService, times(1)).findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID);

        verify(domainService, never()).update(eq(ADMIN_DOMAIN), any(UpdateDomain.class));
        verify(clientService, never()).delete(ADMIN_CLIENT_ID);
        verify(identityProviderService, never()).create(eq(ADMIN_DOMAIN), any(NewIdentityProvider.class));
        verify(domainService, never()).create(anyString(), anyString(), any(NewDomain.class));
        verify(domainService, never()).setMasterDomain(ADMIN_DOMAIN, true);
    }

    @Test
    public void shouldUpdateMasterFlagAdminDomain_adminClientNotFound() {
        final Domain adminDomain = new Domain();
        adminDomain.setId(ADMIN_DOMAIN);
        adminDomain.setName("ADMIN");
        adminDomain.setMaster(false);

        final Client adminClient = new Client();
        adminClient.setId(ADMIN_CLIENT_ID);
        adminClient.setClientId(ADMIN_CLIENT_ID);

        when(domainService.findById(ADMIN_DOMAIN)).thenReturn(Maybe.just(adminDomain));
        when(clientService.findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID)).thenReturn(Maybe.empty());
        when(domainService.setMasterDomain(ADMIN_DOMAIN, true)).thenReturn(Single.just(adminDomain));

        initializeUpgrader.upgrade();

        verify(domainService, times(1)).findById(ADMIN_DOMAIN);
        verify(clientService, times(1)).findByDomainAndClientId(ADMIN_DOMAIN, ADMIN_CLIENT_ID);
        verify(domainService, times(1)).setMasterDomain(ADMIN_DOMAIN, true);

        verify(domainService, never()).update(eq(ADMIN_DOMAIN), any(UpdateDomain.class));
        verify(clientService, never()).delete(ADMIN_CLIENT_ID);
        verify(identityProviderService, never()).create(eq(ADMIN_DOMAIN), any(NewIdentityProvider.class));
        verify(domainService, never()).create(anyString(), anyString(), any(NewDomain.class));
    }


}
