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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.Group;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GroupRepositoryTest extends AbstractManagementTest {

    public static final String DOMAIN_ID = "DOMAIN_ID1";
    @Autowired
    protected GroupRepository repository;

    @Test
    public void shouldCreateGroup() {
        Group group = buildGroup();

        TestObserver<Group> testObserver = repository.create(group).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(g -> g.getId() != null);
        assertEqualsTo(group, testObserver);
    }

    private Group buildGroup() {
        Group group = new Group();
        String random = UUID.randomUUID().toString();
        group.setDescription("Description"+random);
        group.setName("name"+random);
        group.setReferenceId("ref"+random);
        group.setReferenceType(ReferenceType.DOMAIN);
        group.setCreatedAt(new Date());
        group.setUpdatedAt(new Date());
        group.setRoles(Arrays.asList("r1"+random, "r2"+random));
        List<String> members = new ArrayList<>();
        members.add("m1"+random);
        members.add("m2"+random);
        group.setMembers(members);
        return group;
    }

    private void assertEqualsTo(Group group, TestObserver<Group> testObserver) {
        testObserver.assertValue(g -> g.getName().equals(group.getName()));
        testObserver.assertValue(g -> g.getDescription().equals(group.getDescription()));
        testObserver.assertValue(g -> g.getReferenceId().equals(group.getReferenceId()));
        testObserver.assertValue(g -> g.getReferenceType().equals(group.getReferenceType()));
        testObserver.assertValue(g -> g.getMembers().size() == group.getMembers().size());
        testObserver.assertValue(g -> g.getMembers().containsAll(group.getMembers()));
        testObserver.assertValue(g -> g.getRoles().size() == group.getRoles().size());
        testObserver.assertValue(g -> g.getRoles().containsAll(group.getRoles()));
    }

    @Test
    public void shouldFindById() {
        Group group = buildGroup();
        Group createdGroup = repository.create(group).blockingGet();

        TestObserver<Group> testObserver = repository.findById(createdGroup.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(g -> g.getId().equals(createdGroup.getId()));
        assertEqualsTo(group, testObserver);
    }

    @Test
    public void shouldFindById_WithRef() {
        Group group = buildGroup();
        Group createdGroup = repository.create(group).blockingGet();

        TestObserver<Group> testObserver = repository.findById(createdGroup.getReferenceType(), createdGroup.getReferenceId(), createdGroup.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(g -> g.getId().equals(createdGroup.getId()));
        assertEqualsTo(group, testObserver);
    }

    @Test
    public void shouldUpdate() {
        Group group = buildGroup();
        Group createdGroup = repository.create(group).blockingGet();

        Group toUpdate = buildGroup();
        toUpdate.setId(createdGroup.getId());

        // update and check response
        final TestObserver<Group> testUpdate = repository.update(toUpdate).test();
        testUpdate.awaitTerminalEvent();

        testUpdate.assertComplete();
        testUpdate.assertNoErrors();
        testUpdate.assertValue(g -> g.getId().equals(toUpdate.getId()));
        assertEqualsTo(toUpdate, testUpdate);

        // validate the update using findById
        TestObserver<Group> testObserver = repository.findById(toUpdate.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(g -> g.getId().equals(toUpdate.getId()));
        assertEqualsTo(toUpdate, testObserver);
    }

    @Test
    public void shouldDelete() {
        Group group = buildGroup();
        Group createdGroup = repository.create(group).blockingGet();

        // validate the creation using findById
        TestObserver<Group> testObserver = repository.findById(createdGroup.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(g -> g.getId().equals(createdGroup.getId()));

        final TestObserver<Void> testDelete = repository.delete(createdGroup.getId()).test();
        testDelete.awaitTerminalEvent();

        testDelete.assertComplete();
        testDelete.assertNoErrors();

        final Group existAfterDelete = repository.findById(createdGroup.getId()).blockingGet();
        assertNull(existAfterDelete);
    }

    @Test
    public void shouldFindByMember() {
        Group group1 = buildGroup();
        Group createdGroup1 = repository.create(group1).blockingGet();
        final String member1 = group1.getMembers().get(0);
        final String member2 = group1.getMembers().get(1);

        Group group2 = buildGroup();
        // create a second group to add the same member as group 1
        group2.getMembers().add(member1);
        Group createdGroup2 = repository.create(group2).blockingGet();

        TestObserver<List<Group>> testObserver = repository.findByMember(member1).toList().test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(g -> g.size() == 2);
        testObserver.assertValue(g -> g.stream().map(Group::getId).collect(Collectors.toSet()).containsAll(Arrays.asList(createdGroup1.getId(), createdGroup2.getId())));

        testObserver = repository.findByMember(member2).toList().test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(g -> g.size() == 1);
        testObserver.assertValue(g -> g.get(0).getId().equals(createdGroup1.getId()));
    }

    @Test
    public void shouldFindAll() {
        List<Group> emptyList = repository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).toList().blockingGet();
        assertNotNull(emptyList);
        assertTrue(emptyList.isEmpty());

        final int loop = 10;
        for (int i = 0; i < loop; ++i) {
            // build 10 group with random domain
            repository.create(buildGroup()).blockingGet();
        }

        for (int i = 0; i < loop; ++i) {
            // build 10 group with DOMAIN_ID
            final Group item = buildGroup();
            item.setReferenceId(DOMAIN_ID);
            repository.create(item).blockingGet();
        }

        List<Group> groupOfDomain = repository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).toList().blockingGet();
        assertNotNull(groupOfDomain);
        assertEquals(loop, groupOfDomain.size());
        assertEquals(loop, groupOfDomain.stream().filter(g -> g.getReferenceId().equals(DOMAIN_ID) && g.getReferenceType().equals(ReferenceType.DOMAIN)).count());

        groupOfDomain = repository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).toList().blockingGet();
        assertNotNull(groupOfDomain);
        assertEquals(loop, groupOfDomain.size());
        assertEquals(loop, groupOfDomain.stream().filter(g -> g.getReferenceId().equals(DOMAIN_ID) && g.getReferenceType().equals(ReferenceType.DOMAIN)).count());
    }

    @Test
    public void shouldFindAll_WithPage() {
        List<Group> emptyList = repository.findAll(ReferenceType.DOMAIN, DOMAIN_ID).toList().blockingGet();
        assertNotNull(emptyList);
        assertTrue(emptyList.isEmpty());

        final int loop = 10;
        for (int i = 0; i < loop; ++i) {
            // build 10 group with random domain
            repository.create(buildGroup()).blockingGet();
        }

        for (int i = 0; i < loop; ++i) {
            // build 10 group with DOMAIN_ID
            final Group item = buildGroup();
            item.setReferenceId(DOMAIN_ID);
            repository.create(item).blockingGet();
        }

        Page<Group> groupOfDomain = repository.findAll(ReferenceType.DOMAIN, DOMAIN_ID, 0, 20).blockingGet();
        assertNotNull(groupOfDomain);
        assertEquals(0, groupOfDomain.getCurrentPage());
        assertEquals(loop, groupOfDomain.getTotalCount());
        assertEquals(loop, groupOfDomain.getData().stream()
                .filter(g -> g.getReferenceId().equals(DOMAIN_ID)
                        && g.getReferenceType().equals(ReferenceType.DOMAIN)).count());

        final Collection<Group> data = groupOfDomain.getData();

        groupOfDomain = repository.findAll(ReferenceType.DOMAIN, DOMAIN_ID, 0, 5).blockingGet();
        assertNotNull(groupOfDomain);
        assertEquals(loop, groupOfDomain.getTotalCount());
        assertEquals(0, groupOfDomain.getCurrentPage());
        assertEquals(5, groupOfDomain.getData().stream()
                .filter(g -> g.getReferenceId().equals(DOMAIN_ID)
                        && g.getReferenceType().equals(ReferenceType.DOMAIN)).count());

        final Collection<Group> data1 = groupOfDomain.getData();

        groupOfDomain = repository.findAll(ReferenceType.DOMAIN, DOMAIN_ID, 1, 5).blockingGet();
        assertNotNull(groupOfDomain);
        assertEquals(loop, groupOfDomain.getTotalCount());
        assertEquals(1, groupOfDomain.getCurrentPage());
        assertEquals(5, groupOfDomain.getData().stream()
                .filter(g -> g.getReferenceId().equals(DOMAIN_ID)
                        && g.getReferenceType().equals(ReferenceType.DOMAIN)).count());

        final Collection<Group> data2 = groupOfDomain.getData();
        Set<String> pagedData = new HashSet<>();
        pagedData.addAll(data1.stream().map(Group::getId).collect(Collectors.toSet()));
        pagedData.addAll(data2.stream().map(Group::getId).collect(Collectors.toSet()));
        // check that all group are different
        assertTrue(data.stream().map(Group::getId).collect(Collectors.toSet()).containsAll(pagedData));
    }


    @Test
    public void shouldFindIdsIn() {
        final int loop = 10;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < loop; ++i) {
            // build 10 group with random domain
            Group createdGroup = repository.create(buildGroup()).blockingGet();
            if (i %2 == 0) {
                ids.add(createdGroup.getId());
            }
        }

        final TestObserver<List<Group>> testObserver = repository.findByIdIn(ids).toList().test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue(lg -> lg.size() == ids.size());
        testObserver.assertValue(lg -> lg.stream().map(Group::getId).collect(Collectors.toList()).containsAll(ids));
    }

    @Test
    public void shouldFindByName() {
        Group group = buildGroup();
        Group createdGroup = repository.create(group).blockingGet();

        TestObserver<Group> testObserver = repository.findByName(group.getReferenceType(), group.getReferenceId(), group.getName()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(g -> g.getId().equals(createdGroup.getId()));
        assertEqualsTo(group, testObserver);
    }

    @Test
    public void shouldFindByName_unknown() {
        Group group = buildGroup();
        repository.create(group).blockingGet();

        TestObserver<Group> testObserver = repository.findByName(group.getReferenceType(), group.getReferenceId(), "unknown").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }
}
