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

package io.gravitee.am.gateway.handler.common.service;


import io.gravitee.am.dataplane.api.repository.LoginAttemptRepository;
import io.gravitee.am.dataplane.api.search.LoginAttemptCriteria;
import io.gravitee.am.gateway.handler.common.service.impl.LoginAttemptGatewayServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.LoginAttempt;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class LoginAttemptGatewayServiceTest {

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    private LoginAttemptGatewayService service;

    @BeforeEach
    public void setUp() {
        when(dataPlaneRegistry.getLoginAttemptRepository(any())).thenReturn(loginAttemptRepository);
        service = new LoginAttemptGatewayServiceImpl(dataPlaneRegistry);
    }

    @Test
    public void shouldCreateUser_accountLockFirstConnection() {
        final LoginAttemptCriteria loginAttemptCriteria = new LoginAttemptCriteria.Builder()
                .client("client-1")
                .domain("domain-1")
                .username("user-1")
                .identityProvider("idp-1")
                .build();

        final LoginAttempt loginAttempt = new LoginAttempt();
        loginAttempt.setAttempts(1);

        final AccountSettings accountSettings = new AccountSettings();
        accountSettings.setLoginAttemptsDetectionEnabled(true);
        accountSettings.setMaxLoginAttempts(1);
        accountSettings.setAccountBlockedDuration(24 * 60 * 60 * 1000);

        when(loginAttemptRepository.findByCriteria(loginAttemptCriteria)).thenReturn(Maybe.just(loginAttempt));
        when(loginAttemptRepository.update(loginAttempt)).thenReturn(Single.just(loginAttempt));

        TestObserver testObserver = service.loginFailed(new Domain("domain-1"), loginAttemptCriteria, accountSettings).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldUpdateUser_accountLockAlreadyRegistered() {
        final LoginAttemptCriteria loginAttemptCriteria = new LoginAttemptCriteria.Builder()
                .client("client-1")
                .domain("domain-1")
                .username("user-1")
                .identityProvider("idp-1")
                .build();

        final LoginAttempt loginAttempt = new LoginAttempt();
        loginAttempt.setAttempts(1);

        final AccountSettings accountSettings = new AccountSettings();
        accountSettings.setLoginAttemptsDetectionEnabled(true);
        accountSettings.setMaxLoginAttempts(1);
        accountSettings.setAccountBlockedDuration(24 * 60 * 60 * 1000);

        when(loginAttemptRepository.findByCriteria(loginAttemptCriteria)).thenReturn(Maybe.just(loginAttempt));
        when(loginAttemptRepository.update(loginAttempt)).thenReturn(Single.just(loginAttempt));

        TestObserver testObserver = service.loginFailed(new Domain("domain-1"), loginAttemptCriteria, accountSettings).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
    }

    @Test
    public void should_reset_login_attempt() {
        final LoginAttemptCriteria loginAttemptCriteria = new LoginAttemptCriteria.Builder()
                .client("client-1")
                .domain("domain-1")
                .username("user-1")
                .identityProvider("idp-1")
                .build();

        when(loginAttemptRepository.delete(any(LoginAttemptCriteria.class))).thenReturn(Completable.complete());

        TestObserver testObserver = service.reset(new Domain("domain-1"), loginAttemptCriteria).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();

        verify(loginAttemptRepository).delete(any(LoginAttemptCriteria.class));
    }

    @Test
    public void should_propagate_reset_exception() throws Exception {
        final LoginAttemptCriteria loginAttemptCriteria = new LoginAttemptCriteria.Builder()
                .client("client-1")
                .domain("domain-1")
                .username("user-1")
                .identityProvider("idp-1")
                .build();

        when(loginAttemptRepository.delete(any(LoginAttemptCriteria.class))).thenReturn(Completable.error(new RuntimeException("test")));

        var testObserver = service.reset(new Domain("domain-1"), loginAttemptCriteria).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertError(throwable -> throwable.getCause().getMessage().equals("test"));

        verify(loginAttemptRepository).delete(any(LoginAttemptCriteria.class));
    }

    @Test
    public void should_check_account_with_lower_cased_username() {
        final LoginAttemptCriteria mixedCase = new LoginAttemptCriteria.Builder()
                .client("client-1")
                .domain("domain-1")
                .username("John.DOE")
                .identityProvider("idp-1")
                .build();
        final LoginAttempt loginAttempt = new LoginAttempt();
        loginAttempt.setAttempts(3);

        when(loginAttemptRepository.findByCriteria(argThat(c -> c != null && "john.doe".equals(c.username()))))
                .thenReturn(Maybe.just(loginAttempt));

        var testObserver = service.checkAccount(new Domain("domain-1"), mixedCase, new AccountSettings()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertValue(attempt -> attempt.getAttempts() == 3);
    }

    @Test
    public void should_store_login_attempt_with_lower_cased_username() {
        final LoginAttemptCriteria mixedCase = new LoginAttemptCriteria.Builder()
                .client("client-1")
                .domain("domain-1")
                .username("John.DOE")
                .identityProvider("idp-1")
                .build();
        final AccountSettings accountSettings = new AccountSettings();
        accountSettings.setMaxLoginAttempts(3);
        accountSettings.setLoginAttemptsResetTime(60);

        when(loginAttemptRepository.findByCriteria(any())).thenReturn(Maybe.empty());
        when(loginAttemptRepository.create(any())).thenAnswer(i -> Single.just(i.getArgument(0)));

        var testObserver = service.loginFailed(new Domain("domain-1"), mixedCase, accountSettings).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();

        final ArgumentCaptor<LoginAttemptCriteria> lookup = ArgumentCaptor.forClass(LoginAttemptCriteria.class);
        verify(loginAttemptRepository).findByCriteria(lookup.capture());
        assertEquals("john.doe", lookup.getValue().username());

        final ArgumentCaptor<LoginAttempt> created = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(loginAttemptRepository).create(created.capture());
        assertEquals("john.doe", created.getValue().getUsername());
    }

    @Test
    public void should_reset_with_lower_cased_username() {
        final LoginAttemptCriteria mixedCase = new LoginAttemptCriteria.Builder()
                .client("client-1")
                .domain("domain-1")
                .username("John.DOE")
                .identityProvider("idp-1")
                .build();

        when(loginAttemptRepository.delete(any(LoginAttemptCriteria.class))).thenReturn(Completable.complete());

        var testObserver = service.reset(new Domain("domain-1"), mixedCase).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();

        verify(loginAttemptRepository).delete(argThat((LoginAttemptCriteria c) -> "john.doe".equals(c.username())));
    }

    @Test
    public void should_delete_login_attempt_with_lower_cased_username_on_login_success() {
        final LoginAttemptCriteria mixedCase = new LoginAttemptCriteria.Builder()
                .client("client-1")
                .domain("domain-1")
                .username("John.DOE")
                .identityProvider("idp-1")
                .build();

        when(loginAttemptRepository.delete(any(LoginAttemptCriteria.class))).thenReturn(Completable.complete());

        var testObserver = service.loginSucceeded(new Domain("domain-1"), mixedCase).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();

        verify(loginAttemptRepository).delete(argThat((LoginAttemptCriteria c) -> "john.doe".equals(c.username())));
    }
}
