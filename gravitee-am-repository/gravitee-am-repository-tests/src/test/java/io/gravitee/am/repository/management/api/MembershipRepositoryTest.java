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

import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.am.repository.management.api.search.MembershipCriteria;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MembershipRepositoryTest extends AbstractManagementTest {
    public static final String ORGANIZATION_ID = "ORGANIZATIONID123";
    @Autowired
    private MembershipRepository membershipRepository;

    @Test
    public void testFindById() {

        Membership membership = new Membership();
        membership.setRoleId("role#1");
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setMemberType(MemberType.USER);
        membership.setMemberId("user#1");
        membership.setFromRoleMapper(true);

        Membership createdMembership = membershipRepository.create(membership).blockingGet();

        TestObserver<Membership> obs = membershipRepository.findById(createdMembership.getId()).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(m -> m.getId().equals(createdMembership.getId())
                && m.getRoleId().equals(membership.getRoleId())
                && m.getReferenceType() == membership.getReferenceType()
                && m.getReferenceId().equals(membership.getReferenceId())
                && m.getMemberType() == membership.getMemberType()
                && m.getMemberId().equals(membership.getMemberId())
                && m.isFromRoleMapper());
    }

    @Test
    public void testFindByReference() {

        Membership membership = new Membership();
        membership.setRoleId("role#1");
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setMemberType(MemberType.USER);
        membership.setMemberId("user#1");

        Membership createdMembership = membershipRepository.create(membership).blockingGet();

        TestObserver<List<Membership>> obs = membershipRepository.findByReference(ORGANIZATION_ID, ReferenceType.ORGANIZATION).toList().test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertValue(m -> m.size() == 1 && m.get(0).getId().equals(createdMembership.getId()));
    }

    @Test
    public void testFindByMember() {

        Membership membership = new Membership();
        membership.setRoleId("role#1");
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setMemberType(MemberType.USER);
        membership.setMemberId("user#1");

        Membership createdMembership = membershipRepository.create(membership).blockingGet();

        TestObserver<List<Membership>> obs = membershipRepository.findByMember("user#1", MemberType.USER).toList().test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertValue(m -> m.size() == 1 && m.get(0).getMemberId().equals(createdMembership.getMemberId()));
    }

    @Test
    public void testFindByReferenceAndMember() {

        Membership membership = new Membership();
        membership.setRoleId("role#1");
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setMemberType(MemberType.USER);
        membership.setMemberId("user#1");

        Membership createdMembership = membershipRepository.create(membership).blockingGet();

        TestObserver<Membership> obs = membershipRepository.findByReferenceAndMember(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberType(), membership.getMemberId()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertValue(m -> m.getId().equals(createdMembership.getId()));
    }

    @Test
    public void testFindByReferenceIdAndCriteria() {

        Membership membership = new Membership();
        membership.setRoleId("role#1");
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setMemberType(MemberType.USER);
        membership.setMemberId("user#1");

        Membership groupMembership = new Membership();
        groupMembership.setRoleId("role#1");
        groupMembership.setReferenceType(ReferenceType.ORGANIZATION);
        groupMembership.setReferenceId(ORGANIZATION_ID);
        groupMembership.setMemberType(MemberType.GROUP);
        groupMembership.setMemberId("group#1");

        membershipRepository.create(membership).blockingGet();
        membershipRepository.create(groupMembership).blockingGet();

        MembershipCriteria criteria = new MembershipCriteria();
        TestSubscriber<Membership> obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(2);

        criteria.setUserId("user#1");
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(1);
        obs.assertValue(m -> m.getMemberType() == MemberType.USER && m.getMemberId().equals("user#1"));

        criteria.setUserId(null);
        criteria.setGroupIds(Arrays.asList("group#1"));
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(1);
        obs.assertValue(m -> m.getMemberType() == MemberType.GROUP && m.getMemberId().equals("group#1"));

        criteria.setUserId("user#1");
        criteria.setGroupIds(Arrays.asList("group#1"));
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoValues();
        obs.assertNoErrors();

        criteria.setUserId("user#1");
        criteria.setGroupIds(Arrays.asList("group#1"));
        criteria.setLogicalOR(true);
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(2);
    }

    @Test
    public void testFindByCriteria() {

        Membership membership = new Membership();
        membership.setRoleId("role#1");
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setMemberType(MemberType.USER);
        membership.setMemberId("user#1");

        Membership groupMembership = new Membership();
        groupMembership.setRoleId("role#1");
        groupMembership.setReferenceType(ReferenceType.ORGANIZATION);
        groupMembership.setReferenceId(ORGANIZATION_ID);
        groupMembership.setMemberType(MemberType.GROUP);
        groupMembership.setMemberId("group#1");

        membershipRepository.create(membership).blockingGet();
        membershipRepository.create(groupMembership).blockingGet();

        MembershipCriteria criteria = new MembershipCriteria();
        TestSubscriber<Membership> obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(2);

        criteria.setUserId("user#1");
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(1);
        obs.assertValue(m -> m.getMemberType() == MemberType.USER && m.getMemberId().equals("user#1"));

        criteria.setUserId(null);
        criteria.setGroupIds(Arrays.asList("group#1"));
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(1);
        obs.assertValue(m -> m.getMemberType() == MemberType.GROUP && m.getMemberId().equals("group#1"));

        criteria.setUserId("user#1");
        criteria.setGroupIds(Arrays.asList("group#1"));
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoValues();
        obs.assertNoErrors();

        criteria.setUserId("user#1");
        criteria.setGroupIds(Arrays.asList("group#1"));
        criteria.setLogicalOR(true);
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(2);
    }

}
