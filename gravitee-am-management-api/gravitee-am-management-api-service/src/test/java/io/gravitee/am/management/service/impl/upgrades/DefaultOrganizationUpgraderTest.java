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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.PatchOrganization;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class DefaultOrganizationUpgraderTest {

    @Mock
    private OrganizationService organizationService;

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private OrganizationUserService userService;

    @Mock
    private MembershipHelper membershipHelper;

    @Mock
    private RoleService roleService;

    @Mock
    private DomainService domainService;

    @Mock
    private Environment environment;

    private DefaultOrganizationUpgrader cut;

    @BeforeEach
    public void before() {
        when(environment.getProperty("security.defaultAdmin", boolean.class, true)).thenReturn(true);
        cut = new DefaultOrganizationUpgrader(organizationService, identityProviderService, userService, membershipHelper, roleService, domainService, environment, null);
    }

    @Test
    public void shouldCreateDefaultOrganization() {

        IdentityProvider idp = new IdentityProvider();
        idp.setId("test");

        User adminUser = new User();
        adminUser.setId("admin-id");

        final Organization organization = new Organization();
        when(organizationService.createDefault()).thenReturn(Maybe.just(organization));
        when(identityProviderService.create(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), any(NewIdentityProvider.class), isNull(), anyBoolean())).thenReturn(Single.just(idp));
        when(organizationService.update(eq(Organization.DEFAULT), any(PatchOrganization.class), isNull())).thenReturn(Single.just(organization));
        when(userService.create(argThat(user -> !user.isInternal()
                && user.getUsername().equals("admin")
                && user.getSource().equals(idp.getId())
                && user.getReferenceType() == ReferenceType.ORGANIZATION
                && user.getReferenceId().equals(Organization.DEFAULT)))).thenReturn(Single.just(adminUser));
        when(domainService.findById("admin")).thenReturn(Maybe.empty());
        doNothing().when(membershipHelper).setOrganizationPrimaryOwnerRole(argThat(user -> user.getId().equals(adminUser.getId())));
        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(organization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.empty());

        assertTrue(cut.upgrade());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
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
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
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
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
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
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
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
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));
        when(userService.findByUsernameAndSource(ReferenceType.ORGANIZATION, Organization.DEFAULT, "admin", idp.getId())).thenReturn(Maybe.empty());
        when(userService.create(any(User.class))).thenReturn(Single.just(adminUser));
        doNothing().when(membershipHelper).setOrganizationPrimaryOwnerRole(argThat(user -> user.getId().equals(adminUser.getId())));

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
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
        adminUser.setLoginsCount(10L);
        adminUser.setRoles(Arrays.asList("role-id"));

        Organization defaultOrganization = new Organization();
        defaultOrganization.setId(Organization.DEFAULT);
        defaultOrganization.setIdentities(Arrays.asList("inlineIdpId"));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(defaultOrganization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.just(idp));
        when(userService.findByUsernameAndSource(ReferenceType.ORGANIZATION, Organization.DEFAULT, "admin", idp.getId())).thenReturn(Maybe.just(adminUser)); // Admin already exists.
        when(organizationService.createDefault()).thenReturn(Maybe.empty());

        assertTrue(cut.upgrade());
        verify(membershipHelper, times(1)).setPlatformAdminRole();
    }

    @Test
    public void shouldCreateDefaultOrganization_technicalError() {

        when(organizationService.createDefault()).thenReturn(Maybe.error(TechnicalException::new));

        assertFalse(cut.upgrade());
    }

    @Test
    public void shouldCreateSystemRoles_setOwnerRoleToExistingUsers() {

        int totalUsers = 22;

        User user = new User();
        user.setId("user-1");

        Role adminRole = new Role();
        adminRole.setId("role-1");

        Organization organization = new Organization();
        organization.setId("orga-id");

        List<User> users = Stream.iterate(0, i -> i++).limit(10).map(i -> user)
                .toList();

        when(organizationService.createDefault()).thenReturn(Maybe.just(organization));
        when(organizationService.update(any(), any(), any())).thenReturn(Single.just(organization));
        when(domainService.findById("admin")).thenReturn(Maybe.just(new Domain()));
        when(domainService.delete(any(), eq("admin"), any())).thenReturn(Completable.complete());

        when(roleService.findDefaultRole(Organization.DEFAULT, DefaultRole.ORGANIZATION_OWNER, ReferenceType.ORGANIZATION))
                .thenReturn(Maybe.just(adminRole)); // Role has been created.

        when(userService.findAll(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), eq(0), anyInt()))
                .thenReturn(Single.just(new Page<>(users, 0, totalUsers)));

        when(userService.findAll(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), eq(1), anyInt()))
                .thenReturn(Single.just(new Page<>(users, 1, totalUsers)));

        when(userService.findAll(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), eq(2), anyInt()))
                .thenReturn(Single.just(new Page<>(Arrays.asList(user, user), 2, totalUsers)));

        doNothing().when(membershipHelper).setOrganizationRole(eq(user), eq(adminRole));

        when(organizationService.findById(Organization.DEFAULT)).thenReturn(Single.just(organization));
        when(identityProviderService.findAll(ReferenceType.ORGANIZATION, Organization.DEFAULT)).thenReturn(Flowable.empty());

        cut.upgrade();

        verify(membershipHelper, times(totalUsers)).setOrganizationRole(eq(user), eq(adminRole));
        verify(membershipHelper, times(1)).setPlatformAdminRole();
    }
}
