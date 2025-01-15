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
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.GroupRepository;
import io.gravitee.am.service.exception.GroupAlreadyExistsException;
import io.gravitee.am.service.exception.GroupNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.OrganizationGroupServiceImpl;
import io.gravitee.am.service.model.NewGroup;
import io.gravitee.am.service.model.UpdateGroup;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
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
public class OrganizationGroupServiceTest {

    @InjectMocks
    private OrganizationGroupService groupService = new OrganizationGroupServiceImpl();

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private OrganizationUserService organizationUserService;

    private final static String ORGANIZATION = "org1";

    @Test
    public void shouldFindById() {
        when(groupRepository.findById("my-group")).thenReturn(Maybe.just(new Group()));
        TestObserver testObserver = groupService.findById("my-group").test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_groupNotFound() {
        when(groupRepository.findById("my-group")).thenReturn(Maybe.empty());
        TestObserver testObserver = groupService.findById("my-group").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        when(groupRepository.findAll(ReferenceType.ORGANIZATION, ORGANIZATION)).thenReturn(Flowable.just(new Group()));
        TestObserver<List<Group>> testObserver = groupService.findByDomain(ORGANIZATION).toList().test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(groups -> groups.size() == 1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(groupRepository.findAll(ReferenceType.ORGANIZATION, ORGANIZATION)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = groupService.findByDomain(ORGANIZATION).test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainPagination() {
        Page pagedGroups = new Page(Collections.singleton(new Group()), 1, 1);
        when(groupRepository.findAll(ReferenceType.ORGANIZATION, ORGANIZATION, 1, 1)).thenReturn(Single.just(pagedGroups));
        TestObserver<Page<Group>> testObserver = groupService.findByDomain(ORGANIZATION, 1, 1).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.getData().size() == 1);
    }

    @Test
    public void shouldFindByDomainPagination_technicalException() {
        when(groupRepository.findAll(ReferenceType.ORGANIZATION, ORGANIZATION, 1, 1)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        groupService.findByDomain(ORGANIZATION, 1, 1).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewGroup newGroup = Mockito.mock(NewGroup.class);
        Group group = new Group();
        group.setReferenceType(ReferenceType.DOMAIN);
        group.setReferenceId(ORGANIZATION);

        when(newGroup.getName()).thenReturn("name");
        when(groupRepository.findByName(ReferenceType.ORGANIZATION, ORGANIZATION, newGroup.getName())).thenReturn(Maybe.empty());
        when(groupRepository.create(any(Group.class))).thenReturn(Single.just(group));

        TestObserver testObserver = groupService.create(ReferenceType.ORGANIZATION, ORGANIZATION, newGroup, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(groupRepository, times(1)).create(any(Group.class));
    }

    @Test
    public void shouldCreate_technicalException() {
        NewGroup newGroup = Mockito.mock(NewGroup.class);
        when(newGroup.getName()).thenReturn("name");
        when(groupRepository.findByName(ReferenceType.ORGANIZATION, ORGANIZATION, newGroup.getName())).thenReturn(Maybe.empty());
        when(groupRepository.create(any(Group.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        groupService.create(ReferenceType.ORGANIZATION, ORGANIZATION, newGroup, null).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate_alreadyExists() {
        NewGroup newGroup = Mockito.mock(NewGroup.class);
        when(newGroup.getName()).thenReturn("names");
        when(groupRepository.findByName(ReferenceType.ORGANIZATION, ORGANIZATION, newGroup.getName())).thenReturn(Maybe.just(new Group()));

        TestObserver testObserver = new TestObserver();
        groupService.create(ReferenceType.ORGANIZATION, ORGANIZATION, newGroup, null).subscribe(testObserver);

        testObserver.assertError(GroupAlreadyExistsException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate() {
        UpdateGroup updateGroup = Mockito.mock(UpdateGroup.class);
        Group group = new Group();
        group.setReferenceType(ReferenceType.DOMAIN);
        group.setReferenceId(ORGANIZATION);

        when(updateGroup.getName()).thenReturn("name");
        when(groupRepository.findById(ReferenceType.ORGANIZATION, ORGANIZATION, "my-group")).thenReturn(Maybe.just(group));
        when(groupRepository.findByName(ReferenceType.ORGANIZATION, ORGANIZATION, updateGroup.getName())).thenReturn(Maybe.empty());
        when(groupRepository.update(any(Group.class))).thenReturn(Single.just(group));

        TestObserver testObserver = groupService.update(ORGANIZATION, "my-group", updateGroup).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(groupRepository, times(1)).findById(ReferenceType.ORGANIZATION, ORGANIZATION, "my-group");
        verify(groupRepository, times(1)).update(any(Group.class));
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateGroup updateGroup = Mockito.mock(UpdateGroup.class);
        when(groupRepository.findById(ReferenceType.ORGANIZATION, ORGANIZATION, "my-group")).thenReturn(Maybe.just(new Group()));

        TestObserver testObserver = new TestObserver();
        groupService.update(ORGANIZATION, "my-group", updateGroup).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate_groupNotFound() {
        UpdateGroup updateGroup = Mockito.mock(UpdateGroup.class);
        when(groupRepository.findById(ReferenceType.ORGANIZATION, ORGANIZATION, "my-group")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        groupService.update(ORGANIZATION, "my-group", updateGroup).subscribe(testObserver);

        testObserver.assertError(GroupNotFoundException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        Group group = new Group();
        group.setReferenceId(ORGANIZATION);
        group.setReferenceType(ReferenceType.DOMAIN);
        when(groupRepository.findById(ReferenceType.ORGANIZATION, ORGANIZATION, "my-group")).thenReturn(Maybe.just(group));
        when(groupRepository.delete("my-group")).thenReturn(Completable.complete());

        TestObserver testObserver = groupService.delete(ReferenceType.ORGANIZATION, ORGANIZATION, "my-group").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(groupRepository, times(1)).delete("my-group");
    }

    @Test
    public void shouldDelete_technicalException() {
        when(groupRepository.findById(ReferenceType.ORGANIZATION, ORGANIZATION, "my-group")).thenReturn(Maybe.just(new Group()));
        when(groupRepository.delete("my-group")).thenReturn(Completable.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        groupService.delete(ReferenceType.ORGANIZATION, ORGANIZATION, "my-group").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_groupNotFound() {
        when(groupRepository.findById(ReferenceType.ORGANIZATION, ORGANIZATION, "my-group")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        groupService.delete(ReferenceType.ORGANIZATION, ORGANIZATION, "my-group").subscribe(testObserver);

        testObserver.assertError(GroupNotFoundException.class);
        testObserver.assertNotComplete();

        verify(groupRepository, never()).delete("my-group");
    }

    @Test
    public void shouldFindMembersFromOrganizationUsers() {
        Group group = mock(Group.class);
        when(group.getReferenceType()).thenReturn(ReferenceType.ORGANIZATION);
        when(group.getMembers()).thenReturn(Arrays.asList("userid"));

        when(groupRepository.findById(eq(ReferenceType.DOMAIN), eq(ORGANIZATION), eq("group-id"))).thenReturn(Maybe.just(group));
        when(organizationUserService.findByIdIn(any())).thenReturn(Flowable.just(new User()));

        final TestObserver<Page<User>> observer = groupService.findMembers(ReferenceType.ORGANIZATION, ORGANIZATION, "group-id", 0, 0).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        verify(organizationUserService).findByIdIn(any());
    }
}
