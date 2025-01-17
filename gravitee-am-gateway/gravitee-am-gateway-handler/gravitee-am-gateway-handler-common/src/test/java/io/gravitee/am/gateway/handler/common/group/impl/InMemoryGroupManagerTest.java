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
package io.gravitee.am.gateway.handler.common.group.impl;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.GroupEvent;
import io.gravitee.am.dataplane.api.repository.GroupRepository;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.common.event.impl.SimpleEvent;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InMemoryGroupManagerTest {

    @Mock
    Domain domain;

    @Mock
    EventManager eventManager;

    @Mock
    GroupRepository groupRepository;

    private InMemoryGroupManager groupManager;

    @BeforeEach
    void setUp() {
        this.groupManager = new InMemoryGroupManager(domain, eventManager, groupRepository);
        Mockito.when(domain.getId()).thenReturn("id");
    }

    @Test
    public void shouldCreateNewGroup() throws Exception {
        // when
        String groupId = "gr1";
        Group group = new Group();
        group.setId(groupId);
        Maybe<Group> maybe = Maybe.just(group);
        when(groupRepository.findById(groupId)).thenReturn(maybe);
        Payload payload = new Payload(groupId, new Reference(ReferenceType.DOMAIN, "id"), Action.CREATE);
        groupManager.onEvent(new SimpleEvent<>(GroupEvent.DEPLOY, payload));

        TestObserver<Group> observer = maybe.test();
        observer.assertComplete();
        Assertions.assertTrue(groupManager.groups.containsKey(groupId));
        // if group has no member, this method should pass with empty list
        TestSubscriber<Group> testSubscriber = groupManager.findByMember(UUID.randomUUID().toString()).test();
        testSubscriber.await(10, TimeUnit.SECONDS);
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(0);
    }

    @Test
    public void shouldFindByIds() throws Exception {
        final String groupId = "gr1";

        TestSubscriber<Group> test = groupManager.findByIds(List.of(groupId)).test();
        test.assertComplete();
        test.assertNoErrors();
        test.assertValueCount(0);

        // when
        Group group = new Group();
        group.setId(groupId);
        Maybe<Group> maybe = Maybe.just(group);
        when(groupRepository.findById(groupId)).thenReturn(maybe);
        Payload payload = new Payload(groupId, new Reference(ReferenceType.DOMAIN, "id"), Action.CREATE);
        groupManager.onEvent(new SimpleEvent<>(GroupEvent.DEPLOY, payload));

        TestObserver<Group> observer = maybe.test();
        observer.assertComplete();
        Assertions.assertTrue(groupManager.groups.containsKey(groupId));

        test = groupManager.findByIds(List.of(groupId)).test();
        test.assertComplete();
        test.assertNoErrors();
        test.assertValueCount(1);
    }

    @Test
    public void shouldCreateUpdateGroup() {
        // when
        String groupId = "gr1";
        Group existingGroup = new Group();
        existingGroup.setId(groupId);

        groupManager.groups.put(groupId, existingGroup);

        Group newGroup = new Group();
        newGroup.setId(groupId);
        newGroup.setRoles(List.of("role"));

        Maybe<Group> maybe = Maybe.just(newGroup);
        when(groupRepository.findById(groupId)).thenReturn(maybe);
        Payload payload = new Payload(groupId, new Reference(ReferenceType.DOMAIN, "id"), Action.UPDATE);
        groupManager.onEvent(new SimpleEvent<>(GroupEvent.UPDATE, payload));

        TestObserver<Group> observer = maybe.test();
        observer.assertComplete();
        Assertions.assertTrue(groupManager.groups.get(groupId).getRoles().contains("role"));
    }

    @Test
    public void shouldRemoveGroup() {
        // when
        String groupId = "gr1";
        Group existingGroup = new Group();
        existingGroup.setId(groupId);

        groupManager.groups.put(groupId, existingGroup);


        Payload payload = new Payload(groupId, new Reference(ReferenceType.DOMAIN, "id"), Action.DELETE);
        groupManager.onEvent(new SimpleEvent<>(GroupEvent.UNDEPLOY, payload));

        Assertions.assertFalse(groupManager.groups.containsKey(groupId));
    }


}
