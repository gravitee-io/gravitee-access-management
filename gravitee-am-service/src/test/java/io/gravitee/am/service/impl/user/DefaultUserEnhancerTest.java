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
package io.gravitee.am.service.impl.user;

import io.gravitee.am.model.Group;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.RoleService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@ExtendWith(MockitoExtension.class)
public class DefaultUserEnhancerTest {

    @Mock
    GroupService groupService;

    @Mock
    RoleService roleService;

    @InjectMocks
    DomainUserEnhancer domainUserEnhancer;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void shouldCollectRolesFromUserAndGroups(){
        // given
        User user = new User();
        user.setRoles(List.of("role3"));
        user.setDynamicRoles(List.of("role4"));
        Group group1 = new Group();

        group1.setId("gr1");
        group1.setName("gr1");
        group1.setRoles(List.of("role1"));

        Group group2 = new Group();
        group2.setId("gr2");
        group2.setName("gr2");
        group2.setRoles(List.of("role2"));
        Mockito.when(groupService.findByMember(user.getId())).thenReturn(Flowable.just(group1, group2));
        Mockito.when(groupService.findByIdIn(Mockito.any())).thenReturn(Flowable.empty());
        Mockito.when(roleService.findByIdIn(Mockito.anyList())).thenAnswer(a -> Single.just(roles(a.getArgument(0))));

        // when
        TestObserver<User> observer = domainUserEnhancer.enhance(user).test();

        // then
        observer.assertComplete();
        observer.assertValue(u -> u.getGroups().contains(group1.getName()));
        observer.assertValue(u -> u.getGroups().contains(group2.getName()));
        observer.assertValue(u -> u.getRolesPermissions().stream().map(Role::getId).collect(toSet()).contains("role1"));
        observer.assertValue(u -> u.getRolesPermissions().stream().map(Role::getId).collect(toSet()).contains("role2"));
        observer.assertValue(u -> u.getRolesPermissions().stream().map(Role::getId).collect(toSet()).contains("role3"));
        observer.assertValue(u -> u.getRolesPermissions().stream().map(Role::getId).collect(toSet()).contains("role4"));
    }

    @Test
    public void shouldCollectRolesFromUserAndGroupsAndDynamicGroups(){
        // given
        User user = new User();
        user.setRoles(List.of("role0"));
        user.setDynamicRoles(List.of("role4"));
        user.setDynamicGroups(List.of("gr3"));
        Group group1 = new Group();

        group1.setId("gr1");
        group1.setName("gr1");
        group1.setRoles(List.of("role1"));

        Group group2 = new Group();
        group2.setId("gr2");
        group2.setName("gr2");
        group2.setRoles(List.of("role2"));

        Group group3 = new Group();
        group3.setId("gr3");
        group3.setName("gr3");
        group3.setRoles(List.of("role3"));

        Mockito.when(groupService.findByMember(user.getId())).thenReturn(Flowable.just(group1, group2));
        Mockito.when(groupService.findByIdIn(user.getDynamicGroups())).thenReturn(Flowable.just(group2, group3));
        Mockito.when(roleService.findByIdIn(Mockito.anyList())).thenAnswer(a -> Single.just(roles(a.getArgument(0))));

        // when
        TestObserver<User> observer = domainUserEnhancer.enhance(user).test();

        // then
        observer.assertComplete();
        observer.assertValue(u -> u.getGroups().contains(group1.getName()));
        observer.assertValue(u -> u.getGroups().contains(group2.getName()));
        observer.assertValue(u -> u.getGroups().contains(group3.getName()));
        observer.assertValue(u -> u.getGroups().size() == 3);
        observer.assertValue(u -> u.getRolesPermissions().stream().map(Role::getId).collect(toSet()).contains("role0"));
        observer.assertValue(u -> u.getRolesPermissions().stream().map(Role::getId).collect(toSet()).contains("role1"));
        observer.assertValue(u -> u.getRolesPermissions().stream().map(Role::getId).collect(toSet()).contains("role2"));
        observer.assertValue(u -> u.getRolesPermissions().stream().map(Role::getId).collect(toSet()).contains("role3"));
        observer.assertValue(u -> u.getRolesPermissions().stream().map(Role::getId).collect(toSet()).contains("role4"));
    }


    private Set<Role> roles(List<String> ids) {
        return ids.stream().map(id -> {
            Role role = new Role();
            role.setId(id);
            return role;
        }).collect(toSet());
    }

}
