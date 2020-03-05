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

import io.gravitee.am.model.*;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.RoleScope;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.MembershipRepository;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.impl.MembershipServiceImpl;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
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
    public static final String MASTER_DOMAIN_ID = "master-domain";

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
    public void shouldFindAll() {
        when(membershipRepository.findAll()).thenReturn(Flowable.just(new Membership()));
        TestSubscriber<Membership> testSubscriber = membershipService.findAll().test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldFindAll_technicalException() {
        when(membershipRepository.findAll()).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testObserver = new TestSubscriber<>();
        membershipService.findAll().subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate_userMembership() {

        Membership membership = new Membership();
        membership.setReferenceId(MASTER_DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRole("role-id");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId(MASTER_DOMAIN_ID);
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setScope(RoleScope.DOMAIN.getId());

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(user));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());
        when(membershipRepository.create(any())).thenReturn(Single.just(new Membership()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldCreate_groupMembership() {

        Membership membership = new Membership();
        membership.setReferenceId(MASTER_DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("group-id");
        membership.setMemberType(MemberType.GROUP);
        membership.setRole("role-id");

        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId(MASTER_DOMAIN_ID);
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setScope(RoleScope.DOMAIN.getId());

        Group group = new Group();
        group.setReferenceId(MASTER_DOMAIN_ID);
        group.setReferenceType(ReferenceType.DOMAIN);

        when(groupService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(group));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());
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
        membership.setReferenceId(MASTER_DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRole("role-id");

        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId(MASTER_DOMAIN_ID);
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setScope(RoleScope.DOMAIN.getId());

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.error(new UserNotFoundException("user-id")));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreate_groupNotFound() {

        Membership membership = new Membership();
        membership.setReferenceId(MASTER_DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("group-id");
        membership.setMemberType(MemberType.GROUP);
        membership.setRole("role-id");

        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId(MASTER_DOMAIN_ID);
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setScope(RoleScope.DOMAIN.getId());

        when(groupService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.error(new GroupNotFoundException("group-id")));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(GroupNotFoundException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreate_roleNotFound() {

        Membership membership = new Membership();
        membership.setReferenceId(MASTER_DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRole("role-id");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId("master-domain");
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setScope(RoleScope.DOMAIN.getId());

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(user));
        when(roleService.findById(role.getId())).thenReturn(Maybe.empty());
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(RoleNotFoundException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreate_invalidRoleScope() {

        Membership membership = new Membership();
        membership.setReferenceId(MASTER_DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRole("role-id");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId(MASTER_DOMAIN_ID);
        role.setReferenceType(ReferenceType.DOMAIN);
        // Scope application can't be use for domain.
        role.setScope(RoleScope.APPLICATION.getId());

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(user));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRoleException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreate_invalidDomain() {

        Membership membership = new Membership();
        membership.setReferenceId(MASTER_DOMAIN_ID);
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setMemberId("user-id");
        membership.setMemberType(MemberType.USER);
        membership.setRole("role-id");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        Role role = new Role();
        role.setId("role-id");
        // Role is not on the same domain.
        role.setReferenceId("domain#2");
        role.setReferenceType(ReferenceType.DOMAIN);
        role.setScope(RoleScope.DOMAIN.getId());

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(user));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());

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
        membership.setRole("role-id");

        User user = new User();
        user.setReferenceId(ORGANIZATION_ID);
        user.setReferenceType(ReferenceType.ORGANIZATION);

        // Role is not a system and belongs to another organization.
        Role role = new Role();
        role.setId("role-id");
        role.setReferenceId("orga#2");
        role.setReferenceType(ReferenceType.ORGANIZATION);
        role.setScope(RoleScope.DOMAIN.getId());

        when(userService.findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberId())).thenReturn(Single.just(user));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(ORGANIZATION_ID, membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRoleException.class);

        verify(membershipRepository, never()).create(any());
    }
}
