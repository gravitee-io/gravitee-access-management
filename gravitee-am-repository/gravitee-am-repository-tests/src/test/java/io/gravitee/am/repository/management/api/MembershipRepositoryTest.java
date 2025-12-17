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
import io.gravitee.am.repository.management.test.IncompatibleDataTestUtils;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MembershipRepositoryTest extends AbstractManagementTest {
    public static final String ORGANIZATION_ID = "ORGANIZATIONID123";
    private static final String FUTURE_REFERENCE_TYPE = "FUTURE_REFERENCE_TYPE";
    private static final String FUTURE_MEMBER_TYPE = "FUTURE_MEMBER_TYPE";
    private static final String DEFAULT_ROLE_ID = "role#1";
    private static final String DEFAULT_USER_ID = "user#1";
    private static final String DEFAULT_GROUP_ID = "group#1";
    @Autowired
    private MembershipRepository membershipRepository;

    @Test
    public void testFindById() {

        Membership membership = new Membership();
        membership.setRoleId(DEFAULT_ROLE_ID);
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setMemberType(MemberType.USER);
        membership.setMemberId(DEFAULT_USER_ID);

        Membership createdMembership = membershipRepository.create(membership).blockingGet();

        TestObserver<Membership> obs = membershipRepository.findById(createdMembership.getId()).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertComplete();
        obs.assertValue(m -> m.getId().equals(createdMembership.getId())
                && m.getRoleId().equals(membership.getRoleId())
                && m.getReferenceType() == membership.getReferenceType()
                && m.getReferenceId().equals(membership.getReferenceId())
                && m.getMemberType() == membership.getMemberType()
                && m.getMemberId().equals(membership.getMemberId()));
    }

    @Test
    public void testFindByReference() {

        Membership membership = new Membership();
        membership.setRoleId(DEFAULT_ROLE_ID);
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setMemberType(MemberType.USER);
        membership.setMemberId(DEFAULT_USER_ID);

        Membership createdMembership = membershipRepository.create(membership).blockingGet();

        TestObserver<List<Membership>> obs = membershipRepository.findByReference(ORGANIZATION_ID, ReferenceType.ORGANIZATION).toList().test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertValue(m -> m.size() == 1 && m.get(0).getId().equals(createdMembership.getId()));
    }

    @Test
    public void testFindByMember() {

        Membership membership = new Membership();
        membership.setRoleId(DEFAULT_ROLE_ID);
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setMemberType(MemberType.USER);
        membership.setMemberId(DEFAULT_USER_ID);

        Membership createdMembership = membershipRepository.create(membership).blockingGet();

        TestObserver<List<Membership>> obs = membershipRepository.findByMember(DEFAULT_USER_ID, MemberType.USER).toList().test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertValue(m -> m.size() == 1 && m.get(0).getMemberId().equals(createdMembership.getMemberId()));
    }

    @Test
    public void testFindByReferenceAndMember() {

        Membership membership = new Membership();
        membership.setRoleId(DEFAULT_ROLE_ID);
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setMemberType(MemberType.USER);
        membership.setMemberId(DEFAULT_USER_ID);

        Membership createdMembership = membershipRepository.create(membership).blockingGet();

        TestObserver<Membership> obs = membershipRepository.findByReferenceAndMember(ReferenceType.ORGANIZATION, ORGANIZATION_ID, membership.getMemberType(), membership.getMemberId()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertValue(m -> m.getId().equals(createdMembership.getId()));
    }

    @Test
    public void testFindByReferenceIdAndCriteria() {

        Membership membership = new Membership();
        membership.setRoleId(DEFAULT_ROLE_ID);
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setMemberType(MemberType.USER);
        membership.setMemberId(DEFAULT_USER_ID);

        Membership groupMembership = new Membership();
        groupMembership.setRoleId(DEFAULT_ROLE_ID);
        groupMembership.setReferenceType(ReferenceType.ORGANIZATION);
        groupMembership.setReferenceId(ORGANIZATION_ID);
        groupMembership.setMemberType(MemberType.GROUP);
        groupMembership.setMemberId(DEFAULT_GROUP_ID);

        membershipRepository.create(membership).blockingGet();
        membershipRepository.create(groupMembership).blockingGet();

        MembershipCriteria criteria = new MembershipCriteria();
        TestSubscriber<Membership> obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(2);

        criteria.setUserId(DEFAULT_USER_ID);
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(1);
        obs.assertValue(m -> m.getMemberType() == MemberType.USER && m.getMemberId().equals(DEFAULT_USER_ID));

        criteria.setUserId(null);
        criteria.setGroupIds(Arrays.asList(DEFAULT_GROUP_ID));
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(1);
        obs.assertValue(m -> m.getMemberType() == MemberType.GROUP && m.getMemberId().equals(DEFAULT_GROUP_ID));

        criteria.setUserId(DEFAULT_USER_ID);
        criteria.setGroupIds(Arrays.asList(DEFAULT_GROUP_ID));
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoValues();
        obs.assertNoErrors();

        criteria.setUserId(DEFAULT_USER_ID);
        criteria.setGroupIds(Arrays.asList(DEFAULT_GROUP_ID));
        criteria.setLogicalOR(true);
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(2);
    }

    @Test
    public void testFindByCriteria() {

        Membership membership = new Membership();
        membership.setRoleId(DEFAULT_ROLE_ID);
        membership.setReferenceType(ReferenceType.ORGANIZATION);
        membership.setReferenceId(ORGANIZATION_ID);
        membership.setMemberType(MemberType.USER);
        membership.setMemberId(DEFAULT_USER_ID);

        Membership groupMembership = new Membership();
        groupMembership.setRoleId(DEFAULT_ROLE_ID);
        groupMembership.setReferenceType(ReferenceType.ORGANIZATION);
        groupMembership.setReferenceId(ORGANIZATION_ID);
        groupMembership.setMemberType(MemberType.GROUP);
        groupMembership.setMemberId(DEFAULT_GROUP_ID);

        membershipRepository.create(membership).blockingGet();
        membershipRepository.create(groupMembership).blockingGet();

        MembershipCriteria criteria = new MembershipCriteria();
        TestSubscriber<Membership> obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(2);

        criteria.setUserId(DEFAULT_USER_ID);
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(1);
        obs.assertValue(m -> m.getMemberType() == MemberType.USER && m.getMemberId().equals(DEFAULT_USER_ID));

        criteria.setUserId(null);
        criteria.setGroupIds(Arrays.asList(DEFAULT_GROUP_ID));
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(1);
        obs.assertValue(m -> m.getMemberType() == MemberType.GROUP && m.getMemberId().equals(DEFAULT_GROUP_ID));

        criteria.setUserId(DEFAULT_USER_ID);
        criteria.setGroupIds(Arrays.asList(DEFAULT_GROUP_ID));
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoValues();
        obs.assertNoErrors();

        criteria.setUserId(DEFAULT_USER_ID);
        criteria.setGroupIds(Arrays.asList(DEFAULT_GROUP_ID));
        criteria.setLogicalOR(true);
        obs = membershipRepository.findByCriteria(ReferenceType.ORGANIZATION, ORGANIZATION_ID, criteria).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValueCount(2);
    }

    @Test
    public void testFilterOutProtectedResourceMemberships() throws Exception {
        Membership normalMembership = new Membership();
        normalMembership.setRoleId(DEFAULT_ROLE_ID);
        normalMembership.setReferenceType(ReferenceType.ORGANIZATION);
        normalMembership.setReferenceId(ORGANIZATION_ID);
        normalMembership.setMemberType(MemberType.USER);
        normalMembership.setMemberId(DEFAULT_USER_ID);
        Membership normalMembershipCreated = membershipRepository.create(normalMembership).blockingGet();
        
        insertIncompatibleMembershipDirectly("incompatibleMembership", FUTURE_REFERENCE_TYPE, MemberType.USER.name(), ORGANIZATION_ID);
        insertIncompatibleMembershipDirectly("incompatibleMembership2", ReferenceType.ORGANIZATION.name(), FUTURE_MEMBER_TYPE, ORGANIZATION_ID);
        TestSubscriber<Membership> testObserver = membershipRepository.findByReference(ORGANIZATION_ID, ReferenceType.ORGANIZATION).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        
        List<Membership> memberships = testObserver.values();
        assertTrue("Should return the normal membership", 
                memberships.stream().anyMatch(m -> m.getId().equals(normalMembershipCreated.getId())));
        assertEquals("Should return exactly 1 membership (incompatible ones filtered)", 1, memberships.size());
        assertFalse("Incompatible membership with future referenceType should be filtered out",
                memberships.stream().anyMatch(m -> "incompatibleMembership".equals(m.getMemberId())));
        assertFalse("Incompatible membership with future memberType should be filtered out",
                memberships.stream().anyMatch(m -> "incompatibleMembership2".equals(m.getMemberId())));
    }

    @Test
    public void testFilterOutProtectedResourceMemberships_NoCrash() throws Exception {
        insertIncompatibleMembershipDirectly("testFutureMember", FUTURE_REFERENCE_TYPE, MemberType.USER.name(), "test-id");
        
        TestSubscriber<Membership> testObserver = membershipRepository.findByReference(ORGANIZATION_ID, ReferenceType.ORGANIZATION).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        
        List<Membership> memberships = testObserver.values();
        assertFalse("Membership containing future referenceType should be filtered out",
                memberships.stream().anyMatch(m -> "testFutureMember".equals(m.getMemberId())));
    }
    
    private void insertIncompatibleMembershipDirectly(String memberId, String referenceType, String memberType, String referenceId) throws Exception {
        String repoClassName = membershipRepository.getClass().getSimpleName();
        String repoFullName = membershipRepository.getClass().getName();
        
        if (repoClassName.contains("Mongo") || repoFullName.contains("mongodb")) {
            insertIncompatibleMembershipMongoDB(memberId, referenceType, memberType, referenceId);
        } else if (repoClassName.contains("Jdbc") || repoFullName.contains("jdbc")) {
            insertIncompatibleMembershipJDBC(memberId, referenceType, memberType, referenceId);
        } else {
            throw new UnsupportedOperationException("Unknown repository type: " + repoClassName + " (" + repoFullName + ")");
        }
    }
    
    private void insertIncompatibleMembershipMongoDB(String memberId, String referenceType, String memberType, String referenceId) throws Exception {
        IncompatibleDataTestUtils.insertIncompatibleEntityMongoDB(
            membershipRepository,
            "memberships",
            "io.gravitee.am.repository.mongodb.management.internal.model.MembershipMongo",
            membershipMongo -> {
                ReflectionTestUtils.setField(membershipMongo, "memberId", memberId);
                ReflectionTestUtils.setField(membershipMongo, "memberType", memberType);
                ReflectionTestUtils.setField(membershipMongo, "referenceType", referenceType);
                ReflectionTestUtils.setField(membershipMongo, "referenceId", referenceId);
                ReflectionTestUtils.setField(membershipMongo, "role", DEFAULT_ROLE_ID);
            }
        );
    }
    
    private void insertIncompatibleMembershipJDBC(String memberId, String referenceType, String memberType, String referenceId) throws Exception {
        IncompatibleDataTestUtils.insertIncompatibleEntityJDBC(
            membershipRepository,
            "io.gravitee.am.repository.jdbc.management.api.model.JdbcMembership",
            jdbcMembership -> {
                ReflectionTestUtils.setField(jdbcMembership, "memberId", memberId);
                ReflectionTestUtils.setField(jdbcMembership, "memberType", memberType);
                ReflectionTestUtils.setField(jdbcMembership, "referenceType", referenceType);
                ReflectionTestUtils.setField(jdbcMembership, "referenceId", referenceId);
                ReflectionTestUtils.setField(jdbcMembership, "roleId", DEFAULT_ROLE_ID);
                ReflectionTestUtils.setField(jdbcMembership, "domain", null);
                ReflectionTestUtils.setField(jdbcMembership, "createdAt", null);
                ReflectionTestUtils.setField(jdbcMembership, "updatedAt", null);
            }
        );
    }
}
