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
package io.gravitee.am.gateway.handler.scim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.gravitee.am.gateway.handler.scim.exception.UniquenessException;
import io.gravitee.am.gateway.handler.scim.model.Group;
import io.gravitee.am.gateway.handler.scim.model.Member;
import io.gravitee.am.gateway.handler.scim.model.Operation;
import io.gravitee.am.gateway.handler.scim.model.PatchOp;
import io.gravitee.am.gateway.handler.scim.service.impl.GroupServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.management.api.GroupRepository;
import io.gravitee.am.repository.management.api.UserRepository;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class GroupServiceTest {

    @InjectMocks
    private GroupService groupService = new GroupServiceImpl();

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private Domain domain;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    public void shouldCreateGroup() {
        final String domainId = "domain";

        Group newGroup = mock(Group.class);
        when(newGroup.getDisplayName()).thenReturn("my-group");

        io.gravitee.am.model.Group createdGroup = mock(io.gravitee.am.model.Group.class);
        when(createdGroup.getName()).thenReturn("my-group");

        when(domain.getId()).thenReturn(domainId);
        when(groupRepository.findByName(ReferenceType.DOMAIN, domain.getId(), newGroup.getDisplayName())).thenReturn(Maybe.empty());
        when(groupRepository.create(any())).thenReturn(Single.just(createdGroup));

        TestObserver<Group> testObserver = groupService.create(newGroup, "/").test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(g -> "my-group".equals(g.getDisplayName()));
    }

    @Test
    public void shouldCreateGroup_with_members() {
        final String domainId = "domain";

        User user = mock(User.class);
        when(user.getId()).thenReturn("12345");
        when(user.getUsername()).thenReturn("my-member");

        Member member = mock(Member.class);
        when(member.getValue()).thenReturn("12345");

        Group newGroup = mock(Group.class);
        when(newGroup.getDisplayName()).thenReturn("my-group");
        when(newGroup.getMembers()).thenReturn(Collections.singletonList(member));

        io.gravitee.am.model.Group createdGroup = mock(io.gravitee.am.model.Group.class);
        when(createdGroup.getName()).thenReturn("my-group");

        when(domain.getId()).thenReturn(domainId);
        when(groupRepository.findByName(ReferenceType.DOMAIN, domain.getId(), newGroup.getDisplayName())).thenReturn(Maybe.empty());
        when(userRepository.findByIdIn(any())).thenReturn(Flowable.just(user));
        when(groupRepository.create(any())).thenReturn(Single.just(createdGroup));

        TestObserver<Group> testObserver = groupService.create(newGroup, "https://mydomain/scim/Groups").test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(g -> "my-group".equals(g.getDisplayName()));
        verify(userRepository, times(1)).findByIdIn(any());
    }

    @Test
    public void shouldNotCreateGroup_already_exist() {
        final String domainId = "domain";

        Group newGroup = mock(Group.class);
        when(newGroup.getDisplayName()).thenReturn("my-group");

        when(domain.getId()).thenReturn(domainId);
        when(groupRepository.findByName(ReferenceType.DOMAIN, domain.getId(), newGroup.getDisplayName())).thenReturn(Maybe.just(new io.gravitee.am.model.Group()));

        TestObserver<Group> testObserver = groupService.create(newGroup, "/").test();
        testObserver.assertNotComplete();
        testObserver.assertError(UniquenessException.class);
        verify(groupRepository, never()).create(any());
    }

    @Test
    public void shouldPatchGroup() throws Exception {
        final String domainId = "domain";
        final String domainName = "domainName";
        final String groupId = "groupId";

        ObjectNode groupNode = mock(ObjectNode.class);
        when(groupNode.get("displayName")).thenReturn(new TextNode("my group"));

        Operation operation = mock(Operation.class);
        doAnswer(invocation -> {
            ObjectNode arg0 = invocation.getArgument(0);
            Assert.assertTrue(arg0.get("displayName").asText().equals("my group"));
            return null;
        }).when(operation).apply(any());

        PatchOp patchOp = mock(PatchOp.class);
        when(patchOp.getOperations()).thenReturn(Collections.singletonList(operation));

        Group patchGroup = mock(Group.class);
        when(patchGroup.getDisplayName()).thenReturn("my group 2");

        when(domain.getId()).thenReturn(domainId);
        when(domain.getName()).thenReturn(domainName);
        when(objectMapper.convertValue(any(), eq(ObjectNode.class))).thenReturn(groupNode);
        when(objectMapper.treeToValue(groupNode, Group.class)).thenReturn(patchGroup);
        when(groupRepository.findById(groupId)).thenReturn(Maybe.just(new io.gravitee.am.model.Group()));
        when(groupRepository.findByName(eq(ReferenceType.DOMAIN), anyString(), anyString())).thenReturn(Maybe.empty());
        doAnswer(invocation -> {
            io.gravitee.am.model.Group groupToUpdate = invocation.getArgument(0);
            Assert.assertTrue(groupToUpdate.getName().equals("my group 2"));
            return Single.just(groupToUpdate);
        }).when(groupRepository).update(any());

        TestObserver<Group> testObserver = groupService.patch(groupId, patchOp, "/").test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(g -> "my group 2".equals(g.getDisplayName()));
    }
}
