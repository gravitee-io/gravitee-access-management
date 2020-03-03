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
import io.gravitee.am.model.membership.ReferenceType;
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

    @InjectMocks
    private MembershipService membershipService = new MembershipServiceImpl();

    @Mock
    private UserService userService;

    @Mock
    private DomainService domainService;

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
        Membership membership = Mockito.mock(Membership.class);
        when(membership.getReferenceId()).thenReturn("reference-id");
        when(membership.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(membership.getMemberId()).thenReturn("user-id");
        when(membership.getMemberType()).thenReturn(MemberType.USER);
        when(membership.getRole()).thenReturn("role-id");

        User user = Mockito.mock(User.class);
        when(user.getReferenceId()).thenReturn("master-domain");

        Role role = Mockito.mock(Role.class);
        when(role.getId()).thenReturn("role-id");
        when(role.getReferenceId()).thenReturn("master-domain");
        when(role.getScope()).thenReturn(RoleScope.DOMAIN.getId());

        Domain domain = Mockito.mock(Domain.class);
        when(domain.isMaster()).thenReturn(true);

        when(userService.findById(membership.getMemberId())).thenReturn(Maybe.just(user));
        when(domainService.findById(user.getReferenceId())).thenReturn(Maybe.just(domain));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());
        when(membershipRepository.create(any())).thenReturn(Single.just(new Membership()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = membershipService.addOrUpdate(membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldCreate_groupMembership() {
        Membership membership = Mockito.mock(Membership.class);
        when(membership.getReferenceId()).thenReturn("reference-id");
        when(membership.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(membership.getMemberId()).thenReturn("group-id");
        when(membership.getMemberType()).thenReturn(MemberType.GROUP);
        when(membership.getRole()).thenReturn("role-id");

        Group group = Mockito.mock(Group.class);
        when(group.getDomain()).thenReturn("master-domain");

        Role role = Mockito.mock(Role.class);
        when(role.getId()).thenReturn("role-id");
        when(role.getReferenceId()).thenReturn("master-domain");
        when(role.getScope()).thenReturn(RoleScope.DOMAIN.getId());

        Domain domain = Mockito.mock(Domain.class);
        when(domain.isMaster()).thenReturn(true);

        when(groupService.findById(membership.getMemberId())).thenReturn(Maybe.just(group));
        when(domainService.findById(group.getDomain())).thenReturn(Maybe.just(domain));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());
        when(membershipRepository.create(any())).thenReturn(Single.just(new Membership()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = membershipService.addOrUpdate(membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldNotCreate_memberNotFound() {
        Membership membership = Mockito.mock(Membership.class);
        when(membership.getReferenceId()).thenReturn("reference-id");
        when(membership.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(membership.getMemberId()).thenReturn("user-id");
        when(membership.getMemberType()).thenReturn(MemberType.USER);
        when(membership.getRole()).thenReturn("role-id");

        Role role = Mockito.mock(Role.class);
        when(role.getId()).thenReturn("role-id");

        when(userService.findById(membership.getMemberId())).thenReturn(Maybe.empty());
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreate_groupNotFound() {
        Membership membership = Mockito.mock(Membership.class);
        when(membership.getReferenceId()).thenReturn("reference-id");
        when(membership.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(membership.getMemberId()).thenReturn("group-id");
        when(membership.getMemberType()).thenReturn(MemberType.GROUP);
        when(membership.getRole()).thenReturn("role-id");

        Role role = Mockito.mock(Role.class);
        when(role.getId()).thenReturn("role-id");

        when(groupService.findById(membership.getMemberId())).thenReturn(Maybe.empty());
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(GroupNotFoundException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreate_roleNotFound() {
        Membership membership = Mockito.mock(Membership.class);
        when(membership.getReferenceId()).thenReturn("reference-id");
        when(membership.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(membership.getMemberId()).thenReturn("user-id");
        when(membership.getMemberType()).thenReturn(MemberType.USER);
        when(membership.getRole()).thenReturn("role-id");

        User user = Mockito.mock(User.class);
        when(user.getReferenceId()).thenReturn("master-domain");

        Role role = Mockito.mock(Role.class);
        when(role.getId()).thenReturn("role-id");

        Domain domain = Mockito.mock(Domain.class);
        when(domain.isMaster()).thenReturn(true);

        when(userService.findById(membership.getMemberId())).thenReturn(Maybe.just(user));
        when(domainService.findById(user.getReferenceId())).thenReturn(Maybe.just(domain));
        when(roleService.findById(role.getId())).thenReturn(Maybe.empty());
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(RoleNotFoundException.class);

        verify(membershipRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreate_invalidRole() {
        Membership membership = Mockito.mock(Membership.class);
        when(membership.getReferenceId()).thenReturn("reference-id");
        when(membership.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(membership.getMemberId()).thenReturn("user-id");
        when(membership.getMemberType()).thenReturn(MemberType.USER);
        when(membership.getRole()).thenReturn("role-id");

        User user = Mockito.mock(User.class);
        when(user.getReferenceId()).thenReturn("master-domain");

        Role role = Mockito.mock(Role.class);
        when(role.getId()).thenReturn("role-id");
        when(role.getReferenceId()).thenReturn("master-domain");
        when(role.getScope()).thenReturn(RoleScope.APPLICATION.getId());

        Domain domain = Mockito.mock(Domain.class);
        when(domain.isMaster()).thenReturn(true);

        when(userService.findById(membership.getMemberId())).thenReturn(Maybe.just(user));
        when(domainService.findById(user.getReferenceId())).thenReturn(Maybe.just(domain));
        when(roleService.findById(role.getId())).thenReturn(Maybe.just(role));
        when(membershipRepository.findByReferenceAndMember(membership.getReferenceId(), membership.getMemberId())).thenReturn(Maybe.empty());

        TestObserver testObserver = membershipService.addOrUpdate(membership).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRoleException.class);

        verify(membershipRepository, never()).create(any());
    }
}
