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
package io.gravitee.am.repository.gateway.api;

import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.gateway.AbstractGatewayTest;
import io.gravitee.am.repository.jdbc.gateway.api.JdbcScopeApprovalRepository;
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ScopeApprovalRepositoryPurgeTest extends AbstractGatewayTest {
    @Autowired
    private JdbcScopeApprovalRepository scopeApprovalRepository;

    @Test
    public void shouldPurge() {
        Instant now = Instant.now();
        ScopeApproval scope1 = new ScopeApproval();
        scope1.setScope("scope1");
        scope1.setDomain("domain");
        scope1.setUserId("user");
        scope1.setClientId("cli1");
        scope1.setStatus(ScopeApproval.ApprovalStatus.APPROVED);
        scope1.setExpiresAt(new Date(now.plus(1, ChronoUnit.MINUTES).toEpochMilli()));

        ScopeApproval scope2 = new ScopeApproval();
        scope2.setScope("scope2");
        scope2.setDomain("domain");
        scope2.setUserId("user");
        scope2.setClientId("cli2");
        scope2.setStatus(ScopeApproval.ApprovalStatus.APPROVED);
        scope2.setExpiresAt(new Date(now.minus(1, ChronoUnit.MINUTES).toEpochMilli()));

        scopeApprovalRepository.create(scope1).test().awaitDone(10, TimeUnit.SECONDS);
        scopeApprovalRepository.create(scope2).test().awaitDone(10, TimeUnit.SECONDS);

        TestObserver<HashSet<ScopeApproval>> testObserver = scopeApprovalRepository.findByDomainAndUser("domain", "user").collect(HashSet<ScopeApproval>::new, Set::add).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.size() == 1);
        testObserver.assertValue(s -> s.iterator().next().getScope().equals("scope1"));

        TestObserver<Void> testPurge = scopeApprovalRepository.purgeExpiredData().test();
        testPurge.awaitDone(10, TimeUnit.SECONDS);
        testPurge.assertNoErrors();

        testObserver = scopeApprovalRepository.findByDomainAndUser("domain", "user").collect(HashSet<ScopeApproval>::new, Set::add).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.size() == 1);
        testObserver.assertValue(s -> s.iterator().next().getScope().equals("scope1"));

    }

}
