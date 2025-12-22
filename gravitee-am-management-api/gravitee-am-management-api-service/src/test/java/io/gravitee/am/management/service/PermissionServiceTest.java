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

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.*;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.management.service.permissions.Permissions.and;
import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;
import static io.gravitee.am.model.Acl.CREATE;
import static io.gravitee.am.model.Acl.READ;
import static io.gravitee.am.model.permissions.Permission.APPLICATION;
import static io.gravitee.am.model.permissions.Permission.DOMAIN;
import static io.gravitee.am.model.permissions.Permission.ORGANIZATION;
import static io.gravitee.am.model.permissions.Permission.PROTECTED_RESOURCE;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PermissionServiceTest {

    private static final String ORGANIZATION_ID = "orga#1";
    public static final String USER_ID = "user#1";
    public static final String ROLE_ID = "role#1";
    public static final String GROUP_ID = "group#1";
    public static final String DOMAIN_ID = "domain#1";
    public static final String ENVIRONMENT_ID = "environment#1";
    private static final String ROLE_ID2 = "role#2";
    private static final String ROLE_ID3 = "role#3";
    public static final String APPLICATION_ID = "application#1";
    public static final String PROTECTED_RESOURCE_ID = "protected-resource#1";

    @Mock
    private MembershipService membershipService;

    @Mock
    private OrganizationGroupService orgGroupService;

    @Mock
    private RoleService roleService;

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private DomainService domainService;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private ProtectedResourceService protectedResourceService;

    private PermissionService cut;

    @Before
    public void before() {
        cut = new PermissionService(membershipService, orgGroupService, roleService, environmentService, domainService, applicationService, protectedResourceService);
    }

    @Test
    public void hasPermission_fromUserMemberships() {

        DefaultUser user = new DefaultUser("user");
        user.setId(USER_ID);

        Membership membership = new Membership();
        membership.setMemberType(MemberType.USER);
        membership.setMemberId(USER_ID);
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setRoleId(ROLE_ID);

        Role role = new Role();
        role.setId(ROLE_ID);
        role.setAssignableType(ReferenceType.ORGANIZATION);
        role.setPermissionAcls(Permission.of(ORGANIZATION, READ));

        when(orgGroupService.findByMember(user.getId())).thenReturn(Flowable.empty());
        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), argThat(criteria -> criteria.getUserId().get().equals(user.getId())
                && !criteria.getGroupIds().isPresent()
                && criteria.isLogicalOR()))).thenReturn(Flowable.just(membership));
        when(roleService.findByIdIn(Arrays.asList(membership.getRoleId()))).thenReturn(Single.just(Collections.singleton(role)));

        TestObserver<Boolean> obs = cut.hasPermission(user, of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, Permission.ORGANIZATION, READ))
                .test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);
    }

    @Test
    public void hasPermission_fromGroupMemberships() {

        DefaultUser user = new DefaultUser("user");
        user.setId(USER_ID);

        Membership membership = new Membership();
        membership.setMemberType(MemberType.GROUP);
        membership.setMemberId(GROUP_ID);
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setRoleId(ROLE_ID);

        Role role = new Role();
        role.setId(ROLE_ID);
        role.setAssignableType(ReferenceType.ORGANIZATION);
        role.setPermissionAcls(Permission.of(ORGANIZATION, READ));

        Group group = new Group();
        group.setId(GROUP_ID);
        group.setReferenceType(ReferenceType.ORGANIZATION);
        group.setReferenceId(ORGANIZATION_ID);
        group.setMembers(Arrays.asList(user.getId()));

        when(orgGroupService.findByMember(user.getId())).thenReturn(Flowable.just(group));
        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), argThat(criteria -> criteria.getUserId().get().equals(user.getId())
                && criteria.getGroupIds().get().equals(Arrays.asList(group.getId()))
                && criteria.isLogicalOR()))).thenReturn(Flowable.just(membership));
        when(roleService.findByIdIn(Arrays.asList(membership.getRoleId()))).thenReturn(Single.just(Collections.singleton(role)));

        TestObserver<Boolean> obs = cut.hasPermission(user, of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, Permission.ORGANIZATION, READ))
                .test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);
    }

    @Test
    public void getReferenceIds_forUserMembership() {

        DefaultUser user = new DefaultUser("user");
        user.setId(USER_ID);

        Membership membership = new Membership();
        membership.setMemberType(MemberType.USER);
        membership.setMemberId(USER_ID);
        membership.setReferenceType(ReferenceType.APPLICATION);
        membership.setReferenceId("appId");
        membership.setRoleId(ROLE_ID);

        Role role = new Role();
        role.setId(ROLE_ID);
        role.setAssignableType(ReferenceType.APPLICATION);
        role.setPermissionAcls(Permission.of(APPLICATION, READ));

        when(orgGroupService.findByMember(user.getId())).thenReturn(Flowable.empty());
        when(membershipService.findByCriteria(eq(ReferenceType.APPLICATION), argThat(criteria -> criteria.getUserId().get().equals(user.getId())
                && !criteria.getGroupIds().isPresent()
                && criteria.isLogicalOR()))).thenReturn(Flowable.just(membership));
        when(roleService.findByIdIn(Arrays.asList(membership.getRoleId()))).thenReturn(Single.just(Collections.singleton(role)));

        TestSubscriber<String> obs = cut.getReferenceIdsWithPermission(user, ReferenceType.APPLICATION, APPLICATION, Set.of(READ))
                .test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue("appId");
    }

    @Test
    public void hasPermissionResource_noMembership() {

        DefaultUser user = new DefaultUser("user");
        user.setId(USER_ID);

        when(orgGroupService.findByMember(user.getId())).thenReturn(Flowable.empty());
        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), any(MembershipCriteria.class))).thenReturn(Flowable.empty());

        TestObserver<Boolean> obs = cut.hasPermission(user, of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, Permission.ORGANIZATION, READ))
                .test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(false);
    }

    @Test
    public void hasPermission_hasAllPermissions() {

        DefaultUser user = new DefaultUser("user");
        user.setId(USER_ID);

        Membership organizationMembership = new Membership();
        organizationMembership.setMemberType(MemberType.USER);
        organizationMembership.setMemberId(USER_ID);
        organizationMembership.setReferenceType(ReferenceType.ORGANIZATION);
        organizationMembership.setReferenceId(ORGANIZATION_ID);
        organizationMembership.setRoleId(ROLE_ID);

        Membership environmentMembership = new Membership();
        environmentMembership.setMemberType(MemberType.USER);
        environmentMembership.setMemberId(USER_ID);
        environmentMembership.setReferenceType(ReferenceType.ENVIRONMENT);
        environmentMembership.setReferenceId(ENVIRONMENT_ID);
        environmentMembership.setRoleId(ROLE_ID2);

        Membership domainMembership = new Membership();
        domainMembership.setMemberType(MemberType.USER);
        domainMembership.setMemberId(USER_ID);
        domainMembership.setReferenceType(ReferenceType.DOMAIN);
        domainMembership.setReferenceId(DOMAIN_ID);
        domainMembership.setRoleId(ROLE_ID3);

        Role organizationRole = new Role();
        organizationRole.setId(ROLE_ID);
        organizationRole.setAssignableType(ReferenceType.ORGANIZATION);
        organizationRole.setPermissionAcls(Permission.of(DOMAIN, READ));

        Role environmentRole = new Role();
        environmentRole.setId(ROLE_ID2);
        environmentRole.setAssignableType(ReferenceType.ENVIRONMENT);
        environmentRole.setPermissionAcls(Permission.of(DOMAIN, READ));

        Role domainRole = new Role();
        domainRole.setId(ROLE_ID3);
        domainRole.setAssignableType(ReferenceType.DOMAIN);
        domainRole.setPermissionAcls(Permission.of(DOMAIN, READ));

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Environment environment = new Environment();
        environment.setOrganizationId(ORGANIZATION_ID);

        when(domainService.findById(eq(DOMAIN_ID))).thenReturn(Maybe.just(domain));
        when(environmentService.findById(eq(ENVIRONMENT_ID), eq(ORGANIZATION_ID))).thenReturn(Single.just(environment));
        when(orgGroupService.findByMember(user.getId())).thenReturn(Flowable.empty());
        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), any(MembershipCriteria.class))).thenReturn(Flowable.just(organizationMembership));
        when(membershipService.findByCriteria(eq(ReferenceType.ENVIRONMENT), eq(ENVIRONMENT_ID), any(MembershipCriteria.class))).thenReturn(Flowable.just(environmentMembership));
        when(membershipService.findByCriteria(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), any(MembershipCriteria.class))).thenReturn(Flowable.just(domainMembership));
        when(roleService.findByIdIn(Arrays.asList(organizationMembership.getRoleId(), environmentMembership.getRoleId(), domainMembership.getRoleId()))).thenReturn(Single.just(new HashSet<>(Arrays.asList(organizationRole, environmentRole, domainRole))));

        TestObserver<Boolean> obs = cut.hasPermission(user,
                and(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, DOMAIN, READ),
                        of(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, DOMAIN, READ),
                        of(ReferenceType.DOMAIN, DOMAIN_ID, Permission.DOMAIN, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);
    }

    @Test
    public void hasPermission_hasNotAllPermissions() {

        DefaultUser user = new DefaultUser("user");
        user.setId(USER_ID);

        Membership organizationMembership = new Membership();
        organizationMembership.setMemberType(MemberType.USER);
        organizationMembership.setMemberId(USER_ID);
        organizationMembership.setReferenceType(ReferenceType.ORGANIZATION);
        organizationMembership.setReferenceId(ORGANIZATION_ID);
        organizationMembership.setRoleId(ROLE_ID);

        Membership domainMembership = new Membership();
        domainMembership.setMemberType(MemberType.USER);
        domainMembership.setMemberId(USER_ID);
        domainMembership.setReferenceType(ReferenceType.DOMAIN);
        domainMembership.setReferenceId(DOMAIN_ID);
        domainMembership.setRoleId(ROLE_ID2);

        Role organizationRole = new Role();
        organizationRole.setId(ROLE_ID);
        organizationRole.setAssignableType(ReferenceType.ORGANIZATION);
        organizationRole.setPermissionAcls(Permission.of(ORGANIZATION, READ));

        Role domainRole = new Role();
        domainRole.setId(ROLE_ID2);
        domainRole.setAssignableType(ReferenceType.DOMAIN);
        domainRole.setPermissionAcls(Permission.of(DOMAIN, CREATE));

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Environment environment = new Environment();
        environment.setOrganizationId(ORGANIZATION_ID);

        when(domainService.findById(eq(DOMAIN_ID))).thenReturn(Maybe.just(domain));
        when(environmentService.findById(eq(ENVIRONMENT_ID), eq(ORGANIZATION_ID))).thenReturn(Single.just(environment));
        when(orgGroupService.findByMember(user.getId())).thenReturn(Flowable.empty());
        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), any(MembershipCriteria.class))).thenReturn(Flowable.just(organizationMembership));
        when(membershipService.findByCriteria(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), any(MembershipCriteria.class))).thenReturn(Flowable.just(domainMembership));
        when(roleService.findByIdIn(Arrays.asList(organizationMembership.getRoleId(), domainMembership.getRoleId()))).thenReturn(Single.just(new HashSet<>(Arrays.asList(organizationRole, domainRole))));

        TestObserver<Boolean> obs = cut.hasPermission(user,
                and(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, Permission.ORGANIZATION, READ),
                        of(ReferenceType.DOMAIN, DOMAIN_ID, Permission.DOMAIN, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(false);
    }

    @Test
    public void hasPermission_hasOneOfPermissions() {

        DefaultUser user = new DefaultUser("user");
        user.setId(USER_ID);

        Membership organizationMembership = new Membership();
        organizationMembership.setMemberType(MemberType.USER);
        organizationMembership.setMemberId(USER_ID);
        organizationMembership.setReferenceType(ReferenceType.ORGANIZATION);
        organizationMembership.setReferenceId(ORGANIZATION_ID);
        organizationMembership.setRoleId(ROLE_ID);

        Membership domainMembership = new Membership();
        domainMembership.setMemberType(MemberType.USER);
        domainMembership.setMemberId(USER_ID);
        domainMembership.setReferenceType(ReferenceType.DOMAIN);
        domainMembership.setReferenceId(DOMAIN_ID);
        domainMembership.setRoleId(ROLE_ID2);

        Role organizationRole = new Role();
        organizationRole.setId(ROLE_ID);
        organizationRole.setAssignableType(ReferenceType.ORGANIZATION);
        organizationRole.setPermissionAcls(Permission.of(ORGANIZATION, READ));

        Role domainRole = new Role();
        domainRole.setId(ROLE_ID2);
        domainRole.setAssignableType(ReferenceType.DOMAIN);
        domainRole.setPermissionAcls(Permission.of(DOMAIN, CREATE));

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Environment environment = new Environment();
        environment.setOrganizationId(ORGANIZATION_ID);

        when(domainService.findById(eq(DOMAIN_ID))).thenReturn(Maybe.just(domain));
        when(environmentService.findById(eq(ENVIRONMENT_ID), eq(ORGANIZATION_ID))).thenReturn(Single.just(environment));
        when(orgGroupService.findByMember(user.getId())).thenReturn(Flowable.empty());
        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), any(MembershipCriteria.class))).thenReturn(Flowable.just(organizationMembership));
        when(membershipService.findByCriteria(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), any(MembershipCriteria.class))).thenReturn(Flowable.just(domainMembership));
        when(roleService.findByIdIn(Arrays.asList(organizationMembership.getRoleId(), domainMembership.getRoleId()))).thenReturn(Single.just(new HashSet<>(Arrays.asList(organizationRole, domainRole))));

        TestObserver<Boolean> obs = cut.hasPermission(user,
                or(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, Permission.ORGANIZATION, READ),
                        of(ReferenceType.DOMAIN, DOMAIN_ID, Permission.DOMAIN, READ))).test(); // OR instead of AND

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);
    }

    @Test
    public void hasPermission_hasPermissionsOnAnotherReference() {

        DefaultUser user = new DefaultUser("user");
        user.setId(USER_ID);

        Membership membership = new Membership();
        membership.setMemberType(MemberType.USER);
        membership.setMemberId(USER_ID);
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setRoleId(ROLE_ID);

        Role role = new Role();
        role.setId(ROLE_ID);
        role.setAssignableType(ReferenceType.ORGANIZATION);
        role.setPermissionAcls(Permission.of(DOMAIN, READ, CREATE)); // The permission create is set on organization but expected on a domain.

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Environment environment = new Environment();
        environment.setOrganizationId(ORGANIZATION_ID);

        when(domainService.findById(eq(DOMAIN_ID))).thenReturn(Maybe.just(domain));
        when(environmentService.findById(eq(ENVIRONMENT_ID), eq(ORGANIZATION_ID))).thenReturn(Single.just(environment));
        when(orgGroupService.findByMember(user.getId())).thenReturn(Flowable.empty());
        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), any(MembershipCriteria.class))).thenReturn(Flowable.just(membership));
        when(membershipService.findByCriteria(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), any(MembershipCriteria.class))).thenReturn(Flowable.empty());
        when(roleService.findByIdIn(Arrays.asList(membership.getRoleId()))).thenReturn(Single.just(new HashSet<>(Arrays.asList(role))));

        TestObserver<Boolean> obs = cut.hasPermission(user,
                and(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, DOMAIN, READ),
                        of(ReferenceType.DOMAIN, DOMAIN_ID, Permission.DOMAIN, CREATE))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(false);
    }

    @Test
    public void hasPermission_hasPermissionsButNotAssignableToType() {

        DefaultUser user = new DefaultUser("user");
        user.setId(USER_ID);

        Membership membership = new Membership();
        membership.setMemberType(MemberType.USER);
        membership.setMemberId(USER_ID);
        membership.setReferenceType(ReferenceType.APPLICATION);
        membership.setReferenceId(APPLICATION_ID);
        membership.setRoleId(ROLE_ID);

        Role role = new Role();
        role.setId(ROLE_ID);
        role.setAssignableType(ReferenceType.ORGANIZATION);// The role is assignable to organization only by affected to an application.
        role.setPermissionAcls(Permission.of(APPLICATION, READ));

        when(orgGroupService.findByMember(user.getId())).thenReturn(Flowable.empty());
        when(membershipService.findByCriteria(eq(ReferenceType.APPLICATION), eq(APPLICATION_ID), any(MembershipCriteria.class))).thenReturn(Flowable.just(membership));
        when(roleService.findByIdIn(Arrays.asList(membership.getRoleId()))).thenReturn(Single.just(new HashSet<>(Arrays.asList(role))));

        TestObserver<Boolean> obs = cut.hasPermission(user, of(ReferenceType.APPLICATION, APPLICATION_ID, APPLICATION, READ))
                .test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(false);
    }

    @Test
    public void hasPermission_checkNotRelevant() {

        try {
            cut.hasPermission(null, of(ReferenceType.APPLICATION, APPLICATION_ID, ORGANIZATION, READ));
        } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains("not relevant"));
        }
    }

    @Test
    public void hasPermission_aclsFromDifferentGroupAndUser() {

        DefaultUser user = new DefaultUser("user");
        user.setId(USER_ID);

        Membership organizationMembership = new Membership();
        organizationMembership.setMemberType(MemberType.USER);
        organizationMembership.setMemberId(USER_ID);
        organizationMembership.setReferenceType(ReferenceType.ORGANIZATION);
        organizationMembership.setReferenceId(ORGANIZATION_ID);
        organizationMembership.setRoleId(ROLE_ID);

        Membership groupMembership = new Membership();
        groupMembership.setMemberType(MemberType.GROUP);
        groupMembership.setMemberId(GROUP_ID);
        groupMembership.setReferenceType(ReferenceType.ORGANIZATION);
        groupMembership.setReferenceId(ORGANIZATION_ID);
        groupMembership.setRoleId(ROLE_ID2);

        Role organizationRole = new Role();
        organizationRole.setId(ROLE_ID);
        organizationRole.setAssignableType(ReferenceType.ORGANIZATION);
        organizationRole.setPermissionAcls(Permission.of(ORGANIZATION, READ)); // READ permission come from role associated to user.

        Role groupRole = new Role();
        groupRole.setId(ROLE_ID2);
        groupRole.setAssignableType(ReferenceType.ORGANIZATION);
        groupRole.setPermissionAcls(Permission.of(ORGANIZATION, CREATE)); // CREATE permission come from role associated to group of the user.

        Group group = new Group();
        group.setId(GROUP_ID);
        group.setReferenceType(ReferenceType.ORGANIZATION);
        group.setReferenceId(ORGANIZATION_ID);
        group.setMembers(Arrays.asList(user.getId()));

        when(orgGroupService.findByMember(user.getId())).thenReturn(Flowable.just(group));
        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), any(MembershipCriteria.class))).thenReturn(Flowable.just(organizationMembership, groupMembership));
        when(roleService.findByIdIn(Arrays.asList(organizationMembership.getRoleId(), groupMembership.getRoleId()))).thenReturn(Single.just(new HashSet<>(Arrays.asList(organizationRole, groupRole))));

        TestObserver<Boolean> obs = cut.hasPermission(user, of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, Permission.ORGANIZATION, READ, CREATE)).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);
    }

    @Test
    public void findAllPermission() {

        // Note: findAllPermission is based on same business logic than hasPermissions.
        DefaultUser user = new DefaultUser("user");
        user.setId(USER_ID);

        Membership organizationMembership = new Membership();
        organizationMembership.setMemberType(MemberType.USER);
        organizationMembership.setMemberId(USER_ID);
        organizationMembership.setReferenceType(ReferenceType.ORGANIZATION);
        organizationMembership.setReferenceId(ORGANIZATION_ID);
        organizationMembership.setRoleId(ROLE_ID);

        Membership groupMembership = new Membership();
        groupMembership.setMemberType(MemberType.GROUP);
        groupMembership.setMemberId(GROUP_ID);
        groupMembership.setReferenceType(ReferenceType.ORGANIZATION);
        groupMembership.setReferenceId(ORGANIZATION_ID);
        groupMembership.setRoleId(ROLE_ID2);

        Role organizationRole = new Role();
        organizationRole.setId(ROLE_ID);
        organizationRole.setAssignableType(ReferenceType.ORGANIZATION);
        organizationRole.setPermissionAcls(Permission.of(ORGANIZATION, READ)); // READ permission come from role associated to user.

        Role groupRole = new Role();
        groupRole.setId(ROLE_ID2);
        groupRole.setAssignableType(ReferenceType.ORGANIZATION);
        groupRole.setPermissionAcls(Permission.of(ORGANIZATION, CREATE)); // CREATE permission come from role associated to group of the user.

        Group group = new Group();
        group.setId(GROUP_ID);
        group.setReferenceType(ReferenceType.ORGANIZATION);
        group.setReferenceId(ORGANIZATION_ID);
        group.setMembers(Arrays.asList(user.getId()));

        when(orgGroupService.findByMember(user.getId())).thenReturn(Flowable.just(group));
        when(membershipService.findByCriteria(eq(ReferenceType.ORGANIZATION), eq(ORGANIZATION_ID), any(MembershipCriteria.class))).thenReturn(Flowable.just(organizationMembership, groupMembership));
        when(roleService.findByIdIn(Arrays.asList(organizationMembership.getRoleId(), groupMembership.getRoleId()))).thenReturn(Single.just(new HashSet<>(Arrays.asList(organizationRole, groupRole))));

        TestObserver<Map<Permission, Set<Acl>>> obs = cut.findAllPermissions(user, ReferenceType.ORGANIZATION, ORGANIZATION_ID).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(permissions -> permissions.get(ORGANIZATION).containsAll(new HashSet<>(Arrays.asList(READ, CREATE))));
    }


    @Test
    public void haveConsistentIds() {

        Application application = new Application();
        application.setDomain(DOMAIN_ID);

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Environment environment = new Environment();
        environment.setOrganizationId(ORGANIZATION_ID);

        when(applicationService.findById(eq(APPLICATION_ID))).thenReturn(Maybe.just(application));
        when(domainService.findById(eq(DOMAIN_ID))).thenReturn(Maybe.just(domain));
        when(environmentService.findById(eq(ENVIRONMENT_ID), eq(ORGANIZATION_ID))).thenReturn(Single.just(environment));

        TestObserver<Boolean> obs = cut.haveConsistentReferenceIds(or(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, APPLICATION, READ),
                of(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, APPLICATION, READ),
                of(ReferenceType.DOMAIN, DOMAIN_ID, APPLICATION, READ),
                of(ReferenceType.APPLICATION, APPLICATION_ID, APPLICATION, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);
    }

    @Test
    public void haveConsistentIds_applicationIdNotConsistent() {

        Application application = new Application();
        application.setDomain("OTHER_DOMAIN");

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Environment environment = new Environment();
        environment.setOrganizationId(ORGANIZATION_ID);

        when(applicationService.findById(eq(APPLICATION_ID))).thenReturn(Maybe.just(application));

        TestObserver<Boolean> obs = cut.haveConsistentReferenceIds(or(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, APPLICATION, READ),
                of(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, APPLICATION, READ),
                of(ReferenceType.DOMAIN, DOMAIN_ID, APPLICATION, READ),
                of(ReferenceType.APPLICATION, APPLICATION_ID, APPLICATION, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(false);
    }


    @Test
    public void haveConsistentIds_domainIdNotConsistent() {

        Application application = new Application();
        application.setDomain(DOMAIN_ID);

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId("OTHER_ENVIRONMENT");

        Environment environment = new Environment();
        environment.setOrganizationId(ORGANIZATION_ID);

        when(applicationService.findById(eq(APPLICATION_ID))).thenReturn(Maybe.just(application));

        TestObserver<Boolean> obs = cut.haveConsistentReferenceIds(or(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, APPLICATION, READ),
                of(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, APPLICATION, READ),
                of(ReferenceType.DOMAIN, DOMAIN_ID, APPLICATION, READ),
                of(ReferenceType.APPLICATION, APPLICATION_ID, APPLICATION, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(false);
    }

    @Test
    public void haveConsistentIds_environmentIdNotConsistent() {

        Application application = new Application();
        application.setDomain(DOMAIN_ID);

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        when(applicationService.findById(eq(APPLICATION_ID))).thenReturn(Maybe.just(application));

        TestObserver<Boolean> obs = cut.haveConsistentReferenceIds(or(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, APPLICATION, READ),
                of(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, APPLICATION, READ),
                of(ReferenceType.DOMAIN, DOMAIN_ID, APPLICATION, READ),
                of(ReferenceType.APPLICATION, APPLICATION_ID, APPLICATION, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(false);
    }

    @Test
    public void haveConsistentIds_onlyOneReferenceType() {

        TestObserver<Boolean> obs = cut.haveConsistentReferenceIds(of(ReferenceType.APPLICATION, APPLICATION_ID, APPLICATION, READ)).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);

        verify(applicationService, times(0)).findById(any());
        verify(domainService, times(0)).findById(any());
        verify(environmentService, times(0)).findById(any());
    }

    @Test
    public void haveConsistentIds_cached() {

        Application application = new Application();
        application.setDomain(DOMAIN_ID);

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Environment environment = new Environment();
        environment.setOrganizationId(ORGANIZATION_ID);

        when(applicationService.findById(eq(APPLICATION_ID))).thenReturn(Maybe.just(application));
        when(domainService.findById(eq(DOMAIN_ID))).thenReturn(Maybe.just(domain));
        when(environmentService.findById(eq(ENVIRONMENT_ID), eq(ORGANIZATION_ID))).thenReturn(Single.just(environment));

        TestObserver<Boolean> obs = cut.haveConsistentReferenceIds(or(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, APPLICATION, READ),
                of(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, APPLICATION, READ),
                of(ReferenceType.DOMAIN, DOMAIN_ID, APPLICATION, READ),
                of(ReferenceType.APPLICATION, APPLICATION_ID, APPLICATION, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);

        verify(applicationService, times(1)).findById(eq(APPLICATION_ID));
        verify(domainService, times(1)).findById(eq(DOMAIN_ID));
        verify(environmentService, times(1)).findById(eq(ENVIRONMENT_ID), eq(ORGANIZATION_ID));

        // Second call should hit the cache.
        obs = cut.haveConsistentReferenceIds(or(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, APPLICATION, READ),
                of(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, APPLICATION, READ),
                of(ReferenceType.DOMAIN, DOMAIN_ID, APPLICATION, READ),
                of(ReferenceType.APPLICATION, APPLICATION_ID, APPLICATION, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);

        verifyNoMoreInteractions(applicationService, domainService, environmentService);
    }

    @Test
    public void haveConsistentIds_protectedResource() {

        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId(PROTECTED_RESOURCE_ID);
        protectedResource.setDomainId(DOMAIN_ID);

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Environment environment = new Environment();
        environment.setOrganizationId(ORGANIZATION_ID);

        when(protectedResourceService.findById(eq(PROTECTED_RESOURCE_ID))).thenReturn(Maybe.just(protectedResource));
        when(domainService.findById(eq(DOMAIN_ID))).thenReturn(Maybe.just(domain));
        when(environmentService.findById(eq(ENVIRONMENT_ID), eq(ORGANIZATION_ID))).thenReturn(Single.just(environment));

        TestObserver<Boolean> obs = cut.haveConsistentReferenceIds(or(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, PROTECTED_RESOURCE, READ),
                of(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, PROTECTED_RESOURCE, READ),
                of(ReferenceType.DOMAIN, DOMAIN_ID, PROTECTED_RESOURCE, READ),
                of(ReferenceType.PROTECTED_RESOURCE, PROTECTED_RESOURCE_ID, PROTECTED_RESOURCE, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);
    }

    @Test
    public void haveConsistentIds_protectedResourceIdNotConsistent() {

        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId(PROTECTED_RESOURCE_ID);
        protectedResource.setDomainId("OTHER_DOMAIN");

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Environment environment = new Environment();
        environment.setOrganizationId(ORGANIZATION_ID);

        when(protectedResourceService.findById(eq(PROTECTED_RESOURCE_ID))).thenReturn(Maybe.just(protectedResource));

        TestObserver<Boolean> obs = cut.haveConsistentReferenceIds(or(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, PROTECTED_RESOURCE, READ),
                of(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, PROTECTED_RESOURCE, READ),
                of(ReferenceType.DOMAIN, DOMAIN_ID, PROTECTED_RESOURCE, READ),
                of(ReferenceType.PROTECTED_RESOURCE, PROTECTED_RESOURCE_ID, PROTECTED_RESOURCE, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(false);
    }

    @Test
    public void haveConsistentIds_onlyOneReferenceType_protectedResource() {

        TestObserver<Boolean> obs = cut.haveConsistentReferenceIds(of(ReferenceType.PROTECTED_RESOURCE, PROTECTED_RESOURCE_ID, PROTECTED_RESOURCE, READ)).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);

        verify(protectedResourceService, times(0)).findById(any());
        verify(domainService, times(0)).findById(any());
        verify(environmentService, times(0)).findById(any());
    }

    @Test
    public void haveConsistentIds_protectedResource_cached() {

        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId(PROTECTED_RESOURCE_ID);
        protectedResource.setDomainId(DOMAIN_ID);

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Environment environment = new Environment();
        environment.setOrganizationId(ORGANIZATION_ID);

        when(protectedResourceService.findById(eq(PROTECTED_RESOURCE_ID))).thenReturn(Maybe.just(protectedResource));
        when(domainService.findById(eq(DOMAIN_ID))).thenReturn(Maybe.just(domain));
        when(environmentService.findById(eq(ENVIRONMENT_ID), eq(ORGANIZATION_ID))).thenReturn(Single.just(environment));

        TestObserver<Boolean> obs = cut.haveConsistentReferenceIds(or(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, PROTECTED_RESOURCE, READ),
                of(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, PROTECTED_RESOURCE, READ),
                of(ReferenceType.DOMAIN, DOMAIN_ID, PROTECTED_RESOURCE, READ),
                of(ReferenceType.PROTECTED_RESOURCE, PROTECTED_RESOURCE_ID, PROTECTED_RESOURCE, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);

        verify(protectedResourceService, times(1)).findById(eq(PROTECTED_RESOURCE_ID));
        verify(domainService, times(1)).findById(eq(DOMAIN_ID));
        verify(environmentService, times(1)).findById(eq(ENVIRONMENT_ID), eq(ORGANIZATION_ID));

        // Second call should hit the cache.
        obs = cut.haveConsistentReferenceIds(or(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, PROTECTED_RESOURCE, READ),
                of(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, PROTECTED_RESOURCE, READ),
                of(ReferenceType.DOMAIN, DOMAIN_ID, PROTECTED_RESOURCE, READ),
                of(ReferenceType.PROTECTED_RESOURCE, PROTECTED_RESOURCE_ID, PROTECTED_RESOURCE, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);

        verifyNoMoreInteractions(protectedResourceService, domainService, environmentService);
    }

    @Test
    public void haveConsistentIds_applicationAndProtectedResource() {

        Application application = new Application();
        application.setDomain(DOMAIN_ID);

        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId(PROTECTED_RESOURCE_ID);
        protectedResource.setDomainId(DOMAIN_ID);

        Domain domain = new Domain();
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);

        Environment environment = new Environment();
        environment.setOrganizationId(ORGANIZATION_ID);

        when(applicationService.findById(eq(APPLICATION_ID))).thenReturn(Maybe.just(application));
        when(protectedResourceService.findById(eq(PROTECTED_RESOURCE_ID))).thenReturn(Maybe.just(protectedResource));
        when(domainService.findById(eq(DOMAIN_ID))).thenReturn(Maybe.just(domain));
        when(environmentService.findById(eq(ENVIRONMENT_ID), eq(ORGANIZATION_ID))).thenReturn(Single.just(environment));

        TestObserver<Boolean> obs = cut.haveConsistentReferenceIds(or(of(ReferenceType.ORGANIZATION, ORGANIZATION_ID, APPLICATION, READ),
                of(ReferenceType.ENVIRONMENT, ENVIRONMENT_ID, APPLICATION, READ),
                of(ReferenceType.DOMAIN, DOMAIN_ID, APPLICATION, READ),
                of(ReferenceType.APPLICATION, APPLICATION_ID, APPLICATION, READ),
                of(ReferenceType.PROTECTED_RESOURCE, PROTECTED_RESOURCE_ID, PROTECTED_RESOURCE, READ))).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(true);
    }

}
