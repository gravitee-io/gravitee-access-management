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
package io.gravitee.am.repository.oauth2.api;

import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

public class ScopeApprovalRepositoryTest extends AbstractOAuthTest {
    @Autowired
    private ScopeApprovalRepository scopeApprovalRepository;
    @Test
    public void shouldCreateWithLongClientName(){
        ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setScope("Test");
        scopeApproval.setStatus(ScopeApproval.ApprovalStatus.APPROVED);
        scopeApproval.setClientId("very-long-client-very-long-client-very-long-client-very-long-client-very-long-client-very-long-client");
        TestObserver<ScopeApproval> testObserver = scopeApprovalRepository.create(scopeApproval).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }
}
