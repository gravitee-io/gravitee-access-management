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

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.impl.ScopeApprovalServiceImpl;
import io.gravitee.am.service.reporter.builder.UserConsentAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
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
    private AccessTokenRepository accessTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

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
        ScopeApproval dummyScopeApproval = new ScopeApproval();
        dummyScopeApproval.setUserId("");
        dummyScopeApproval.setClientId("");
        dummyScopeApproval.setScope("");
        when(scopeApprovalRepository.findByDomainAndUser(DOMAIN, "userId")).thenReturn(Flowable.just(dummyScopeApproval));
        TestObserver<HashSet<ScopeApproval>> testObserver = scopeApprovalService.findByDomainAndUser(DOMAIN, "userId").collect(HashSet<ScopeApproval>::new, Set::add).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopeApprovals -> scopeApprovals.size() == 1);
    }

    @Test
    public void shouldFindByDomainAndUser_technicalException() {
        when(scopeApprovalRepository.findByDomainAndUser(DOMAIN, "userId")).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber<ScopeApproval> testSubscriber = scopeApprovalService.findByDomainAndUser(DOMAIN, "userId").test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainAndUserAndClient() {
        ScopeApproval dummyScopeApproval = new ScopeApproval();
        dummyScopeApproval.setUserId("");
        dummyScopeApproval.setClientId("");
        dummyScopeApproval.setScope("");
        when(scopeApprovalRepository.findByDomainAndUserAndClient(DOMAIN, "userId", "clientId")).thenReturn(Flowable.just(dummyScopeApproval));
        TestObserver<HashSet<ScopeApproval>> testObserver = scopeApprovalService.findByDomainAndUserAndClient(DOMAIN, "userId", "clientId").collect(HashSet<ScopeApproval>::new, Set::add).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopeApprovals -> scopeApprovals.size() == 1);
    }

    @Test
    public void shouldFindByDomainAndUserAndClient_technicalException() {
        when(scopeApprovalRepository.findByDomainAndUserAndClient(DOMAIN, "userId", "clientId")).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = scopeApprovalService.findByDomainAndUserAndClient(DOMAIN, "userId", "clientId").test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
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
        when(userService.findById(anyString())).thenReturn(Maybe.just(new User()));
        when(accessTokenRepository.deleteByDomainIdClientIdAndUserId("my-domain", "client-id", "user-id")).thenReturn(Completable.complete());
        when(refreshTokenRepository.deleteByDomainIdClientIdAndUserId("my-domain", "client-id", "user-id")).thenReturn(Completable.complete());

        ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setClientId("client-id");
        scopeApproval.setDomain("my-domain");
        scopeApproval.setUserId("user-id");
        when(scopeApprovalRepository.delete("my-consent")).thenReturn(Completable.complete());
        when(scopeApprovalRepository.findById("my-consent")).thenReturn(Maybe.just(scopeApproval));


        TestObserver testObserver = scopeApprovalService.revokeByConsent("my-domain","user-id", "my-consent").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(scopeApprovalRepository, times(1)).delete("my-consent");
        verify(auditService, times(1)).report(any(UserConsentAuditBuilder.class));
    }

    @Test
    public void shouldRevokeByUser() {
        ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setScope("test");
        scopeApproval.setClientId("client-id");
        scopeApproval.setDomain("my-domain");
        scopeApproval.setUserId("user-id");

        when(userService.findById("user-id")).thenReturn(Maybe.just(new User()));
        when(scopeApprovalRepository.findByDomainAndUser("my-domain", "user-id")).thenReturn(Flowable.just(scopeApproval));
        when(scopeApprovalRepository.deleteByDomainAndUser("my-domain", "user-id")).thenReturn(Completable.complete());
        when(accessTokenRepository.deleteByDomainIdAndUserId("my-domain", "user-id")).thenReturn(Completable.complete());
        when(refreshTokenRepository.deleteByDomainIdAndUserId("my-domain", "user-id")).thenReturn(Completable.complete());

        TestObserver<Void> testObserver = scopeApprovalService.revokeByUser("my-domain", "user-id", new DefaultUser("user-id")).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(auditService, times(1)).report(any(UserConsentAuditBuilder.class));
    }

    @Test
    public void shouldRevokeByUser_UserNotFoundException() {

        when(userService.findById("user-id")).thenReturn(Maybe.empty());

        TestObserver<Void> testObserver = scopeApprovalService.revokeByUser("my-domain", "user-id", new DefaultUser("user-id")).test();
        testObserver.assertError(UserNotFoundException.class);
    }

    @Test
    public void shouldRevokeByUserAndClient() {
        ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setScope("test");
        scopeApproval.setClientId("client-id");
        scopeApproval.setDomain("my-domain");
        scopeApproval.setUserId("user-id");

        when(userService.findById("user-id")).thenReturn(Maybe.just(new User()));
        when(scopeApprovalRepository.findByDomainAndUserAndClient("my-domain", "user-id", "client-id")).thenReturn(Flowable.just(scopeApproval));
        when(scopeApprovalRepository.deleteByDomainAndUserAndClient("my-domain", "user-id", "client-id")).thenReturn(Completable.complete());
        when(accessTokenRepository.deleteByDomainIdClientIdAndUserId("my-domain", "client-id", "user-id")).thenReturn(Completable.complete());
        when(refreshTokenRepository.deleteByDomainIdClientIdAndUserId("my-domain", "client-id", "user-id")).thenReturn(Completable.complete());

        TestObserver<Void> testObserver = scopeApprovalService.revokeByUserAndClient("my-domain", "user-id", "client-id", new DefaultUser("user-id")).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(auditService, times(1)).report(any(UserConsentAuditBuilder.class));
    }

    @Test
    public void shouldRevokeByUserAndClient_UserNotFoundException() {

        when(userService.findById("user-id")).thenReturn(Maybe.empty());

        TestObserver<Void> testObserver = scopeApprovalService.revokeByUserAndClient("my-domain", "user-id", "client-id", new DefaultUser("user-id")).test();
        testObserver.assertError(UserNotFoundException.class);
    }
}
