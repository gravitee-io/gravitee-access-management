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
package io.gravitee.am.gateway.handler.user;

import io.gravitee.am.gateway.handler.user.impl.UserServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.exception.ScopeApprovalNotFoundException;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Set;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    @InjectMocks
    private UserService userService = new UserServiceImpl();

    @Mock
    private Domain domain;

    @Mock
    private ScopeApprovalService scopeApprovalService;

    @Test
    public void shouldFindUserConsents() {
        final String userId = "userId";

        final ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setId("consentId");

        when(scopeApprovalService.findByDomainAndUser(anyString(), eq(userId))).thenReturn(Single.just(Collections.singleton(scopeApproval)));

        TestObserver<Set<ScopeApproval>> testObserver = userService.consents(userId).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopeApprovals -> scopeApprovals.iterator().next().getId().equals("consentId"));
    }

    @Test
    public void shouldFindUserConsent() {
        final ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setId("consentId");

        when(scopeApprovalService.findById(anyString())).thenReturn(Maybe.just(scopeApproval));

        TestObserver<ScopeApproval> testObserver = userService.consent("consentId").test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopeApproval1 -> scopeApproval1.getId().equals("consentId"));
    }

    @Test
    public void shouldNotFindUserConsent_consentNotFound() {
        final ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setId("consentId");

        when(scopeApprovalService.findById(anyString())).thenReturn(Maybe.empty());

        TestObserver<ScopeApproval> testObserver = userService.consent("consentId").test();

        testObserver.assertNotComplete();
        testObserver.assertError(ScopeApprovalNotFoundException.class);
    }

    @Test
    public void shouldRevokeConsents() {
        final String userId = "userId";

        final ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setId("consentId");

        when(scopeApprovalService.revoke(anyString(), eq(userId))).thenReturn(Completable.complete());

        TestObserver testObserver = userService.revokeConsents(userId).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldRevokeConsent() {
        when(scopeApprovalService.revoke(anyString())).thenReturn(Completable.complete());

        TestObserver testObserver = userService.revokeConsent("consentId").test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }
}
