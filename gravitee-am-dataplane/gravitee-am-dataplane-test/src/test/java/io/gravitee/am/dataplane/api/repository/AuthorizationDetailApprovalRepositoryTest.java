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
package io.gravitee.am.dataplane.api.repository;

import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.ScopeApproval;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthorizationDetailApprovalRepositoryTest extends AbstractDataPlaneTest {

    private static final String DOMAIN = "test-domain";
    private static final String CLIENT = "test-client";

    private final UserId fullUserId = new UserId("user-internal", "user-external", "some-idp");
    private final UserId externalUserId = new UserId(null, "user-external", "some-idp");

    @Autowired
    protected AuthorizationDetailApprovalRepository repository;

    @Test
    public void shouldCreateAndFindAnApproval() {
        Map<String, Object> detail = Map.of("type", "payment_initiation",
                "instructedAmount", Map.of("currency", "EUR", "amount", "10.00"),
                "actions", List.of("initiate", "status"));
        AuthorizationDetailApproval approval = approval(fullUserId, "digest-1", inDays(30));
        approval.setDetail(detail);
        repository.create(approval).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        var found = repository.findByDomainAndUserAndClient(DOMAIN, fullUserId, CLIENT)
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete().assertValueCount(1).values().get(0);

        assertThat(found.getId()).isNotNull();
        assertThat(found.getDigest()).isEqualTo("digest-1");
        assertThat(found.getStatus()).isEqualTo(ScopeApproval.ApprovalStatus.APPROVED);
        assertThat(plain(found.getDetail())).isEqualTo(detail);
        assertThat(found.getUserId()).isEqualTo(fullUserId);
        assertThat(found.getExpiresAt()).isNotNull();
    }

    @Test
    public void shouldFindTheApprovalsOfAnExternalUser() {
        repository.create(approval(fullUserId, "digest-1", inDays(30))).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        repository.findByDomainAndUserAndClient(DOMAIN, externalUserId, CLIENT)
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete().assertValueCount(1);
    }

    @Test
    public void shouldOnlyFindTheApprovalsOfTheClientAndUser() {
        repository.create(approval(fullUserId, "digest-1", inDays(30))).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        AuthorizationDetailApproval otherClient = approval(fullUserId, "digest-2", inDays(30));
        otherClient.setClientId("other-client");
        repository.create(otherClient).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        repository.create(approval(new UserId("other-user", null, null), "digest-3", inDays(30)))
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        var found = repository.findByDomainAndUserAndClient(DOMAIN, fullUserId, CLIENT)
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete().values();

        assertThat(found).extracting(AuthorizationDetailApproval::getDigest).containsExactly("digest-1");
    }

    @Test
    public void shouldNotFindExpiredApprovals() {
        repository.create(approval(fullUserId, "digest-1", inDays(-1))).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        repository.findByDomainAndUserAndClient(DOMAIN, fullUserId, CLIENT)
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoValues();
    }

    @Test
    public void shouldReplaceTheApprovalWithTheSameDigestOnUpsert() {
        AuthorizationDetailApproval first = repository.upsert(approval(fullUserId, "digest-1", inDays(1)))
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete().values().get(0);
        Date laterExpiry = inDays(30);

        AuthorizationDetailApproval denial = approval(fullUserId, "digest-1", laterExpiry);
        denial.setStatus(ScopeApproval.ApprovalStatus.DENIED);
        AuthorizationDetailApproval second = repository.upsert(denial)
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete().values().get(0);

        assertThat(second.getId()).isEqualTo(first.getId());
        var found = repository.findByDomainAndUserAndClient(DOMAIN, fullUserId, CLIENT)
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete().assertValueCount(1).values().get(0);
        assertThat(found.getExpiresAt()).isCloseTo(laterExpiry, 1000);
        assertThat(found.getStatus()).isEqualTo(ScopeApproval.ApprovalStatus.DENIED);
        assertThat(found.getCreatedAt()).isCloseTo(first.getCreatedAt(), 1000);
    }

    @Test
    public void shouldKeepApprovalsWithDifferentDigestsOnUpsert() {
        repository.upsert(approval(fullUserId, "digest-1", inDays(30))).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        repository.upsert(approval(fullUserId, "digest-2", inDays(30))).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        repository.findByDomainAndUserAndClient(DOMAIN, fullUserId, CLIENT)
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete().assertValueCount(2);
    }

    @Test
    public void shouldDeleteTheApprovalsOfADomain() {
        repository.create(approval(fullUserId, "digest-1", inDays(30))).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        AuthorizationDetailApproval otherDomain = approval(fullUserId, "digest-1", inDays(30));
        otherDomain.setDomain("other-domain");
        repository.create(otherDomain).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        repository.deleteByDomain(DOMAIN).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        repository.findByDomainAndUserAndClient(DOMAIN, fullUserId, CLIENT)
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoValues();
        repository.findByDomainAndUserAndClient("other-domain", fullUserId, CLIENT)
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete().assertValueCount(1);
    }

    private static AuthorizationDetailApproval approval(UserId userId, String digest, Date expiresAt) {
        AuthorizationDetailApproval approval = new AuthorizationDetailApproval();
        approval.setDomain(DOMAIN);
        approval.setClientId(CLIENT);
        approval.setUserId(userId);
        approval.setDetail(Map.of("type", "payment_initiation"));
        approval.setDigest(digest);
        approval.setStatus(ScopeApproval.ApprovalStatus.APPROVED);
        approval.setExpiresAt(expiresAt);
        return approval;
    }

    /**
     * Stores may return their own map and list types, which do not equal other maps.
     */
    private static Object plain(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new HashMap<>();
            map.forEach((key, member) -> copy.put(key, plain(member)));
            return copy;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(AuthorizationDetailApprovalRepositoryTest::plain).toList();
        }
        return value;
    }

    private static Date inDays(int days) {
        return Date.from(Instant.now().plus(days, ChronoUnit.DAYS));
    }
}
