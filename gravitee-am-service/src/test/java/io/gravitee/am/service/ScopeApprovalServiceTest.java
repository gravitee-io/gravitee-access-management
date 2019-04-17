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
package io.gravitee.am.service;

import io.gravitee.am.model.User;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.ScopeApprovalServiceImpl;
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
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ScopeApprovalServiceTest {

    @InjectMocks
    private ScopeApprovalService scopeApprovalService = new ScopeApprovalServiceImpl();

    @Mock
    private ScopeApprovalRepository scopeApprovalRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private UserService userService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(scopeApprovalRepository.findById("my-consent")).thenReturn(Maybe.just(new ScopeApproval()));
        TestObserver testObserver = scopeApprovalService.findById("my-consent").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingScopeApproval() {
        when(scopeApprovalRepository.findById("my-consent")).thenReturn(Maybe.empty());
        TestObserver testObserver = scopeApprovalService.findById("my-consent").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(scopeApprovalRepository.findById("my-consent")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        scopeApprovalService.findById("my-consent").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainAndUser() {
        when(scopeApprovalRepository.findByDomainAndUser(DOMAIN, "userId")).thenReturn(Single.just(Collections.singleton(new ScopeApproval())));
        TestObserver<Set<ScopeApproval>> testObserver = scopeApprovalService.findByDomainAndUser(DOMAIN, "userId").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopeApprovals -> scopeApprovals.size() == 1);
    }

    @Test
    public void shouldFindByDomainAndUser_technicalException() {
        when(scopeApprovalRepository.findByDomainAndUser(DOMAIN, "userId")).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        scopeApprovalService.findByDomainAndUser(DOMAIN, "userId").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainAndUserAndClient() {
        when(scopeApprovalRepository.findByDomainAndUserAndClient(DOMAIN, "userId", "clientId")).thenReturn(Single.just(Collections.singleton(new ScopeApproval())));
        TestObserver<Set<ScopeApproval>> testObserver = scopeApprovalService.findByDomainAndUserAndClient(DOMAIN, "userId", "clientId").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopeApprovals -> scopeApprovals.size() == 1);
    }

    @Test
    public void shouldFindByDomainAndUserAndClient_technicalException() {
        when(scopeApprovalRepository.findByDomainAndUserAndClient(DOMAIN, "userId", "clientId")).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        scopeApprovalService.findByDomainAndUserAndClient(DOMAIN, "userId", "clientId").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_technicalException() {
        when(userService.findById(anyString())).thenReturn(Maybe.just(new User()));
        TestObserver testObserver = scopeApprovalService.revokeByConsent("my-domain","user-id","my-consent").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        when(scopeApprovalRepository.delete("my-consent")).thenReturn(Completable.complete());
        when(scopeApprovalRepository.findById("my-consent")).thenReturn(Maybe.just(new ScopeApproval()));
        when(userService.findById(anyString())).thenReturn(Maybe.just(new User()));

        TestObserver testObserver = scopeApprovalService.revokeByConsent("my-domain","user-id", "my-consent").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(scopeApprovalRepository, times(1)).delete("my-consent");
    }
}
