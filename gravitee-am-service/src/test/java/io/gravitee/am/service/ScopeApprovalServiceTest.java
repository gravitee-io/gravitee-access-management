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

import io.gravitee.am.dataplane.api.repository.ScopeApprovalRepository;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.impl.ScopeApprovalServiceImpl;
import io.gravitee.am.service.reporter.builder.UserConsentAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private DataPlaneRegistry dataPlaneRegistry;
    @Mock
    private UserRepository userRepository;

    private static final String CONSENT_ID = "my-consent";
    private static final String DOMAIN_ID = "my-domain";
    private static final Domain DOMAIN = new Domain(DOMAIN_ID);

    @Before
    public void setUp() throws Exception {
        lenient().when(dataPlaneRegistry.getUserRepository(any())).thenReturn(userRepository);
        lenient().when(dataPlaneRegistry.getScopeApprovalRepository(any())).thenReturn(scopeApprovalRepository);
    }

    @Test
    public void shouldFindById() {
        when(scopeApprovalRepository.findById(CONSENT_ID)).thenReturn(Maybe.just(new ScopeApproval()));
        TestObserver testObserver = scopeApprovalService.findById(DOMAIN, CONSENT_ID).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingScopeApproval() {
        when(scopeApprovalRepository.findById(CONSENT_ID)).thenReturn(Maybe.empty());
        TestObserver testObserver = scopeApprovalService.findById(DOMAIN, CONSENT_ID).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(scopeApprovalRepository.findById(CONSENT_ID)).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        scopeApprovalService.findById(DOMAIN, CONSENT_ID).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainAndUser() {
        ScopeApproval dummyScopeApproval = new ScopeApproval();
        dummyScopeApproval.setUserId(UserId.internal(""));
        dummyScopeApproval.setClientId("");
        dummyScopeApproval.setScope("");
        when(scopeApprovalRepository.findByDomainAndUser(DOMAIN_ID, UserId.internal("userId"))).thenReturn(Flowable.just(dummyScopeApproval));
        TestObserver<HashSet<ScopeApproval>> testObserver = scopeApprovalService.findByDomainAndUser(DOMAIN, UserId.internal("userId")).collect(HashSet<ScopeApproval>::new, Set::add).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopeApprovals -> scopeApprovals.size() == 1);
    }

    @Test
    public void shouldFindByDomainAndUser_technicalException() {
        when(scopeApprovalRepository.findByDomainAndUser(DOMAIN_ID, UserId.internal("userId"))).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber<ScopeApproval> testSubscriber = scopeApprovalService.findByDomainAndUser(DOMAIN, UserId.internal("userId")).test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainAndUserAndClient() {
        ScopeApproval dummyScopeApproval = new ScopeApproval();
        dummyScopeApproval.setUserId(UserId.internal(""));
        dummyScopeApproval.setClientId("");
        dummyScopeApproval.setScope("");
        when(scopeApprovalRepository.findByDomainAndUserAndClient(DOMAIN_ID, UserId.internal("userId"), "clientId")).thenReturn(Flowable.just(dummyScopeApproval));
        TestObserver<HashSet<ScopeApproval>> testObserver = scopeApprovalService.findByDomainAndUserAndClient(DOMAIN, UserId.internal("userId"), "clientId").collect(HashSet<ScopeApproval>::new, Set::add).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopeApprovals -> scopeApprovals.size() == 1);
    }

    @Test
    public void shouldFindByDomainAndUserAndClient_technicalException() {
        when(scopeApprovalRepository.findByDomainAndUserAndClient(DOMAIN_ID, UserId.internal("userId"), "clientId")).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = scopeApprovalService.findByDomainAndUserAndClient(DOMAIN, UserId.internal("userId"), "clientId").test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldDelete_technicalException() {
        when(userRepository.findById(any(UserId.class))).thenReturn(Maybe.just(new User()));
        TestObserver testObserver = scopeApprovalService.revokeByConsent(new Domain(DOMAIN_ID), UserId.internal("user-id"), CONSENT_ID, (Domain, RevokeToken) -> Completable.complete(), null).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        when(userRepository.findById(any(UserId.class))).thenReturn(Maybe.just(new User()));

        ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setClientId("client-id");
        scopeApproval.setDomain(DOMAIN_ID);
        scopeApproval.setUserId(UserId.internal("user-id"));
        when(scopeApprovalRepository.delete(CONSENT_ID)).thenReturn(Completable.complete());
        when(scopeApprovalRepository.findById(CONSENT_ID)).thenReturn(Maybe.just(scopeApproval));


        TestObserver testObserver = scopeApprovalService.revokeByConsent(new Domain(DOMAIN_ID), UserId.internal("user-id"), CONSENT_ID,  (Domain, RevokeToken) -> Completable.complete(), null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(scopeApprovalRepository, times(1)).delete(CONSENT_ID);
        verify(auditService, times(1)).report(any(UserConsentAuditBuilder.class));
    }

    @Test
    public void shouldRevokeByUser() {
        ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setScope("test");
        scopeApproval.setClientId("client-id");
        scopeApproval.setDomain(DOMAIN_ID);
        scopeApproval.setUserId(UserId.internal("user-id"));

        when(userRepository.findById(UserId.internal("user-id"))).thenReturn(Maybe.just(User.builder().id("user-id").build()));
        when(scopeApprovalRepository.findByDomainAndUser(DOMAIN_ID, UserId.internal("user-id"))).thenReturn(Flowable.just(scopeApproval));
        when(scopeApprovalRepository.deleteByDomainAndUser(DOMAIN_ID, UserId.internal("user-id"))).thenReturn(Completable.complete());

        TestObserver<Void> testObserver = scopeApprovalService.revokeByUser(new Domain(DOMAIN_ID), UserId.internal("user-id"), (Domain, RevokeToken) -> Completable.complete(), new DefaultUser("user-id")).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(auditService, times(1)).report(any(UserConsentAuditBuilder.class));
    }

    @Test
    public void shouldRevokeByUser_UserNotFoundException() {

        when(userRepository.findById(UserId.internal("user-id"))).thenReturn(Maybe.empty());

        TestObserver<Void> testObserver = scopeApprovalService.revokeByUser(new Domain(DOMAIN_ID), UserId.internal("user-id"), (Domain, RevokeToken) -> Completable.complete(),  new DefaultUser("user-id")).test();
        testObserver.assertError(UserNotFoundException.class);
    }

    @Test
    public void shouldRevokeByUserAndClient() {
        ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setScope("test");
        scopeApproval.setClientId("client-id");
        scopeApproval.setDomain(DOMAIN_ID);
        scopeApproval.setUserId(UserId.internal("user-id"));
        var user = new User();
        user.setId("user-id");
        when(userRepository.findById(UserId.internal("user-id"))).thenReturn(Maybe.just(user));
        when(scopeApprovalRepository.findByDomainAndUserAndClient(DOMAIN_ID, UserId.internal("user-id"), "client-id")).thenReturn(Flowable.just(scopeApproval));
        when(scopeApprovalRepository.deleteByDomainAndUserAndClient(DOMAIN_ID, UserId.internal("user-id"), "client-id")).thenReturn(Completable.complete());

        TestObserver<Void> testObserver = scopeApprovalService.revokeByUserAndClient(new Domain(DOMAIN_ID), UserId.internal("user-id"), "client-id", (Domain, RevokeToken) -> Completable.complete(), new DefaultUser("user-id")).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(auditService, times(1)).report(any(UserConsentAuditBuilder.class));
    }

    @Test
    public void shouldRevokeByUserAndClient_UserNotFoundException() {

        when(userRepository.findById(UserId.internal("user-id"))).thenReturn(Maybe.empty());

        TestObserver<Void> testObserver = scopeApprovalService.revokeByUserAndClient(new Domain(DOMAIN_ID), UserId.internal("user-id"), "client-id", (Domain, RevokeToken) -> Completable.complete(), new DefaultUser("user-id")).test();
        testObserver.assertError(UserNotFoundException.class);
    }
}
