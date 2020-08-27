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
package io.gravitee.am.service;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.*;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.DefaultRole;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.MembershipRepository;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.impl.MembershipServiceImpl;
import io.gravitee.am.service.model.NewMembership;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MembershipServiceTest {

    public static final String ORGANIZATION_ID = "orga#1";
    public static final String DOMAIN_ID = "master-domain";

    @InjectMocks
    private MembershipService membershipService = new MembershipServiceImpl();

    @Mock
    private UserService userService;

    @Mock
    private RoleService roleService;

    @Mock
    private EventService eventService;

    @Mock
    private GroupService groupService;

    @Mock
    private AuditService auditService;

    @Mock
    private MembershipRepository membershipRepository;

    @Test
    public void shouldCreate_userMembership() {

        Membership membership = new Membership();
        membership.setReferenceId(DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRoleId("role-id");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId(DOMAIN_ID);
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setAssignableType(ReferenceType.DOMAIN);

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(user));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceType(), membership.getReferenceId(), membership.getMemberType(), membership.getMemberId())).thenReturn(Maybe.empty());
        when(membershipRepository.create(any())).thenReturn(Single.just(new Membership()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldCreate_primaryOwner() {

        Membership membership = new Membership();
        membership.setReferenceId(DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRoleId("role-primary-owner");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role role = new Role();
        role.setId("role-primary-owner");
        role.setName(SystemRole.DOMAIN_PRIMARY_OWNER.name());
        role.setReferenceId(Platform.DEFAULT);
        role.setSystem(true);
        role.setReferenceType(ReferenceType.PLATFORM);
        role.setAssignableType(ReferenceType.DOMAIN);

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(user));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceType(), membership.getReferenceId(), membership.getMemberType(), membership.getMemberId())).thenReturn(Maybe.empty());
        when(membershipRepository.create(any())).thenReturn(Single.just(new Membership()));
        when(membershipRepository.findByCriteria(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), argThat(criteria -> criteria.getRoleId().isPresent()))).thenReturn(Flowable.empty());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldCreate_groupMembership() {

        Membership membership = new Membership();
        membership.setReferenceId(DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("group-id");
        membership.setMemberType(MemberType.GROUP);
        membership.setRoleId("role-id");

        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId(DOMAIN_ID);
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setAssignableType(ReferenceType.DOMAIN);

        Group group = new Group();
        group.setReferenceId(DOMAIN_ID);
        group.setReferenceType(ReferenceType.DOMAIN);

        when(groupService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(group));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceType(), membership.getReferenceId(), membership.getMemberType(), membership.getMemberId())).thenReturn(Maybe.empty());
        when(membershipRepository.create(any())).thenReturn(Single.just(new Membership()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldNotCreate_memberNotFound() {

        Membership membership = new Membership();
        membership.setReferenceId(DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRoleId("role-id");

        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId(DOMAIN_ID);
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setAssignableType(ReferenceType.DOMAIN);

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.error(new UserNotFoundException("user-id")));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceType(), membership.getReferenceId(), membership.getMemberType(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreate_primaryOwnerAlreadyAssigned() {

        Membership membership = new Membership();
        membership.setReferenceId(DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRoleId("role-primary-owner");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role role = new Role();
        role.setId("role-primary-owner");
        role.setName(SystemRole.DOMAIN_PRIMARY_OWNER.name());
        role.setReferenceId(Platform.DEFAULT);
        role.setSystem(true);
        role.setReferenceType(ReferenceType.PLATFORM);
        role.setAssignableType(ReferenceType.DOMAIN);

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(user));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByCriteria(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), argThat(criteria -> criteria.getRoleId().isPresent()))).thenReturn(Flowable.just(new Membership()));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceType(), membership.getReferenceId(), membership.getMemberType(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertError(SinglePrimaryOwnerException.class);
    }

    @Test
    public void shouldNotCreate_primaryOwnerCantBeAssignedToGroup() {

        Membership membership = new Membership();
        membership.setReferenceId(DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("group-id");
        membership.setMemberType(MemberType.GROUP);
        membership.setRoleId("role-primary-owner");

        Group group = new Group();
        group.setReferenceId(ORGANIZATION_ID);
        group.setReferenceType(ReferenceType.ORGANIZATION);

        Role role = new Role();
        role.setId("role-primary-owner");
        role.setName(SystemRole.DOMAIN_PRIMARY_OWNER.name());
        role.setReferenceId(Platform.DEFAULT);
        role.setSystem(true);
        role.setReferenceType(ReferenceType.PLATFORM);
        role.setAssignableType(ReferenceType.DOMAIN);

        when(groupService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(group));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceType(), membership.getReferenceId(), membership.getMemberType(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertError(InvalidRoleException.class);
    }

    @Test
    public void shouldNotCreate_groupNotFound() {

        Membership membership = new Membership();
        membership.setReferenceId(DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("group-id");
        membership.setMemberType(MemberType.GROUP);
        membership.setRoleId("role-id");

        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId(DOMAIN_ID);
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setAssignableType(ReferenceType.DOMAIN);

        when(groupService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.error(new GroupNotFoundException("group-id")));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceType(), membership.getReferenceId(), membership.getMemberType(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(GroupNotFoundException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreate_roleNotFound() {

        Membership membership = new Membership();
        membership.setReferenceId(DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRoleId("role-id");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId("master-domain");
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setAssignableType(ReferenceType.DOMAIN);

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(user));
        when(roleService.findById(role.getId())).thenReturn(Maybe.empty());
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceType(), membership.getReferenceId(), membership.getMemberType(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(RoleNotFoundException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreate_invalidRoleScope() {

        Membership membership = new Membership();
        membership.setReferenceId(DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRoleId("role-id");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId(DOMAIN_ID);
        role.setReferenceType(ReferenceType.DOMAIN);
        // Scope application can't be use for domain.
        role.setAssignableType(ReferenceType.APPLICATION);

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(user));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceType(), membership.getReferenceId(), membership.getMemberType(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRoleException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreate_invalidDomain() {

        Membership membership = new Membership();
        membership.setReferenceId(DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRoleId("role-id");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role role = new Role();
        role.setId("role-id");
        // Role is not on the same domain.
        role.setReferenceId("domain#2");
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setAssignableType(ReferenceType.DOMAIN);

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(user));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceType(), membership.getReferenceId(), membership.getMemberType(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRoleException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreate_invalidOrganization() {

        Membership membership = new Membership();
        membership.setReferenceId("master-domain");
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRoleId("role-id");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        // Role is not a system and belongs to another organization.
        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId("orga#2");
        role.setReferenceType(ReferenceType.ORGANIZATION);
        role.setAssignableType(ReferenceType.DOMAIN);

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(user));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceType(), membership.getReferenceId(), membership.getMemberType(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRoleException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldAddEnvironmentUserRole() {

        NewMembership membership = new NewMembership();
        membership.setMemberType(MemberType.USER);
        membership.setMemberId("user#1");

        DefaultUser principal = new DefaultUser("username");
        principal.setId("user#1");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role environmentUserRole = new Role();
        environmentUserRole.setId("role#1");

        when(membershipRepository.findByCriteria(eq(ReferenceType.ENVIRONMENT), eq("env#1"), any(MembershipCriteria.class))).thenReturn(Flowable.empty());
        when(roleService.findDefaultRole("orga#1", DefaultRole.ENVIRONMENT_USER, ReferenceType.ENVIRONMENT)).thenReturn(Maybe.just(environmentUserRole));
        when(membershipRepository.create(any())).thenReturn(Single.just(new Membership()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<Void> completable = membershipService.addEnvironmentUserRoleIfNecessary("orga#1", "env#1", membership, principal).test();

        completable.awaitTerminalEvent();
        completable.assertNoErrors();
        completable.assertComplete();
    }

    @Test
    public void shouldNotAddEnvironmentUserRole_userAlreadyHasMembership() {

        NewMembership membership = new NewMembership();
        membership.setMemberType(MemberType.USER);
        membership.setMemberId("user#1");

        DefaultUser principal = new DefaultUser("username");
        principal.setId("user#1");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role environmentUserRole = new Role();
        environmentUserRole.setId("role#1");

        when(membershipRepository.findByCriteria(eq(ReferenceType.ENVIRONMENT), eq("env#1"), any(MembershipCriteria.class))).thenReturn(Flowable.just(new Membership()));

        TestObserver<Void> completable = membershipService.addEnvironmentUserRoleIfNecessary("orga#1", "env#1", membership, principal).test();

        completable.awaitTerminalEvent();
        completable.assertNoErrors();
        completable.assertComplete();

        verify(membershipRepository, times(0)).create(any());
        verifyZeroInteractions(auditService);
    }

    @Test
    public void shouldAddDomainUserRole() {

        NewMembership membership = new NewMembership();
        membership.setMemberType(MemberType.USER);
        membership.setMemberId("user#1");

        DefaultUser principal = new DefaultUser("username");
        principal.setId("user#1");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role environmentUserRole = new Role();
        environmentUserRole.setId("role#1");

        when(membershipRepository.findByCriteria(eq(ReferenceType.DOMAIN), eq("domain#1"), any(MembershipCriteria.class))).thenReturn(Flowable.empty());
        when(roleService.findDefaultRole("orga#1", DefaultRole.DOMAIN_USER, ReferenceType.DOMAIN)).thenReturn(Maybe.just(environmentUserRole));
        when(membershipRepository.create(any())).thenReturn(Single.just(new Membership()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(membershipRepository.findByCriteria(eq(ReferenceType.ENVIRONMENT), eq("env#1"), any(MembershipCriteria.class))).thenReturn(Flowable.empty());
        when(roleService.findDefaultRole("orga#1", DefaultRole.ENVIRONMENT_USER, ReferenceType.ENVIRONMENT)).thenReturn(Maybe.just(environmentUserRole));

        TestObserver<Void> completable = membershipService.addDomainUserRoleIfNecessary("orga#1", "env#1", "domain#1", membership, principal).test();

        completable.awaitTerminalEvent();
        completable.assertNoErrors();
        completable.assertComplete();
    }

    @Test
    public void shouldNotAddDomainUserRole_userAlreadyHasMembership() {

        NewMembership membership = new NewMembership();
        membership.setMemberType(MemberType.USER);
        membership.setMemberId("user#1");

        DefaultUser principal = new DefaultUser("username");
        principal.setId("user#1");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role environmentUserRole = new Role();
        environmentUserRole.setId("role#1");

        when(membershipRepository.findByCriteria(eq(ReferenceType.ENVIRONMENT), eq("env#1"), any(MembershipCriteria.class))).thenReturn(Flowable.just(new Membership()));
        when(membershipRepository.findByCriteria(eq(ReferenceType.DOMAIN), eq("domain#1"), any(MembershipCriteria.class))).thenReturn(Flowable.just(new Membership()));

        TestObserver<Void> completable = membershipService.addDomainUserRoleIfNecessary("orga#1", "env#1", "domain#1", membership, principal).test();

        completable.awaitTerminalEvent();
        completable.assertNoErrors();
        completable.assertComplete();

        verify(membershipRepository, times(0)).create(any());
        verifyZeroInteractions(auditService);
    }
}
