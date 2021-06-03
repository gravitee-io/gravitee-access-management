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
package io.gravitee.am.management.service.impl.upgrades.helpers;

import io.gravitee.am.model.*;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.RoleService;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MembershipHelperTest {

    @Mock
    private MembershipService membershipService;

    @Mock
    private RoleService roleService;

    private MembershipHelper cut;

    @Before
    public void before() {

        cut = new MembershipHelper(membershipService, roleService);
    }

    @Test
    public void shouldSetOrganizationPrimaryOwnerRole() {

        User user = new User();
        user.setId("user-id");

        final Role primaryOwnerRole = new Role();
        primaryOwnerRole.setId("role-id");

        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), any(MembershipCriteria.class))).thenReturn(Flowable.empty()); // user has no role yet.
        when(roleService.findSystemRole(SystemRole.ORGANIZATION_PRIMARY_OWNER, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(primaryOwnerRole));
        when(membershipService.addOrUpdate(eq(Organization.DEFAULT), any(Membership.class))).thenReturn(Single.just(new Membership()));

        cut.setOrganizationPrimaryOwnerRole(user);
    }

    @Test
    public void shouldNotSetOrganizationPrimaryOwnerRole_alreadyHasARole() {

        User user = new User();
        user.setId("user-id");

        final Role adminRole = new Role();
        adminRole.setId("role-id");

        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), any(MembershipCriteria.class))).thenReturn(Flowable.just(new Membership()));
        when(roleService.findSystemRole(SystemRole.ORGANIZATION_PRIMARY_OWNER, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(adminRole));

        cut.setOrganizationPrimaryOwnerRole(user);
    }

    @Test
    public void shouldSetPlatformAdminToOrganizationPrimaryOwner() {

        final Role organizationPrimaryOwner = new Role();
        organizationPrimaryOwner.setId("organization-primary-owner");

        final Membership membership = new Membership();
        final String userId = "user-id";

        membership.setMemberId(userId);
        membership.setMemberType(MemberType.USER);

        when(roleService.findSystemRole(SystemRole.ORGANIZATION_PRIMARY_OWNER, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(organizationPrimaryOwner));
        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), any(MembershipCriteria.class))).thenReturn(Flowable.just(membership));
        when(membershipService.setPlatformAdmin(userId)).thenReturn(Single.just(new Membership()));

        cut.setPlatformAdminRole();

        verify(membershipService).setPlatformAdmin(userId);
    }

    @Test
    public void shouldNotSetPlatformAdminNoOrganizationPrimaryOwner() {

        final Role organizationPrimaryOwner = new Role();
        organizationPrimaryOwner.setId("organization-primary-owner");

        final Membership membership = new Membership();
        final String userId = "user-id";

        membership.setMemberId(userId);
        membership.setMemberType(MemberType.USER);

        when(roleService.findSystemRole(SystemRole.ORGANIZATION_PRIMARY_OWNER, ReferenceType.ORGANIZATION)).thenReturn(Maybe.just(organizationPrimaryOwner));
        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(Organization.DEFAULT), any(MembershipCriteria.class))).thenReturn(Flowable.empty());

        cut.setPlatformAdminRole();

        verify(membershipService, times(0)).setPlatformAdmin(anyString());
    }
}