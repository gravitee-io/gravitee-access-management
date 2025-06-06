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
import io.gravitee.am.common.scim.filter.AttributePath;
import io.gravitee.am.common.scim.filter.Filter;
import io.gravitee.am.common.scim.filter.Operator;
import io.gravitee.am.dataplane.api.repository.GroupRepository;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.gateway.handler.scim.exception.UniquenessException;
import io.gravitee.am.gateway.handler.scim.model.Group;
import io.gravitee.am.gateway.handler.scim.model.ListResponse;
import io.gravitee.am.gateway.handler.scim.model.Member;
import io.gravitee.am.gateway.handler.scim.model.Operation;
import io.gravitee.am.gateway.handler.scim.model.PatchOp;
import io.gravitee.am.gateway.handler.scim.service.impl.ScimGroupServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.AuditService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
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
public class ScimGroupServiceTest {

    @InjectMocks
    private ScimGroupService groupService = new ScimGroupServiceImpl();

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private Domain domain;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AuditService auditService;

    @Test
    public void shouldCreateGroup() {
        final String domainId = "domain";

        Group newGroup = mock(Group.class);
        when(newGroup.getDisplayName()).thenReturn("my-group");

        io.gravitee.am.model.Group createdGroup = mock(io.gravitee.am.model.Group.class);
        when(createdGroup.getName()).thenReturn("my-group");
        when(createdGroup.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(createdGroup.getReferenceId()).thenReturn("id");
        when(domain.getId()).thenReturn(domainId);
        when(groupRepository.findByName(ReferenceType.DOMAIN, domain.getId(), newGroup.getDisplayName())).thenReturn(Maybe.empty());
        when(groupRepository.create(any())).thenReturn(Single.just(createdGroup));

        TestObserver<Group> testObserver = groupService.create(newGroup, "/", null).test();
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
        when(createdGroup.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(createdGroup.getReferenceId()).thenReturn("id");

        when(domain.getId()).thenReturn(domainId);
        when(groupRepository.findByName(ReferenceType.DOMAIN, domain.getId(), newGroup.getDisplayName())).thenReturn(Maybe.empty());
        when(userRepository.findByIdIn(eq(Reference.domain(domain.getId())), any())).thenReturn(Flowable.just(user));
        when(groupRepository.create(any())).thenReturn(Single.just(createdGroup));

        TestObserver<Group> testObserver = groupService.create(newGroup, "https://mydomain/scim/Groups", null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(g -> "my-group".equals(g.getDisplayName()));
        verify(userRepository, times(1)).findByIdIn(eq(Reference.domain(domain.getId())), any());
    }

    @Test
    public void shouldNotCreateGroup_already_exist() {
        final String domainId = "domain";

        Group newGroup = mock(Group.class);
        when(newGroup.getDisplayName()).thenReturn("my-group");

        when(domain.getId()).thenReturn(domainId);
        when(groupRepository.findByName(ReferenceType.DOMAIN, domain.getId(), newGroup.getDisplayName())).thenReturn(Maybe.just(new io.gravitee.am.model.Group()));

        TestObserver<Group> testObserver = groupService.create(newGroup, "/", null).test();
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
        io.gravitee.am.model.Group group = new io.gravitee.am.model.Group();
        group.setReferenceType(ReferenceType.DOMAIN);
        group.setReferenceId("id");
        when(groupRepository.findById(groupId)).thenReturn(Maybe.just(group));
        when(groupRepository.findByName(eq(ReferenceType.DOMAIN), anyString(), anyString())).thenReturn(Maybe.empty());
        doAnswer(invocation -> {
            io.gravitee.am.model.Group groupToUpdate = invocation.getArgument(0);
            Assert.assertTrue(groupToUpdate.getName().equals("my group 2"));
            return Single.just(groupToUpdate);
        }).when(groupRepository).update(any());

        TestObserver<Group> testObserver = groupService.patch(groupId, patchOp, "/", null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(g -> "my group 2".equals(g.getDisplayName()));
    }

    @Test
    public void shouldPatchGroup_PreserveRoles() throws Exception {
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

        final io.gravitee.am.model.Group existingGroup = new io.gravitee.am.model.Group();
        existingGroup.setRoles(List.of("role1"));
        existingGroup.setDescription("group description");
        existingGroup.setId("my-group-id");
        existingGroup.setName("my-group-name");
        existingGroup.setReferenceId(domainId);
        existingGroup.setReferenceType(ReferenceType.DOMAIN);
        when(groupRepository.findById(groupId)).thenReturn(Maybe.just(existingGroup));

        when(groupRepository.findByName(eq(ReferenceType.DOMAIN), anyString(), anyString())).thenReturn(Maybe.empty());
        doAnswer(invocation -> {
            io.gravitee.am.model.Group groupToUpdate = invocation.getArgument(0);
            Assert.assertTrue(groupToUpdate.getName().equals("my group 2"));
            Assert.assertTrue(groupToUpdate.getDescription().equals(existingGroup.getDescription()));
            Assert.assertTrue(groupToUpdate.getRoles() != null && groupToUpdate.getRoles().get(0).equals(existingGroup.getRoles().get(0)));

            return Single.just(groupToUpdate);
        }).when(groupRepository).update(any());

        TestObserver<Group> testObserver = groupService.patch(groupId, patchOp, "/", null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(g -> "my group 2".equals(g.getDisplayName()));

    }

    @Test
    public void shouldListGroups() {
        List<io.gravitee.am.model.Group> groups = IntStream.range(0, 10).mapToObj(i -> {
            final io.gravitee.am.model.Group group = new io.gravitee.am.model.Group();
            group.setName("" + i);
            return group;
        }).toList();

        final Page page = new Page(groups, 0, groups.size());
        final String domainID = "any-domain-id";
        when(domain.getId()).thenReturn(domainID);
        when(groupRepository.findAllScim(ReferenceType.DOMAIN, domainID, 0, 10)).thenReturn(Single.just(page));

        TestObserver<ListResponse<Group>> observer = groupService.list(null, 0, 10, "").test();
        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(listResp -> 10 == listResp.getItemsPerPage()
                &&
                listResp.getResources().stream().map(Group::getDisplayName).collect(Collectors.joining(","))
                        .equals(groups.stream().map(io.gravitee.am.model.Group::getName).collect(Collectors.joining(","))));

        verify(groupRepository, times(1)).findAllScim(ReferenceType.DOMAIN, domainID, 0, 10);
    }

    @Test
    public void shouldSearchGroups() {
        final io.gravitee.am.model.Group group = new io.gravitee.am.model.Group();
        final Page page = new Page(List.of(group), 0, 1);
        final String domainID = "any-domain-id";
        Filter dummyFilter = new Filter(Operator.EQUALITY, new AttributePath("", "", ""), "", false, null);
        when(domain.getId()).thenReturn(domainID);
        when(groupRepository.searchScim(any(ReferenceType.class), anyString(), any(FilterCriteria.class), anyInt(), anyInt())).thenReturn(Single.just(page));

        TestObserver<ListResponse<Group>> observer = groupService.list(dummyFilter, 0, 10, "").test();
        observer.assertNoErrors();
        observer.assertComplete();

        verify(groupRepository, times(1)).searchScim(any(ReferenceType.class), anyString(), any(FilterCriteria.class), anyInt(), anyInt());
    }
}
