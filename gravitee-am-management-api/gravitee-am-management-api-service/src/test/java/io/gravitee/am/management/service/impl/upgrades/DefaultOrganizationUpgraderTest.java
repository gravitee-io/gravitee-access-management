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

import io.gravitee.am.management.service.impl.upgrades.DefaultOrganizationUpgrader;
import io.gravitee.am.management.service.impl.upgrades.helpers.MembershipHelper;
import io.gravitee.am.model.*;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.PatchOrganization;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultOrganizationUpgraderTest {

    @Mock
    private OrganizationService organizationService;

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private UserService userService;

    @Mock
    private MembershipHelper membershipHelper;

    private DefaultOrganizationUpgrader cut;

    @Before
    public void before() {

        cut = new DefaultOrganizationUpgrader(organizationService, identityProviderService, userService, membershipHelper);
    }

    @Test
    public void shouldCreateDefaultOrganization() {

        IdentityProvider idp = new IdentityProvider();
        idp.setId("test");

        User adminUser = new User();
        adminUser.setId("admin-id");

        when(organizationService.createDefault()).thenReturn(Maybe.just(new Organization()));
        when(identityProviderService.create(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), any(NewIdentityProvider.class), isNull())).thenReturn(Single.just(idp));
        when(organizationService.update(eq(Organization.DEFAULT), any(PatchOrganization.class), isNull())).thenReturn(Single.just(new Organization()));
        when(userService.create(argThat(user -> !user.isInternal()
                && user.getUsername().equals("admin")
                && user.getSource().equals(idp.getId())
                && user.getReferenceType() == ReferenceType.ORGANIZATION
                && user.getReferenceId().equals(Organization.DEFAULT)))).thenReturn(Single.just(adminUser));
        doNothing().when(membershipHelper).setOrganizationAdminRole(argThat(user -> user.getId().equals(adminUser.getId())));

        assertTrue(cut.upgrade());
    }

    @Test
    public void shouldNotUpdateIdentityProviderRoleMapper_noInlineIdp() {

        IdentityProvider idp = new IdentityProvider();
        idp.setId("test");
        idp.setType("idpType");

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("test"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Single.just(Collections.singletonList(idp)));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
    }

    @Test
    public void shouldNotUpdateIdentityProviderRoleMapper_adminUserRemoved() {

        IdentityProvider idp = new IdentityProvider();
        idp.setId("inlineIdpId");
        idp.setType("inline-am-idp");
        idp.setExternal(false);
        idp.setConfiguration("{}"); // no admin user.
        idp.setRoleMapper(Collections.singletonMap("role1", new String[]{"username=test"}));

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("inlineIdpId"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Single.just(Collections.singletonList(idp)));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
    }

    @Test
    public void shouldNotUpdateIdentityProviderRoleMapper_roleMapperIsAlreadySet() {

        IdentityProvider idp = new IdentityProvider();
        idp.setId("inlineIdpId");
        idp.setType("inline-am-idp");
        idp.setExternal(false);
        idp.setConfiguration(DefaultOrganizationUpgrader.DEFAULT_INLINE_IDP_CONFIG);
        idp.setRoleMapper(Collections.singletonMap("role1", new String[]{"username=test"})); // RoleMapper already set.

        final Role adminRole = new Role();
        adminRole.setId("role-id");

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("inlineIdpId"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Single.just(Collections.singletonList(idp)));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
    }

    @Test
    public void shouldCreateAdminUser_noAdminUser() {

        IdentityProvider idp = new IdentityProvider();
        idp.setId("inlineIdpId");
        idp.setType("inline-am-idp");
        idp.setExternal(false);
        idp.setConfiguration(DefaultOrganizationUpgrader.DEFAULT_INLINE_IDP_CONFIG);
        idp.setRoleMapper(new HashMap<>());

        User adminUser = new User();
        adminUser.setId("admin-id");

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("inlineIdpId"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Single.just(Collections.singletonList(idp)));
        when(userService.findByUsernameAndSource(ReferenceType.ORGANIZATION, Organization.DEFAULT, "admin", idp.getId())).thenReturn(Maybe.empty());
        when(userService.create(any(User.class))).thenReturn(Single.just(adminUser));
        doNothing().when(membershipHelper).setOrganizationAdminRole(argThat(user -> user.getId().equals(adminUser.getId())));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
    }

    @Test
    public void shouldNotUpdateAdminUser_adminAlreadyExist() {

        IdentityProvider idp = new IdentityProvider();
        idp.setId("inlineIdpId");
        idp.setType("inline-am-idp");
        idp.setExternal(false);
        idp.setConfiguration(DefaultOrganizationUpgrader.DEFAULT_INLINE_IDP_CONFIG);
        idp.setRoleMapper(new HashMap<>());

        User adminUser = new User();
        adminUser.setId("adminId");
        adminUser.setUsername("admin");
        adminUser.setLoginsCount(10);
        adminUser.setRoles(Arrays.asList("role-id"));

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("inlineIdpId"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Single.just(Collections.singletonList(idp)));
        when(userService.findByUsernameAndSource(ReferenceType.ORGANIZATION, Organization.DEFAULT, "admin", idp.getId())).thenReturn(Maybe.just(adminUser)); // Admin already exists.
        when(organizationService.createDefault()).thenReturn(Maybe.empty());

        assertTrue(cut.upgrade());
    }

    @Test
    public void shouldCreateDefaultOrganization_technicalError() {

        when(organizationService.createDefault()).thenReturn(Maybe.error(TechnicalException::new));

        assertFalse(cut.upgrade());
    }
}