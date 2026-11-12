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
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest;
import io.gravitee.am.model.oauth2.ScopeApproval;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest.SORT_FIELD_EXPIRES_AT;
import static io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest.SORT_FIELD_STATUS;
import static io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest.SORT_FIELD_TYPE;
import static io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest.SORT_FIELD_UPDATED_AT;
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
    public void shouldFindTheApprovalsOfAUserForEveryClient() {
        repository.create(approval(fullUserId, "digest-1", inDays(30))).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        AuthorizationDetailApproval otherClient = approval(fullUserId, "digest-2", inDays(30));
        otherClient.setClientId("other-client");
        repository.create(otherClient).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        var found = page(fullUserId, cursor(SORT_FIELD_UPDATED_AT, "DESC", null, null), 10);

        assertThat(found.getData()).extracting(AuthorizationDetailApproval::getClientId)
                .containsExactlyInAnyOrder(CLIENT, "other-client");
        assertThat(found.getTotalCount()).isEqualTo(2);
        assertThat(found.getNextCursor()).isNull();
    }

    @Test
    public void shouldFindTheApprovalsOfAnExternalUserForEveryClient() {
        repository.create(approval(fullUserId, "digest-1", inDays(30))).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        assertThat(page(externalUserId, cursor(SORT_FIELD_UPDATED_AT, "DESC", null, null), 10).getData()).hasSize(1);
    }

    @Test
    public void shouldOnlyFindTheApprovalsOfTheUserInTheDomain() {
        repository.create(approval(fullUserId, "digest-1", inDays(30))).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        AuthorizationDetailApproval otherDomain = approval(fullUserId, "digest-2", inDays(30));
        otherDomain.setDomain("other-domain");
        repository.create(otherDomain).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        repository.create(approval(new UserId("other-user", null, null), "digest-3", inDays(30)))
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        var found = page(fullUserId, cursor(SORT_FIELD_UPDATED_AT, "DESC", null, null), 10);

        assertThat(found.getData()).extracting(AuthorizationDetailApproval::getDigest).containsExactly("digest-1");
        assertThat(found.getTotalCount()).isEqualTo(1);
    }

    @Test
    public void shouldNotFindExpiredApprovalsOfAUser() {
        repository.create(approval(fullUserId, "digest-1", inDays(-1))).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        repository.create(approval(fullUserId, "digest-2", inDays(30))).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        var found = page(fullUserId, cursor(SORT_FIELD_UPDATED_AT, "DESC", null, null), 10);

        assertThat(found.getData()).extracting(AuthorizationDetailApproval::getDigest).containsExactly("digest-2");
        assertThat(found.getTotalCount()).isEqualTo(1);
    }

    @Test
    public void shouldFilterTheApprovalsOfAUserByClientAndType() {
        repository.create(approval(fullUserId, "digest-1", inDays(30))).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        AuthorizationDetailApproval otherType = approval(fullUserId, "digest-2", inDays(30));
        otherType.setDetail(Map.of("type", "account_information"));
        repository.create(otherType).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        AuthorizationDetailApproval otherClient = approval(fullUserId, "digest-3", inDays(30));
        otherClient.setClientId("other-client");
        repository.create(otherClient).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();

        var found = page(fullUserId, cursor(SORT_FIELD_UPDATED_AT, "DESC", CLIENT, "payment_initiation"), 10);

        assertThat(found.getData()).extracting(AuthorizationDetailApproval::getDigest).containsExactly("digest-1");
        assertThat(found.getData().get(0).getType()).isEqualTo("payment_initiation");
        assertThat(found.getTotalCount()).isEqualTo(1);
    }

    @Test
    public void shouldMatchTheTypeIgnoringCaseWithWildcards() {
        for (String type : List.of("payment_initiation", "payment_status", "paymentXinitiation", "account_information")) {
            AuthorizationDetailApproval approval = approval(fullUserId, type, inDays(30));
            approval.setDetail(Map.of("type", type));
            repository.create(approval).test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        }

        assertThat(types("PAYMENT_INITIATION")).containsExactly("payment_initiation");
        assertThat(types("payment_*")).containsExactlyInAnyOrder("payment_initiation", "payment_status");
        assertThat(types("*_INFO*")).containsExactly("account_information");
        assertThat(types("*")).hasSize(4);
        assertThat(types("payment")).isEmpty();
    }

    @Test
    public void shouldPageThroughTheApprovalsOfAUserInEverySortOrder() {
        List<AuthorizationDetailApproval> approvals = List.of(
                sortable("id-1", "payment_initiation", ScopeApproval.ApprovalStatus.APPROVED, 5, 40),
                sortable("id-2", "account_information", ScopeApproval.ApprovalStatus.DENIED, 3, 10),
                sortable("id-3", "payment_initiation", ScopeApproval.ApprovalStatus.DENIED, 4, 30),
                sortable("id-4", "credit_transfer", ScopeApproval.ApprovalStatus.APPROVED, 1, 20),
                sortable("id-5", "account_information", ScopeApproval.ApprovalStatus.APPROVED, 2, 50));
        approvals.forEach(approval -> repository.create(approval).test().awaitDone(5, TimeUnit.SECONDS).assertComplete());

        Map<String, Comparator<AuthorizationDetailApproval>> sortFields = Map.of(
                SORT_FIELD_UPDATED_AT, Comparator.comparing(AuthorizationDetailApproval::getUpdatedAt),
                SORT_FIELD_EXPIRES_AT, Comparator.comparing(AuthorizationDetailApproval::getExpiresAt),
                SORT_FIELD_TYPE, Comparator.comparing(AuthorizationDetailApproval::getType),
                SORT_FIELD_STATUS, Comparator.comparing(approval -> approval.getStatus().name()));
        sortFields.forEach((sortField, comparator) -> {
            Comparator<AuthorizationDetailApproval> ascending = comparator.thenComparing(AuthorizationDetailApproval::getId);
            for (String direction : List.of("ASC", "DESC")) {
                List<String> expected = approvals.stream()
                        .sorted("ASC".equals(direction) ? ascending : ascending.reversed())
                        .map(AuthorizationDetailApproval::getId)
                        .toList();

                assertThat(allPages(cursor(sortField, direction, null, null), 2))
                        .as("sorted by %s %s", sortField, direction)
                        .isEqualTo(expected);
            }
        });
    }

    @Test
    public void shouldStartAtTheRequestedPage() {
        for (int i = 1; i <= 5; i++) {
            repository.create(sortable("id-" + i, "payment_initiation", ScopeApproval.ApprovalStatus.APPROVED, 6 - i, i))
                    .test().awaitDone(5, TimeUnit.SECONDS).assertComplete();
        }

        var found = page(fullUserId, AuthorizationDetailApprovalCursorRequest.initialCursor(SORT_FIELD_UPDATED_AT, "ASC", 1, null, null), 2);

        assertThat(found.getData()).extracting(AuthorizationDetailApproval::getId).containsExactly("id-3", "id-4");
        assertThat(found.getTotalCount()).isEqualTo(5);
        assertThat(found.getNextCursor().getPage()).isEqualTo(2);
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

    private CursorPage<AuthorizationDetailApproval, AuthorizationDetailApprovalCursorRequest> page(
            UserId userId, AuthorizationDetailApprovalCursorRequest cursor, int limit) {
        return repository.findByDomainAndUser(DOMAIN, userId, cursor, limit)
                .test().awaitDone(5, TimeUnit.SECONDS).assertComplete().values().get(0);
    }

    private List<String> types(String type) {
        return page(fullUserId, cursor(SORT_FIELD_UPDATED_AT, "DESC", null, type), 10).getData().stream()
                .map(AuthorizationDetailApproval::getType)
                .toList();
    }

    private List<String> allPages(AuthorizationDetailApprovalCursorRequest first, int limit) {
        List<String> ids = new ArrayList<>();
        AuthorizationDetailApprovalCursorRequest cursor = first;
        while (cursor != null) {
            var found = page(fullUserId, cursor, limit);
            found.getData().forEach(approval -> ids.add(approval.getId()));
            cursor = found.getNextCursor();
        }
        return ids;
    }

    private static AuthorizationDetailApprovalCursorRequest cursor(String sortField, String direction, String clientId, String type) {
        return AuthorizationDetailApprovalCursorRequest.initialCursor(sortField, direction, 0, clientId, type);
    }

    private AuthorizationDetailApproval sortable(String id, String type, ScopeApproval.ApprovalStatus status,
                                                 int updatedDaysAgo, int expiresInDays) {
        AuthorizationDetailApproval approval = approval(fullUserId, id, inDays(expiresInDays));
        approval.setId(id);
        approval.setDetail(Map.of("type", type));
        approval.setStatus(status);
        approval.setUpdatedAt(inDays(-updatedDaysAgo));
        return approval;
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
        approval.setUpdatedAt(new Date());
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
