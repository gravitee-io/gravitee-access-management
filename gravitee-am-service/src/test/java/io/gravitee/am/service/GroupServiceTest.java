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

import io.gravitee.am.model.Group;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.GroupRepository;
import io.gravitee.am.service.exception.GroupAlreadyExistsException;
import io.gravitee.am.service.exception.GroupNotFoundException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.GroupServiceImpl;
import io.gravitee.am.service.model.NewGroup;
import io.gravitee.am.service.model.UpdateGroup;
import io.reactivex.Completable;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class GroupServiceTest {

    @InjectMocks
    private GroupService groupService = new GroupServiceImpl();

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserService userService;

    @Mock
    private OrganizationUserService organizationUserService;

    @Mock
    private AuditService auditService;

    @Mock
    private RoleService roleService;

    @Mock
    private EventService eventService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(groupRepository.findById("my-group")).thenReturn(Maybe.just(new Group()));
        TestObserver testObserver = groupService.findById("my-group").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_groupNotFound() {
        when(groupRepository.findById("my-group")).thenReturn(Maybe.empty());
        TestObserver testObserver = groupService.findById("my-group").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(groupRepository.findById("my-group")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        groupService.findById("my-group").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }


    @Test
    public void shouldFindByDomain() {
        when(groupRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(new Group()));
        TestObserver<List<Group>> testObserver = groupService.findByDomain(DOMAIN).toList().test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(groups -> groups.size() == 1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(groupRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = groupService.findByDomain(DOMAIN).test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainPagination() {
        Page pagedGroups = new Page(Collections.singleton(new Group()), 1, 1);
        when(groupRepository.findAll(ReferenceType.DOMAIN, DOMAIN, 1, 1)).thenReturn(Single.just(pagedGroups));
        TestObserver<Page<Group>> testObserver = groupService.findByDomain(DOMAIN, 1, 1).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.getData().size() == 1);
    }

    @Test
    public void shouldFindByDomainPagination_technicalException() {
        when(groupRepository.findAll(ReferenceType.DOMAIN, DOMAIN, 1, 1)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        groupService.findByDomain(DOMAIN, 1, 1).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewGroup newGroup = Mockito.mock(NewGroup.class);
        Group group = new Group();
        group.setReferenceType(ReferenceType.DOMAIN);
        group.setReferenceId(DOMAIN);

        when(newGroup.getName()).thenReturn("name");
        when(groupRepository.findByName(ReferenceType.DOMAIN, DOMAIN, newGroup.getName())).thenReturn(Maybe.empty());
        when(groupRepository.create(any(Group.class))).thenReturn(Single.just(group));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = groupService.create(DOMAIN, newGroup).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(groupRepository, times(1)).create(any(Group.class));
    }

    @Test
    public void shouldCreate_technicalException() {
        NewGroup newGroup = Mockito.mock(NewGroup.class);
        when(newGroup.getName()).thenReturn("name");
        when(groupRepository.findByName(ReferenceType.DOMAIN, DOMAIN, newGroup.getName())).thenReturn(Maybe.empty());
        when(groupRepository.create(any(Group.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        groupService.create(DOMAIN, newGroup).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate_alreadyExists() {
        NewGroup newGroup = Mockito.mock(NewGroup.class);
        when(newGroup.getName()).thenReturn("names");
        when(groupRepository.findByName(ReferenceType.DOMAIN, DOMAIN, newGroup.getName())).thenReturn(Maybe.just(new Group()));

        TestObserver testObserver = new TestObserver();
        groupService.create(DOMAIN, newGroup).subscribe(testObserver);

        testObserver.assertError(GroupAlreadyExistsException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate() {
        UpdateGroup updateGroup = Mockito.mock(UpdateGroup.class);
        Group group = new Group();
        group.setReferenceType(ReferenceType.DOMAIN);
        group.setReferenceId(DOMAIN);

        when(updateGroup.getName()).thenReturn("name");
        when(groupRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-group")).thenReturn(Maybe.just(group));
        when(groupRepository.findByName(ReferenceType.DOMAIN, DOMAIN, updateGroup.getName())).thenReturn(Maybe.empty());
        when(groupRepository.update(any(Group.class))).thenReturn(Single.just(group));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = groupService.update(DOMAIN, "my-group", updateGroup).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(groupRepository, times(1)).findById(ReferenceType.DOMAIN, DOMAIN, "my-group");
        verify(groupRepository, times(1)).update(any(Group.class));
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateGroup updateGroup = Mockito.mock(UpdateGroup.class);
        when(groupRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-group")).thenReturn(Maybe.just(new Group()));

        TestObserver testObserver = new TestObserver();
        groupService.update(DOMAIN, "my-group", updateGroup).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate_groupNotFound() {
        UpdateGroup updateGroup = Mockito.mock(UpdateGroup.class);
        when(groupRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-group")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        groupService.update(DOMAIN, "my-group", updateGroup).subscribe(testObserver);

        testObserver.assertError(GroupNotFoundException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        when(groupRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-group")).thenReturn(Maybe.just(new Group()));
        when(groupRepository.delete("my-group")).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = groupService.delete(ReferenceType.DOMAIN, DOMAIN, "my-group").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(groupRepository, times(1)).delete("my-group");
    }

    @Test
    public void shouldDelete_technicalException() {
        when(groupRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-group")).thenReturn(Maybe.just(new Group()));
        when(groupRepository.delete("my-group")).thenReturn(Completable.error(TechnicalException::new));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = new TestObserver();
        groupService.delete(ReferenceType.DOMAIN, DOMAIN, "my-group").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_groupNotFound() {
        when(groupRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-group")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        groupService.delete(ReferenceType.DOMAIN, DOMAIN, "my-group").subscribe(testObserver);

        testObserver.assertError(GroupNotFoundException.class);
        testObserver.assertNotComplete();

        verify(groupRepository, never()).delete("my-group");
    }

    @Test
    public void shouldAssignRoles() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        Group group = mock(Group.class);
        when(group.getId()).thenReturn("group-id");

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(groupRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("group-id"))).thenReturn(Maybe.just(group));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(roles));
        when(groupRepository.update(any())).thenReturn(Single.just(new Group()));

        TestObserver testObserver = groupService.assignRoles(ReferenceType.DOMAIN, DOMAIN, group.getId(), rolesIds).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(groupRepository, times(1)).update(any());
    }

    @Test
    public void shouldAssignRoles_roleNotFound() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        Group group = mock(Group.class);
        when(group.getId()).thenReturn("group-id");

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(groupRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("group-id"))).thenReturn(Maybe.just(group));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(Collections.emptySet()));

        TestObserver testObserver = groupService.assignRoles(ReferenceType.DOMAIN, DOMAIN, group.getId(), rolesIds).test();
        testObserver.assertNotComplete();
        testObserver.assertError(RoleNotFoundException.class);
        verify(groupRepository, never()).update(any());
    }

    @Test
    public void shouldRevokeRole() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        Group group = mock(Group.class);
        when(group.getId()).thenReturn("group-id");

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(groupRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("group-id"))).thenReturn(Maybe.just(group));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(roles));
        when(groupRepository.update(any())).thenReturn(Single.just(new Group()));

        TestObserver testObserver = groupService.revokeRoles(ReferenceType.DOMAIN, DOMAIN, group.getId(), rolesIds).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(groupRepository, times(1)).update(any());
    }

    @Test
    public void shouldRevokeRoles_roleNotFound() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        Group group = mock(Group.class);
        when(group.getId()).thenReturn("group-id");

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(groupRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("group-id"))).thenReturn(Maybe.just(group));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(Collections.emptySet()));

        TestObserver testObserver = groupService.revokeRoles(ReferenceType.DOMAIN, DOMAIN, group.getId(), rolesIds).test();
        testObserver.assertNotComplete();
        testObserver.assertError(RoleNotFoundException.class);
        verify(groupRepository, never()).update(any());
    }

    @Test
    public void shouldFindMembersFromDomainUsers() {
        Group group = mock(Group.class);
        when(group.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(group.getMembers()).thenReturn(Arrays.asList("userid"));

        when(groupRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("group-id"))).thenReturn(Maybe.just(group));
        when(userService.findByIdIn(any())).thenReturn(Flowable.just(new User()));

        final TestObserver<Page<User>> observer = groupService.findMembers(ReferenceType.DOMAIN, DOMAIN, "group-id", 0, 0).test();
        observer.awaitTerminalEvent();

        verify(userService).findByIdIn(any());
        verify(organizationUserService, never()).findByIdIn(any());
    }

    @Test
    public void shouldFindMembersFromDomainUsers_Paginate() {
        final var userIds = IntStream.range(0, 52).mapToObj(i -> "user-" + i).collect(Collectors.toList());
        var group = mock(Group.class);
        when(group.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(group.getMembers()).thenReturn(userIds);

        when(groupRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("group-id"))).thenReturn(Maybe.just(group));
        when(userService.findByIdIn(any())).thenReturn(Flowable.fromIterable(userIds.stream().map(userId -> {
            final var user = new User();
            user.setId(userId);
            return user;
        }).collect(Collectors.toList())));

        var observer = groupService.findMembers(ReferenceType.DOMAIN, DOMAIN, "group-id", 0, 25).test();
        observer.awaitTerminalEvent();
        observer.assertValue(page -> page.getTotalCount() == userIds.size());
        observer.assertValue(page -> page.getCurrentPage() == 0);

        observer = groupService.findMembers(ReferenceType.DOMAIN, DOMAIN, "group-id", 1, 25).test();
        observer.awaitTerminalEvent();
        observer.assertValue(page -> page.getTotalCount() == userIds.size());
        observer.assertValue(page -> page.getCurrentPage() == 1);

        observer = groupService.findMembers(ReferenceType.DOMAIN, DOMAIN, "group-id", 2, 25).test();
        observer.awaitTerminalEvent();
        observer.assertValue(page -> page.getTotalCount() == userIds.size());
        observer.assertValue(page -> page.getCurrentPage() == 2);

        verify(userService, times(2)).findByIdIn(argThat(memberIds -> memberIds.size() == 25));
        verify(userService, times(1)).findByIdIn(argThat(memberIds -> memberIds.size() == 2));
        verify(organizationUserService, never()).findByIdIn(any());
    }

    @Test
    public void shouldFindMembersFromOrganizationUsers() {
        Group group = mock(Group.class);
        when(group.getReferenceType()).thenReturn(ReferenceType.ORGANIZATION);
        when(group.getMembers()).thenReturn(Arrays.asList("userid"));

        when(groupRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("group-id"))).thenReturn(Maybe.just(group));
        when(organizationUserService.findByIdIn(any())).thenReturn(Flowable.just(new User()));

        final TestObserver<Page<User>> observer = groupService.findMembers(ReferenceType.DOMAIN, DOMAIN, "group-id", 0, 0).test();
        observer.awaitTerminalEvent();

        verify(organizationUserService).findByIdIn(any());
        verify(userService, never()).findByIdIn(any());
    }
}
