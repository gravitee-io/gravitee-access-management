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
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for the AND combination of userId + roleId in {@link MembershipCriteria}.
 *
 * Covers both {@link MembershipRepository#findByCriteria(ReferenceType, String, MembershipCriteria)}
 * and {@link MembershipRepository#findByCriteria(ReferenceType, MembershipCriteria)}.
 *
 * @author GraviteeSource Team
 */
public class MembershipRepositoryCriteriaTest extends AbstractManagementTest {

    @Autowired
    private MembershipRepository membershipRepository;

    // -------------------------------------------------------------------------
    // findByCriteria(referenceType, referenceId, criteria)
    // -------------------------------------------------------------------------

    @Test
    public void findByCriteria_withReferenceId_userIdAndRoleId_returnsMatch() {
        String refId = "ref-and-" + UUID.randomUUID();
        String userId = "user-" + UUID.randomUUID();
        String roleId = "role-" + UUID.randomUUID();
        String otherRoleId = "role-other-" + UUID.randomUUID();

        create(refId, userId, MemberType.USER, roleId);
        create(refId, userId, MemberType.USER, otherRoleId);  // same user, different role

        MembershipCriteria criteria = new MembershipCriteria(userId).setRoleId(roleId);
        TestSubscriber<Membership> obs = membershipRepository.findByCriteria(ReferenceType.APPLICATION, refId, criteria).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(1);
        obs.assertValue(m -> roleId.equals(m.getRoleId()) && userId.equals(m.getMemberId()));
    }

    @Test
    public void findByCriteria_withReferenceId_userIdAndRoleId_noMatch_wrongRole() {
        String refId = "ref-nomatch-" + UUID.randomUUID();
        String userId = "user-" + UUID.randomUUID();
        String roleId = "role-" + UUID.randomUUID();
        String otherRoleId = "role-other-" + UUID.randomUUID();

        create(refId, userId, MemberType.USER, otherRoleId);  // user exists but with a different role

        MembershipCriteria criteria = new MembershipCriteria(userId).setRoleId(roleId);
        TestSubscriber<Membership> obs = membershipRepository.findByCriteria(ReferenceType.APPLICATION, refId, criteria).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(0);
    }

    @Test
    public void findByCriteria_withReferenceId_userIdAndRoleId_noMatch_wrongUser() {
        String refId = "ref-wronguser-" + UUID.randomUUID();
        String userId = "user-" + UUID.randomUUID();
        String otherUserId = "user-other-" + UUID.randomUUID();
        String roleId = "role-" + UUID.randomUUID();

        create(refId, otherUserId, MemberType.USER, roleId);  // role matches but user differs

        MembershipCriteria criteria = new MembershipCriteria(userId).setRoleId(roleId);
        TestSubscriber<Membership> obs = membershipRepository.findByCriteria(ReferenceType.APPLICATION, refId, criteria).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(0);
    }

    @Test
    public void findByCriteria_withReferenceId_roleIdOnly_returnsAllWithRole() {
        String refId = "ref-roleid-" + UUID.randomUUID();
        String userId1 = "user-a-" + UUID.randomUUID();
        String userId2 = "user-b-" + UUID.randomUUID();
        String roleId = "role-" + UUID.randomUUID();

        create(refId, userId1, MemberType.USER, roleId);
        create(refId, userId2, MemberType.USER, roleId);

        MembershipCriteria criteria = new MembershipCriteria().setRoleId(roleId);
        TestSubscriber<Membership> obs = membershipRepository.findByCriteria(ReferenceType.APPLICATION, refId, criteria).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(2);
    }

    @Test
    public void findByCriteria_withReferenceId_userIdOnly_returnsAllRolesForUser() {
        String refId = "ref-userid-" + UUID.randomUUID();
        String userId = "user-" + UUID.randomUUID();
        String roleId1 = "role-1-" + UUID.randomUUID();
        String roleId2 = "role-2-" + UUID.randomUUID();

        create(refId, userId, MemberType.USER, roleId1);
        create(refId, userId, MemberType.USER, roleId2);

        MembershipCriteria criteria = new MembershipCriteria(userId);
        TestSubscriber<Membership> obs = membershipRepository.findByCriteria(ReferenceType.APPLICATION, refId, criteria).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(2);
    }

    // -------------------------------------------------------------------------
    // findByCriteria(referenceType, criteria) — no referenceId scoping
    // -------------------------------------------------------------------------

    @Test
    public void findByCriteria_noReferenceId_userIdAndRoleId_returnsOnlyMatchAcrossRefs() {
        String refId1 = "ref-multi-a-" + UUID.randomUUID();
        String refId2 = "ref-multi-b-" + UUID.randomUUID();
        String userId = "user-" + UUID.randomUUID();
        String roleId = "role-" + UUID.randomUUID();
        String otherRoleId = "role-other-" + UUID.randomUUID();

        create(refId1, userId, MemberType.USER, roleId);       // should match
        create(refId2, userId, MemberType.USER, otherRoleId);  // same user, wrong role

        MembershipCriteria criteria = new MembershipCriteria(userId).setRoleId(roleId);
        TestSubscriber<Membership> obs = membershipRepository.findByCriteria(ReferenceType.APPLICATION, criteria).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(1);
        obs.assertValue(m -> roleId.equals(m.getRoleId()) && userId.equals(m.getMemberId()) && refId1.equals(m.getReferenceId()));
    }

    @Test
    public void findByCriteria_noReferenceId_userIdAndRoleId_multipleRefs_allMatch() {
        String refId1 = "ref-all-a-" + UUID.randomUUID();
        String refId2 = "ref-all-b-" + UUID.randomUUID();
        String refId3 = "ref-all-c-" + UUID.randomUUID();
        String userId = "user-" + UUID.randomUUID();
        String roleId = "role-" + UUID.randomUUID();
        String otherRoleId = "role-" + UUID.randomUUID();

        create(refId1, userId, MemberType.USER, roleId);
        create(refId2, userId, MemberType.USER, roleId);
        create(refId3, userId, MemberType.USER, otherRoleId);

        MembershipCriteria criteria = new MembershipCriteria(userId).setRoleId(roleId);
        TestSubscriber<Membership> obs = membershipRepository.findByCriteria(ReferenceType.APPLICATION, criteria).test();
        obs.awaitDone(10, TimeUnit.SECONDS);

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValueCount(2);
        obs.values().forEach( m ->
            Assert.assertTrue(refId1.equals(m.getReferenceId()) || refId2.equals(m.getReferenceId()))
        );
    }

    // -------------------------------------------------------------------------
    // helper
    // -------------------------------------------------------------------------

    private Membership create(String referenceId, String memberId, MemberType memberType, String roleId) {
        Membership m = new Membership();
        m.setReferenceType(ReferenceType.APPLICATION);
        m.setReferenceId(referenceId);
        m.setMemberType(memberType);
        m.setMemberId(memberId);
        m.setRoleId(roleId);
        return membershipRepository.create(m).blockingGet();
    }
}
