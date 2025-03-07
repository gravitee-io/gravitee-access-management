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
package io.gravitee.am.gateway.handler.common.service.mfa;

import io.gravitee.am.gateway.handler.common.service.mfa.impl.VerifyAttemptServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.VerifyAttempt;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.gateway.api.VerifyAttemptRepository;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class VerifyAttemptServiceImplTest {

    @InjectMocks
    private  VerifyAttemptService verifyAttemptService = new VerifyAttemptServiceImpl();

    @Mock
    VerifyAttemptRepository repository;

    @Mock
    AccountSettings accountSettings;
    @Mock
    private Domain domain;

    @Mock
    private Client client;

    @Mock
    User  user;

    private final String userId = "any-user-id";
    private final String factorId= "any-factor-id";

    @Test
    public void checkVerification_when_acc_settings_is_null() {
        when(domain.getAccountSettings()).thenReturn(null);
        final Client client = null;
        TestObserver<VerifyAttempt> observer = verifyAttemptService.checkVerifyAttempt(user, factorId, client, domain).test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertNoValues();
    }

    @Test
    public void checkVerification_when_isMfaChallengeAttemptsDetectionEnabled_is_false() {
        when(domain.getAccountSettings()).thenReturn(accountSettings);
        when(accountSettings.isMfaChallengeAttemptsDetectionEnabled()).thenReturn(false);
        final Client client = null;

        TestObserver<VerifyAttempt> observer = verifyAttemptService.checkVerifyAttempt(user, factorId, client, domain).test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertNoValues();
    }

    @Test
    public void checkVerification_returns_verifyAttempt_currentDate_greaterThan_resetTime() throws ParseException {
        when(client.getAccountSettings()).thenReturn(accountSettings);
        when(accountSettings.isMfaChallengeAttemptsDetectionEnabled()).thenReturn(true);
        when(client.getId()).thenReturn("any-client-id");
        when(accountSettings.getMfaChallengeAttemptsResetTime()).thenReturn(360000);
        final VerifyAttempt verifyAttempt = createVerifyAttempt();
        Date pastDate = getDate("11-06-22");
        verifyAttempt.setUpdatedAt(pastDate);

        verifyAttempt.setAllowRequest(false);
        when(repository.findByCriteria(any())).thenReturn(Maybe.just(verifyAttempt));

        TestObserver<VerifyAttempt> observer = verifyAttemptService.checkVerifyAttempt(user, factorId, client, domain).test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(verifyAttempt1 -> verifyAttempt.getAttempts() == 0);
        observer.assertValue(verifyAttempt1 -> verifyAttempt.isAllowRequest());
    }

    @Test
    public void checkVerification_returns_verifyAttempt_reset_greaterThan_currentDate() throws ParseException {
        when(client.getAccountSettings()).thenReturn(accountSettings);
        when(accountSettings.isMfaChallengeAttemptsDetectionEnabled()).thenReturn(true);
        when(accountSettings.getMfaChallengeMaxAttempts()).thenReturn(5);
        when(accountSettings.getMfaChallengeAttemptsResetTime()).thenReturn(360000);
        when(client.getId()).thenReturn("any-client-id");
        final VerifyAttempt verifyAttempt = createVerifyAttempt();
        verifyAttempt.setAllowRequest(false);
        verifyAttempt.setAttempts(2);
        verifyAttempt.setUpdatedAt(new Date());
        when(repository.findByCriteria(any())).thenReturn(Maybe.just(verifyAttempt));

        TestObserver<VerifyAttempt> observer = verifyAttemptService.checkVerifyAttempt(user, factorId, client, domain).test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(verifyAttempt1 -> verifyAttempt.getAttempts() == 2);
        observer.assertValue(verifyAttempt1 -> verifyAttempt.isAllowRequest());
    }

    @Test
    public void verificationFailed_when_acc_settings_is_null() {
        when(domain.getAccountSettings()).thenReturn(null);
        final Client client = null;
        TestObserver<Void> observer = verifyAttemptService.incrementAttempt(userId, factorId, client, domain, Optional.empty()).test();

        observer.assertComplete();
        observer.assertNoErrors();
    }

    @Test
    public void verificationFailed_when_isMfaChallengeAttemptsDetectionEnabled_is_false() {
        when(domain.getAccountSettings()).thenReturn(accountSettings);
        when(accountSettings.isMfaChallengeAttemptsDetectionEnabled()).thenReturn(false);
        final Client client = null;

        TestObserver<Void> observer = verifyAttemptService.incrementAttempt(userId, factorId, client, domain, Optional.empty()).test();

        observer.assertComplete();
        observer.assertNoErrors();
    }

    @Test
    public void verificationFailed_when_verifyAttempt_is_empty() {
        when(client.getAccountSettings()).thenReturn(accountSettings);
        when(accountSettings.isMfaChallengeAttemptsDetectionEnabled()).thenReturn(true);
        when(repository.create(any())).thenReturn(Single.just(new VerifyAttempt()));

        TestObserver<Void> observer = verifyAttemptService.incrementAttempt(userId, factorId, client, domain, Optional.empty()).test();

        observer.assertComplete();
        observer.assertNoErrors();
        verify(repository, times(1)).create(any());
        verify(repository, never()).update(any());
    }


    @Test
    public void verificationFailed_when_verifyAttempt_is_not_empty() {
        when(client.getAccountSettings()).thenReturn(accountSettings);
        when(accountSettings.isMfaChallengeAttemptsDetectionEnabled()).thenReturn(true);
        when(repository.update(any())).thenReturn(Single.just(new VerifyAttempt()));
        VerifyAttempt verifyAttempt = createVerifyAttempt();

        TestObserver<Void> observer = verifyAttemptService.incrementAttempt(userId, factorId, client, domain, Optional.of(verifyAttempt)).test();

        observer.assertComplete();
        observer.assertNoErrors();
        verify(repository, times(1)).update(any());
        verify(repository, never()).create(any());
    }

    @Test
    public void checkVerification_returns_empty() {
        when(client.getAccountSettings()).thenReturn(accountSettings);
        when(accountSettings.isMfaChallengeAttemptsDetectionEnabled()).thenReturn(true);
        when(repository.findByCriteria(any())).thenReturn(Maybe.empty());

        TestObserver<VerifyAttempt> observer = verifyAttemptService.checkVerifyAttempt(user, factorId, client, domain).test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertNoValues();
    }

    @Test
    public void should_throw_MFAValidationAttemptException() throws ParseException {
        when(client.getAccountSettings()).thenReturn(accountSettings);
        when(accountSettings.isMfaChallengeAttemptsDetectionEnabled()).thenReturn(true);
        when(accountSettings.getMfaChallengeAttemptsResetTime()).thenReturn(360000);
        when(accountSettings.getMfaChallengeMaxAttempts()).thenReturn(2);

        final VerifyAttempt verifyAttempt = createVerifyAttempt();
        Date futureDate = getDate("11-06-29");
        verifyAttempt.setUpdatedAt(futureDate);
        verifyAttempt.setAllowRequest(false);
        verifyAttempt.setAttempts(666);
        when(repository.findByCriteria(any())).thenReturn(Maybe.just(verifyAttempt));

        TestObserver<VerifyAttempt> observer = verifyAttemptService.checkVerifyAttempt(user, factorId, client, domain).test();

        observer.assertNotComplete().assertError(err -> "Maximum verification limit exceed".equals(err.getMessage()));
    }

    private VerifyAttempt createVerifyAttempt() {
        return new VerifyAttempt();
    }

    private Date getDate(String strDate) throws ParseException {
        DateFormat formatter = new SimpleDateFormat("dd-MM-yy");
        return formatter.parse(strDate);
    }
}
