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
package io.gravitee.am.management.handlers.management.api.provider;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.manager.group.GroupManager;
import io.gravitee.am.management.handlers.management.api.manager.membership.MembershipManager;
import io.gravitee.am.management.handlers.management.api.manager.role.RoleManager;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.membership.ReferenceType;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.model.permissions.RoleScope;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PermissionsFilterTest {

    @InjectMocks
    private PermissionsFilter permissionsFilter = new PermissionsFilter();

    @Mock
    private MembershipManager membershipManager;

    @Mock
    private GroupManager groupManager;

    @Mock
    private RoleManager roleManager;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private ContainerRequestContext containerRequestContext;

    @Test(expected = ForbiddenException.class)
    public void shouldThrowForbiddenExceptionWhenNoDomainPermissions() {
        final String referenceId = "reference-id";
        initMocks(ReferenceType.DOMAIN, referenceId);

        Permissions permissions = mock(Permissions.class);
        Permission perm = mock(Permission.class);
        when(perm.value()).thenReturn(RolePermission.DOMAIN_IDENTITY_PROVIDER);
        when(permissions.value()).thenReturn(new Permission[]{perm});

        permissionsFilter.filter(permissions, containerRequestContext);
    }

    @Test(expected = ForbiddenException.class)
    public void shouldThrowForbiddenException_wrongDomainPermissions() {
        final String referenceId = "reference-id";
        initMocks(ReferenceType.DOMAIN, referenceId);

        List<Membership> memberships = new ArrayList<>();
        Membership membership = new Membership();
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setReferenceId(referenceId);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setRole("role-id");
        memberships.add(membership);

        Role role = new Role();
        role.setId("role-id");
        role.setScope(RoleScope.DOMAIN.getId());
        role.setPermissions(Collections.singletonList(RolePermission.DOMAIN_ROLE.getPermission().getMask() + "_" + RolePermissionAction.READ.getMask()));

        when(roleManager.findByIdIn(Collections.singletonList("role-id"))).thenReturn(Collections.singleton(role));
        when(membershipManager.findByReference(referenceId, ReferenceType.DOMAIN)).thenReturn(memberships);

        Permissions permissions = mock(Permissions.class);
        Permission perm = mock(Permission.class);
        when(perm.value()).thenReturn(RolePermission.DOMAIN_IDENTITY_PROVIDER);
        when(perm.acls()).thenReturn(new RolePermissionAction[]{RolePermissionAction.UPDATE});
        when(permissions.value()).thenReturn(new Permission[]{perm});

        permissionsFilter.filter(permissions, containerRequestContext);
    }

    @Test
    public void shouldBeAuthorizedWhenDomainPermissions_adminUser() throws IOException {
        final String referenceId = "reference-id";
        initMocks(ReferenceType.DOMAIN, referenceId);

        when(roleManager.isAdminRoleGranted(any())).thenReturn(true);
        permissionsFilter.filter(containerRequestContext);
    }

    @Test
    public void shouldBeAuthorizedWhenDomainPermissions_userMember() {
        final String referenceId = "reference-id";
        initMocks(ReferenceType.DOMAIN, referenceId);

        List<Membership> memberships = new ArrayList<>();
        Membership membership = new Membership();
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setReferenceId(referenceId);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setRole("role-id");
        memberships.add(membership);

        Role role = new Role();
        role.setId("role-id");
        role.setScope(RoleScope.DOMAIN.getId());
        role.setPermissions(Collections.singletonList(RolePermission.DOMAIN_IDENTITY_PROVIDER.getPermission().getMask() + "_" + RolePermissionAction.UPDATE.getMask()));

        when(roleManager.findByIdIn(Collections.singletonList("role-id"))).thenReturn(Collections.singleton(role));
        when(membershipManager.findByReference(referenceId, ReferenceType.DOMAIN)).thenReturn(memberships);

        Permissions permissions = mock(Permissions.class);
        Permission perm = mock(Permission.class);
        when(perm.value()).thenReturn(RolePermission.DOMAIN_IDENTITY_PROVIDER);
        when(perm.acls()).thenReturn(new RolePermissionAction[]{RolePermissionAction.UPDATE});
        when(permissions.value()).thenReturn(new Permission[]{perm});

        permissionsFilter.filter(permissions, containerRequestContext);
    }

    @Test
    public void shouldBeAuthorizedWhenDomainPermissions_groupMember() {
        final String referenceId = "reference-id";
        initMocks(ReferenceType.DOMAIN, referenceId);

        List<Membership> memberships = new ArrayList<>();
        Membership membership = new Membership();
        membership.setMemberId("group-id");
        membership.setMemberType(MemberType.GROUP);
        membership.setReferenceId(referenceId);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setRole("role-id");
        memberships.add(membership);

        Role role = new Role();
        role.setId("role-id");
        role.setScope(RoleScope.DOMAIN.getId());
        role.setPermissions(Collections.singletonList(RolePermission.DOMAIN_IDENTITY_PROVIDER.getPermission().getMask() + "_" + RolePermissionAction.UPDATE.getMask()));

        Group group = new Group();
        group.setId("group-id");
        group.setMembers(Collections.singletonList("user-id"));

        when(groupManager.findByMember("user-id")).thenReturn(Collections.singletonList(group));
        when(roleManager.findByIdIn(Collections.singletonList("role-id"))).thenReturn(Collections.singleton(role));
        when(membershipManager.findByReference(referenceId, ReferenceType.DOMAIN)).thenReturn(memberships);

        Permissions permissions = mock(Permissions.class);
        Permission perm = mock(Permission.class);
        when(perm.value()).thenReturn(RolePermission.DOMAIN_IDENTITY_PROVIDER);
        when(perm.acls()).thenReturn(new RolePermissionAction[]{RolePermissionAction.UPDATE});
        when(permissions.value()).thenReturn(new Permission[]{perm});

        permissionsFilter.filter(permissions, containerRequestContext);
    }

    @Test(expected = ForbiddenException.class)
    public void shouldThrowForbiddenExceptionWhenNoApplicationPermissions() {
        final String referenceId = "reference-id";
        initMocks(ReferenceType.APPLICATION, referenceId);

        Permissions permissions = mock(Permissions.class);
        Permission perm = mock(Permission.class);
        when(perm.value()).thenReturn(RolePermission.APPLICATION_IDENTITY_PROVIDER);
        when(permissions.value()).thenReturn(new Permission[]{perm});

        permissionsFilter.filter(permissions, containerRequestContext);
    }

    @Test(expected = ForbiddenException.class)
    public void shouldThrowForbiddenException_wrongApplicationPermissions() {
        final String referenceId = "reference-id";
        initMocks(ReferenceType.APPLICATION, referenceId);

        List<Membership> memberships = new ArrayList<>();
        Membership membership = new Membership();
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setReferenceId(referenceId);
        membership.setReferenceType(ReferenceType.APPLICATION);
        membership.setRole("role-id");
        memberships.add(membership);

        Role role = new Role();
        role.setId("role-id");
        role.setScope(RoleScope.APPLICATION.getId());
        role.setPermissions(Collections.singletonList(RolePermission.APPLICATION_FORM.getPermission().getMask() + "_" + RolePermissionAction.READ.getMask()));

        when(roleManager.findByIdIn(Collections.singletonList("role-id"))).thenReturn(Collections.singleton(role));
        when(membershipManager.findByReference(referenceId, ReferenceType.APPLICATION)).thenReturn(memberships);

        Permissions permissions = mock(Permissions.class);
        Permission perm = mock(Permission.class);
        when(perm.value()).thenReturn(RolePermission.APPLICATION_IDENTITY_PROVIDER);
        when(perm.acls()).thenReturn(new RolePermissionAction[]{RolePermissionAction.UPDATE});
        when(permissions.value()).thenReturn(new Permission[]{perm});

        permissionsFilter.filter(permissions, containerRequestContext);
    }

    @Test
    public void shouldBeAuthorizedWhenApplicationPermissions_adminUser() throws IOException {
        final String referenceId = "reference-id";
        initMocks(ReferenceType.APPLICATION, referenceId);

        when(roleManager.isAdminRoleGranted(any())).thenReturn(true);
        permissionsFilter.filter(containerRequestContext);
    }

    @Test
    public void shouldBeAuthorizedWhenApplicationPermissions_userMember() {
        final String referenceId = "reference-id";
        initMocks(ReferenceType.APPLICATION, referenceId);

        List<Membership> memberships = new ArrayList<>();
        Membership membership = new Membership();
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setReferenceId(referenceId);
        membership.setReferenceType(ReferenceType.APPLICATION);
        membership.setRole("role-id");
        memberships.add(membership);

        Role role = new Role();
        role.setId("role-id");
        role.setScope(RoleScope.APPLICATION.getId());
        role.setPermissions(Collections.singletonList(RolePermission.APPLICATION_IDENTITY_PROVIDER.getPermission().getMask() + "_" + RolePermissionAction.UPDATE.getMask()));

        when(roleManager.findByIdIn(Collections.singletonList("role-id"))).thenReturn(Collections.singleton(role));
        when(membershipManager.findByReference(referenceId, ReferenceType.APPLICATION)).thenReturn(memberships);

        Permissions permissions = mock(Permissions.class);
        Permission perm = mock(Permission.class);
        when(perm.value()).thenReturn(RolePermission.APPLICATION_IDENTITY_PROVIDER);
        when(perm.acls()).thenReturn(new RolePermissionAction[]{RolePermissionAction.UPDATE});
        when(permissions.value()).thenReturn(new Permission[]{perm});

        permissionsFilter.filter(permissions, containerRequestContext);
    }

    @Test
    public void shouldBeAuthorizedWhenApplicationPermissions_groupMember() {
        final String referenceId = "reference-id";
        initMocks(ReferenceType.APPLICATION, referenceId);

        List<Membership> memberships = new ArrayList<>();
        Membership membership = new Membership();
        membership.setMemberId("group-id");
        membership.setMemberType(MemberType.GROUP);
        membership.setReferenceId(referenceId);
        membership.setReferenceType(ReferenceType.APPLICATION);
        membership.setRole("role-id");
        memberships.add(membership);

        Role role = new Role();
        role.setId("role-id");
        role.setScope(RoleScope.APPLICATION.getId());
        role.setPermissions(Collections.singletonList(RolePermission.APPLICATION_IDENTITY_PROVIDER.getPermission().getMask() + "_" + RolePermissionAction.UPDATE.getMask()));

        Group group = new Group();
        group.setId("group-id");
        group.setMembers(Collections.singletonList("user-id"));

        when(groupManager.findByMember("user-id")).thenReturn(Collections.singletonList(group));
        when(roleManager.findByIdIn(Collections.singletonList("role-id"))).thenReturn(Collections.singleton(role));
        when(membershipManager.findByReference(referenceId, ReferenceType.APPLICATION)).thenReturn(memberships);

        Permissions permissions = mock(Permissions.class);
        Permission perm = mock(Permission.class);
        when(perm.value()).thenReturn(RolePermission.APPLICATION_IDENTITY_PROVIDER);
        when(perm.acls()).thenReturn(new RolePermissionAction[]{RolePermissionAction.UPDATE});
        when(permissions.value()).thenReturn(new Permission[]{perm});

        permissionsFilter.filter(permissions, containerRequestContext);
    }

    @Test(expected = ForbiddenException.class)
    public void shouldThrowForbiddenExceptionWhenNoManagementPermissions() {
        initUser();

        Permissions permissions = mock(Permissions.class);
        Permission perm = mock(Permission.class);
        when(perm.value()).thenReturn(RolePermission.MANAGEMENT_AUDIT);
        when(permissions.value()).thenReturn(new Permission[]{perm});

        permissionsFilter.filter(permissions, containerRequestContext);
    }

    @Test(expected = ForbiddenException.class)
    public void shouldThrowForbiddenException_wrongManagementPermissions() {
        initUser();

        Role role = new Role();
        role.setId("role-id");
        role.setScope(RoleScope.MANAGEMENT.getId());
        role.setPermissions(Collections.singletonList(RolePermission.MANAGEMENT_USER.getPermission().getMask() + "_" + RolePermissionAction.UPDATE.getMask()));

        Permissions permissions = mock(Permissions.class);
        Permission perm = mock(Permission.class);
        when(perm.value()).thenReturn(RolePermission.MANAGEMENT_AUDIT);
        when(perm.acls()).thenReturn(new RolePermissionAction[]{RolePermissionAction.UPDATE});
        when(permissions.value()).thenReturn(new Permission[]{perm});

        when(roleManager.findByIdIn(Collections.singletonList("role-id"))).thenReturn(Collections.singleton(role));
        permissionsFilter.filter(permissions, containerRequestContext);
    }

    @Test
    public void shouldBeAuthorizedWhenManagementPermissions() {
        initUser();

        Role role = new Role();
        role.setId("role-id");
        role.setScope(RoleScope.MANAGEMENT.getId());
        role.setPermissions(Collections.singletonList(RolePermission.MANAGEMENT_AUDIT.getPermission().getMask() + "_" + RolePermissionAction.UPDATE.getMask()));

        Permissions permissions = mock(Permissions.class);
        Permission perm = mock(Permission.class);
        when(perm.value()).thenReturn(RolePermission.MANAGEMENT_AUDIT);
        when(perm.acls()).thenReturn(new RolePermissionAction[]{RolePermissionAction.UPDATE});
        when(permissions.value()).thenReturn(new Permission[]{perm});

        when(roleManager.findByIdIn(Collections.singletonList("role-id"))).thenReturn(Collections.singleton(role));
        permissionsFilter.filter(permissions, containerRequestContext);
    }

    private void initMocks(ReferenceType referenceType, String referenceId) {
        initUser();

        UriInfo uriInfo = mock(UriInfo.class);
        MultivaluedHashMap<String, String> map = new MultivaluedHashMap<>();
        map.put(referenceType.name().toLowerCase(), Collections.singletonList(referenceId));
        when(uriInfo.getPathParameters()).thenReturn(map);
        when(containerRequestContext.getUriInfo()).thenReturn(uriInfo);
    }

    private void initUser() {
        User endUser = new DefaultUser("username");
        ((DefaultUser) endUser).setId("user-id");
        ((DefaultUser) endUser).setRoles(Collections.singletonList("role-id"));
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(endUser, null);
        when(securityContext.getUserPrincipal()).thenReturn(usernamePasswordAuthenticationToken);
    }
}
