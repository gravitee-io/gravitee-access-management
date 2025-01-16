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

import io.gravitee.am.dataplane.api.repository.GroupRepository;
import io.gravitee.am.management.service.impl.DomainGroupServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.OrganizationUserService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.GroupAlreadyExistsException;
import io.gravitee.am.service.exception.GroupNotFoundException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewGroup;
import io.gravitee.am.service.model.UpdateGroup;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Mockito.any;
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
public class DomainGroupServiceTest {

    @InjectMocks
    private DomainGroupService domainGroupService = new DomainGroupServiceImpl();

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private UserService userService;

    @Mock
    private OrganizationUserService organizationUserService;

    @Mock
    private AuditService auditService;

    @Mock
    private RoleService roleService;

    private final static String DOMAIN = "domain1";
    private final static Domain DOMAIN_ENTITY = new Domain();

    @Before
    public void beforeClass() throws Exception {
        DOMAIN_ENTITY.setId("domain1");
        when(dataPlaneRegistry.getGroupRepository(any())).thenReturn(groupRepository);
    }

    @Test
    public void shouldFindById() {
        when(groupRepository.findById(any(), any(), eq("my-group"))).thenReturn(Maybe.just(new Group()));
        TestObserver testObserver = domainGroupService.findById(DOMAIN_ENTITY, "my-group").test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_groupNotFound() {
        when(groupRepository.findById(any(), any(), eq("my-group"))).thenReturn(Maybe.empty());
        TestObserver testObserver = domainGroupService.findById(DOMAIN_ENTITY, "my-group").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(groupRepository.findById(any(), any(), eq("my-group"))).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        domainGroupService.findById(DOMAIN_ENTITY, "my-group").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }


    @Test
    public void shouldFindAll() {
        when(groupRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(new Group()));
        TestObserver<List<Group>> testObserver = domainGroupService.findAll(DOMAIN_ENTITY).toList().test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(groups -> groups.size() == 1);
    }

    @Test
    public void shouldFindAll_technicalException() {
        when(groupRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = domainGroupService.findAll(DOMAIN_ENTITY).test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldFindAllPagination() {
        Page pagedGroups = new Page(Collections.singleton(new Group()), 1, 1);
        when(groupRepository.findAll(ReferenceType.DOMAIN, DOMAIN, 1, 1)).thenReturn(Single.just(pagedGroups));
        TestObserver<Page<Group>> testObserver = domainGroupService.findAll(DOMAIN_ENTITY, 1, 1).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.getData().size() == 1);
    }

    @Test
    public void shouldFindAllPagination_technicalException() {
        when(groupRepository.findAll(ReferenceType.DOMAIN, DOMAIN, 1, 1)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        domainGroupService.findAll(DOMAIN_ENTITY, 1, 1).subscribe(testObserver);

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

        TestObserver testObserver = domainGroupService.create(DOMAIN_ENTITY, newGroup, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        domainGroupService.create(DOMAIN_ENTITY, newGroup, null).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate_alreadyExists() {
        NewGroup newGroup = Mockito.mock(NewGroup.class);
        when(newGroup.getName()).thenReturn("names");
        when(groupRepository.findByName(ReferenceType.DOMAIN, DOMAIN, newGroup.getName())).thenReturn(Maybe.just(new Group()));

        TestObserver testObserver = new TestObserver();
        domainGroupService.create(DOMAIN_ENTITY, newGroup, null).subscribe(testObserver);

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

        TestObserver testObserver = domainGroupService.update(DOMAIN_ENTITY, "my-group", updateGroup, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(groupRepository, times(1)).findById(ReferenceType.DOMAIN, DOMAIN, "my-group");
        verify(groupRepository, times(1)).update(any(Group.class));
    }

    @Test
    public void shouldNotUpdate_NameAlreadyExist() {
        UpdateGroup updateGroup = Mockito.mock(UpdateGroup.class);
        when(groupRepository.findById(any(), any(), any())).thenReturn(Maybe.just(new Group()));
        when(groupRepository.findByName(any(), any(), any())).thenReturn(Maybe.just(new Group()));

        TestObserver testObserver = new TestObserver();
        domainGroupService.update(DOMAIN_ENTITY, "my-group", updateGroup, null).subscribe(testObserver);

        testObserver.assertError(GroupAlreadyExistsException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate_groupNotFound() {
        UpdateGroup updateGroup = Mockito.mock(UpdateGroup.class);
        when(groupRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-group")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        domainGroupService.update(DOMAIN_ENTITY, "my-group", updateGroup, null).subscribe(testObserver);

        testObserver.assertError(GroupNotFoundException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        Group group = new Group();
        group.setReferenceId(DOMAIN);
        group.setReferenceType(ReferenceType.DOMAIN);
        when(groupRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-group")).thenReturn(Maybe.just(group));
        when(groupRepository.delete("my-group")).thenReturn(Completable.complete());

        TestObserver testObserver = domainGroupService.delete(DOMAIN_ENTITY, "my-group", null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(groupRepository, times(1)).delete("my-group");
    }

    @Test
    public void shouldDelete_technicalException() {
        when(groupRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-group")).thenReturn(Maybe.just(new Group()));
        when(groupRepository.delete("my-group")).thenReturn(Completable.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        domainGroupService.delete(DOMAIN_ENTITY, "my-group", null).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_groupNotFound() {
        when(groupRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-group")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        domainGroupService.delete(DOMAIN_ENTITY, "my-group", null).subscribe(testObserver);

        testObserver.assertError(GroupNotFoundException.class);
        testObserver.assertNotComplete();

        verify(groupRepository, never()).delete("my-group");
    }

    @Test
    public void shouldAssignRoles() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        Group group = new Group();
        group.setId("group-id");
        group.setReferenceType(ReferenceType.DOMAIN);
        group.setReferenceId(DOMAIN);

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(groupRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("group-id"))).thenReturn(Maybe.just(group));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(roles));
        when(groupRepository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));

        TestObserver testObserver = domainGroupService.assignRoles(DOMAIN_ENTITY, group.getId(), rolesIds, null).test();
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

        TestObserver testObserver = domainGroupService.assignRoles(DOMAIN_ENTITY, group.getId(), rolesIds, null).test();
        testObserver.assertNotComplete();
        testObserver.assertError(RoleNotFoundException.class);
        verify(groupRepository, never()).update(any());
    }

    @Test
    public void shouldRevokeRole() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        Group group = new Group();
        group.setId("group-id");
        group.setReferenceId(DOMAIN);
        group.setReferenceType(ReferenceType.DOMAIN);

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(groupRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("group-id"))).thenReturn(Maybe.just(group));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(roles));
        when(groupRepository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));

        TestObserver testObserver = domainGroupService.revokeRoles(DOMAIN_ENTITY, group.getId(), rolesIds, null).test();
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

        TestObserver testObserver = domainGroupService.revokeRoles(DOMAIN_ENTITY, group.getId(), rolesIds, null).test();
        testObserver.assertNotComplete();
        testObserver.assertError(RoleNotFoundException.class);
        verify(groupRepository, never()).update(any());
    }

    @Test
    public void shouldFindMembersFromDomainUsers() {
        Group group = mock(Group.class);
        when(group.getMembers()).thenReturn(Arrays.asList("userid"));

        when(groupRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("group-id"))).thenReturn(Maybe.just(group));
        when(userService.findByIdIn(any())).thenReturn(Flowable.just(new User()));

        final TestObserver<Page<User>> observer = domainGroupService.findMembers(DOMAIN_ENTITY, "group-id", 0, 0).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        verify(userService).findByIdIn(any());
        verify(organizationUserService, never()).findByIdIn(any());
    }

    @Test
    public void shouldFindMembersFromDomainUsers_Paginate() {
        final var userIds = IntStream.range(0, 52).mapToObj(i -> "user-" + i).collect(Collectors.toList());
        var group = mock(Group.class);
        when(group.getMembers()).thenReturn(userIds);

        when(groupRepository.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN), eq("group-id"))).thenReturn(Maybe.just(group));
        when(userService.findByIdIn(any())).thenReturn(Flowable.fromIterable(userIds.stream().map(userId -> {
            final var user = new User();
            user.setId(userId);
            return user;
        }).collect(Collectors.toList())));

        var observer = domainGroupService.findMembers(DOMAIN_ENTITY, "group-id", 0, 25).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertValue(page -> page.getTotalCount() == userIds.size());
        observer.assertValue(page -> page.getCurrentPage() == 0);

        observer = domainGroupService.findMembers(DOMAIN_ENTITY, "group-id", 1, 25).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertValue(page -> page.getTotalCount() == userIds.size());
        observer.assertValue(page -> page.getCurrentPage() == 1);

        observer = domainGroupService.findMembers(DOMAIN_ENTITY, "group-id", 2, 25).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertValue(page -> page.getTotalCount() == userIds.size());
        observer.assertValue(page -> page.getCurrentPage() == 2);

        verify(userService, times(2)).findByIdIn(argThat(memberIds -> memberIds.size() == 25));
        verify(userService, times(1)).findByIdIn(argThat(memberIds -> memberIds.size() == 2));
        verify(organizationUserService, never()).findByIdIn(any());
    }
}
