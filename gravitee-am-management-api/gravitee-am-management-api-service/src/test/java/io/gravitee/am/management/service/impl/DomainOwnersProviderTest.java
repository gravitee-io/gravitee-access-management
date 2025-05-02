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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.OrganizationGroupService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.RoleService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.model.permissions.DefaultRole.DOMAIN_OWNER;
import static io.reactivex.rxjava3.core.Maybe.just;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class DomainOwnersProviderTest {

    @Mock
    private MembershipService membershipService;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private RoleService roleService;

    @Mock
    private OrganizationUserService userService;

    @Mock
    private OrganizationGroupService organizationGroupService;

    @InjectMocks
    private DomainOwnersProvider domainOwnersProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void test(){
        Domain domain = new Domain();
        domain.setId("domainId");
        domain.setReferenceId("envId");

        Environment environment = new Environment();
        environment.setOrganizationId("organizationId");

        Role systemRole = new Role();
        systemRole.setId("systemRoleId");

        Role defaultRole = new Role();
        defaultRole.setId("defaultRoleId");

        Membership userMembership = new Membership();
        userMembership.setMemberType(MemberType.USER);
        userMembership.setMemberId("userId");

        User user = new User();
        user.setId("userId");

        Membership groupMembership = new Membership();
        groupMembership.setMemberType(MemberType.GROUP);
        groupMembership.setMemberId("groupId");

        when(environmentService.findById("envId")).thenReturn(Single.just(environment));
        when(roleService.findSystemRole(eq(SystemRole.DOMAIN_PRIMARY_OWNER), eq(DOMAIN))).thenReturn(just(systemRole));
        when(roleService.findDefaultRole(eq("organizationId"), eq(DOMAIN_OWNER), eq(DOMAIN))).thenReturn(just(defaultRole));

        when(membershipService.findByCriteria(eq(DOMAIN), eq(domain.getId()), argThat(c -> c.getRoleId().get().equals("systemRoleId"))))
                .thenReturn(Flowable.just(userMembership));

        when(membershipService.findByCriteria(eq(DOMAIN), eq(domain.getId()), argThat(c -> c.getRoleId().get().equals("defaultRoleId"))))
                .thenReturn(Flowable.just(groupMembership));

        when(userService.findById(eq(Reference.organization("organizationId")), eq(UserId.internal("userId"))))
                .thenReturn(Single.just(user));

        when(organizationGroupService.findMembers(eq("organizationId"), eq("groupId"), anyInt(), anyInt()))
                .thenReturn(Single.just(new Page<>(List.of(), 0, 0)));

        domainOwnersProvider.retrieveDomainOwners(domain)
                .test()
                .assertComplete()
                .assertValue(result -> result.getId().equals("userId"));
    }

}