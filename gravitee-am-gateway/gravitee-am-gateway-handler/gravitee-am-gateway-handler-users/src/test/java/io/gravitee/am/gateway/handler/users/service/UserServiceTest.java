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
package io.gravitee.am.gateway.handler.users.service;

import io.gravitee.am.gateway.handler.users.service.impl.DomainUserConsentServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.exception.ScopeApprovalNotFoundException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    @InjectMocks
    private DomainUserConsentService userService = new DomainUserConsentServiceImpl();

    @Mock
    private Domain domain;

    @Mock
    private ScopeApprovalService scopeApprovalService;

    @Test
    public void shouldFindUserConsents() {
        final String userId = "userId";
        final String domainId = "domainId";

        final ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setId("consentId");
        scopeApproval.setUserId(UserId.internal(userId));
        scopeApproval.setClientId("");
        scopeApproval.setScope("");

        when(domain.getId()).thenReturn(domainId);
        when(scopeApprovalService.findByDomainAndUser(domainId, UserId.internal(userId))).thenReturn(Flowable.just(scopeApproval));

        TestObserver<Set<ScopeApproval>> testObserver = userService.consents(UserId.internal(userId)).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopeApprovals -> scopeApprovals.iterator().next().getId().equals("consentId"));
    }

    @Test
    public void shouldFindUserConsent() {
        final ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setId("consentId");

        when(scopeApprovalService.findById("consentId")).thenReturn(Maybe.just(scopeApproval));

        TestObserver<ScopeApproval> testObserver = userService.consent("consentId").test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopeApproval1 -> scopeApproval1.getId().equals("consentId"));
    }

    @Test
    public void shouldNotFindUserConsent_consentNotFound() {

        when(scopeApprovalService.findById(anyString())).thenReturn(Maybe.empty());

        userService.consent("consentId").test()
                .assertNotComplete()
                .assertError(ScopeApprovalNotFoundException.class);
    }

    @Test
    public void shouldRevokeConsents() {
        final var userId = UserId.internal("userId");
        final String domainId = "domainId";

        when(scopeApprovalService.revokeByUser(any(Domain.class), any(UserId.class), any())).thenReturn(Completable.complete());

       userService.revokeConsents(userId, null).test()
               .assertComplete()
               .assertNoErrors();
    }

    @Test
    public void shouldRevokeConsent() {
        final String domainId = "domainId";
        final String userId = "userId";
        final String consentId = "consentId";

        when(scopeApprovalService.revokeByConsent(any(Domain.class), any(UserId.class), eq(consentId), any())).thenReturn(Completable.complete());

        TestObserver testObserver = userService.revokeConsent(UserId.internal(userId), consentId, null).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }
}
