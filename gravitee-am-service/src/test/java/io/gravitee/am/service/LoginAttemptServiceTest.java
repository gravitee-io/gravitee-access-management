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

import io.gravitee.am.model.LoginAttempt;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.repository.management.api.LoginAttemptRepository;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.gravitee.am.service.impl.LoginAttemptServiceImpl;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginAttemptServiceTest {

    @InjectMocks
    private LoginAttemptService loginAttemptService = new LoginAttemptServiceImpl();

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

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

        TestObserver testObserver = loginAttemptService.loginFailed(loginAttemptCriteria, accountSettings).test();
        testObserver.awaitTerminalEvent();
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

        TestObserver testObserver = loginAttemptService.loginFailed(loginAttemptCriteria, accountSettings).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
    }
}
