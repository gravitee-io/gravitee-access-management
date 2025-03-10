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
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class ScopeApprovalRepositoryTest extends AbstractDataPlaneTest {

    public static final String TEST_DOMAIN = "test-domain";

    private final UserId fullUserId = new UserId("user-internal", "user-external", "some-idp");
    private final UserId externalUserId = new UserId(null, "user-external", "some-idp");
    private final UserId randomUserId = new UserId("blabla", "blabla", "random-4");


    @Autowired
    protected ScopeApprovalRepository repository;

    @Test
    public void shouldCreate() {
        var approval = basicApproval();
        // when
        repository.create(approval)
                .test()
                // then
                .awaitDone(5, TimeUnit.SECONDS)
                .assertValue(x -> x.getId() != null)
                .assertComplete();
    }

    @Test
    public void shouldFindAllByExternalId() {
        givenStandardTestApprovalsExist();
        // when searching by external user id
        var foundApprovals = repository.findByDomainAndUser(TEST_DOMAIN, externalUserId)
                .test()
                // then all matching approvals are returned
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertValueCount(2)
                .values();
        assertThat(foundApprovals).map(ScopeApproval::getUserId).containsExactlyInAnyOrder(fullUserId, externalUserId);
    }

    @Test
    public void shouldFindAllByFullId() {
        givenStandardTestApprovalsExist();
        // when searching by the full user id
        var foundApprovals = repository.findByDomainAndUser(TEST_DOMAIN, fullUserId)
                .test()
                // then all matching approvals are returned
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete()
                .assertValueCount(2)
                .values();
        assertThat(foundApprovals).map(ScopeApproval::getUserId).containsExactlyInAnyOrder(fullUserId, externalUserId);
    }

    @Test
    public void shouldFindNothingByNullId() {
        var nullUserId = new UserId(null, null, null);
        givenStandardTestApprovalsExist();
        // when searching by an empty user id
        assertThatThrownBy(() -> repository.findByDomainAndUser(TEST_DOMAIN, nullUserId)
                .test()
                .awaitDone(1, TimeUnit.SECONDS))
                // an error is thrown
                .isInstanceOf(IllegalArgumentException.class);
   }

    @Test
    public void shouldCreateWithLongClientName(){
        ScopeApproval scopeApproval = basicApproval();
        scopeApproval.setClientId("very-long-client-very-long-client-very-long-client-very-long-client-very-long-client-very-long-client");
        TestObserver<ScopeApproval> testObserver = repository.create(scopeApproval).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    // common test data
    private void givenStandardTestApprovalsExist() {
        List.of(basicApprovalFor(fullUserId),
                        basicApprovalFor(externalUserId),
                        basicApprovalFor(randomUserId))
                .forEach(x -> repository.create(x).blockingSubscribe());
    }

    private ScopeApproval basicApproval() {
        var it = new ScopeApproval();
        it.setId(UUID.randomUUID().toString());
        it.setScope("test-scope");
        it.setDomain(TEST_DOMAIN);
        it.setUserId(UserId.internal(UUID.randomUUID().toString()));
        it.setCreatedAt(Date.from(Instant.now()));
        it.setUpdatedAt(Date.from(Instant.now()));
        it.setClientId("test-client-id");
        it.setExpiresAt(Date.from(Instant.now().plus(10, ChronoUnit.DAYS)));
        it.setStatus(ScopeApproval.ApprovalStatus.APPROVED);
        return it;
    }

    private ScopeApproval basicApprovalFor(UserId userId) {
        var it = basicApproval();
        it.setUserId(userId);
        return it;
    }
}
