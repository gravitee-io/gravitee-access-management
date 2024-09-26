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
package io.gravitee.am.service.impl;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.management.api.PasswordHistoryRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.exception.PasswordHistoryException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.reactivex.rxjava3.core.Flowable.fromIterable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordHistoryServiceTest {

    private static final String REFERENCE_ID = UUID.randomUUID().toString();
    @Mock
    private PasswordHistoryRepository repository;
    @Mock
    private AuditService auditService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private PasswordHistoryService service;
    private User user;
    private String userId;
    private String password;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        password = "Password123";
        user = new User();
        user.setId(userId);
        user.setPassword(password);
        user.setReferenceId(REFERENCE_ID);
        user.setReferenceType(DOMAIN);
    }

    @Test
    @DisplayName("Should create a new password_histories entry with an encrypted password")
    void addPasswordToHistory() {
        PasswordPolicy passwordSettings = getPasswordSettings(5);

        given(repository.findUserHistory(DOMAIN, REFERENCE_ID, userId)).willReturn(fromIterable(List.of()));
        given(repository.create(any(PasswordHistory.class))).willAnswer(a -> Single.just(a.getArgument(0)));
        final String encrypted_password = "encrypted password";
        given(passwordEncoder.encode(password)).willReturn(encrypted_password);


        var testObserver = service
                .addPasswordToHistory(ReferenceType.DOMAIN, REFERENCE_ID, user, password , new DefaultUser(), passwordSettings)
                .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        var encoderCaptor = ArgumentCaptor.forClass(CharSequence.class);
        verify(passwordEncoder).encode(encoderCaptor.capture());
        assertEquals(password, encoderCaptor.getValue());

        var passwordHistoryCaptor = ArgumentCaptor.forClass(PasswordHistory.class);
        verify(repository).create(passwordHistoryCaptor.capture());
        var captured = passwordHistoryCaptor.getValue();
        assertEquals(userId, captured.getUserId());
        assertEquals(DOMAIN, captured.getReferenceType());
        assertEquals(REFERENCE_ID, captured.getReferenceId());
        assertEquals(encrypted_password, captured.getPassword());

        verify(auditService).report(isA(UserAuditBuilder.class));
    }

    @Test
    @DisplayName("Should return error when new password has already been used")
    void rejectUsedPassword() {
        PasswordPolicy passwordSettings = getPasswordSettings(0);

        given(repository.findUserHistory(DOMAIN, REFERENCE_ID, userId)).willReturn(fromIterable(List.of(new PasswordHistory())));
        given(passwordEncoder.matches(any(), any())).willReturn(true);

        var testObserver = service
                .addPasswordToHistory(ReferenceType.DOMAIN, REFERENCE_ID, user, password , new DefaultUser(), passwordSettings)
                .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(PasswordHistoryException.class);
    }

    @Test
    @DisplayName("Should replace oldest entry when history count == settings.oldPasswords")
    void replaceOldestPassword() {
        PasswordPolicy passwordSettings = getPasswordSettings(0);

        List<PasswordHistory> passwordHistories = new ArrayList<>();
        Calendar instance = Calendar.getInstance();
        for (int i = 0; i < 5; i++) {
            var passwordHistory = new PasswordHistory();
            passwordHistory.setId(UUID.randomUUID().toString());
            passwordHistory.setCreatedAt(instance.getTime());
            instance.roll(Calendar.MINUTE, true);
            passwordHistories.add(passwordHistory);
        }

        given(repository.findUserHistory(DOMAIN, REFERENCE_ID, userId)).willReturn(fromIterable(passwordHistories));
        given(repository.delete(any())).willReturn(Completable.complete());
        given(repository.create(any(PasswordHistory.class))).willReturn(Single.just(new PasswordHistory()));
        given(passwordEncoder.encode(password)).willReturn("encrypted password");

        var testObserver = service
                .addPasswordToHistory(ReferenceType.DOMAIN, REFERENCE_ID, user, password , new DefaultUser(), passwordSettings)
                .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        var encoderCaptor = ArgumentCaptor.forClass(CharSequence.class);
        verify(passwordEncoder).encode(encoderCaptor.capture());
        assertEquals(password, encoderCaptor.getValue());

        verify(repository).delete(any());
        var passwordHistoryCaptor = ArgumentCaptor.forClass(PasswordHistory.class);
        verify(repository).create(passwordHistoryCaptor.capture());
        var captured = passwordHistoryCaptor.getValue();
        assertEquals(userId, captured.getUserId());
        assertEquals(DOMAIN, captured.getReferenceType());
        assertEquals(REFERENCE_ID, captured.getReferenceId());

        verify(auditService).report(isA(UserAuditBuilder.class));
    }

    @Test
    @DisplayName("Should throw Technical Management Exception when repo encounters error")
    void findByReferenceError() {
        given(repository.findByReference(any(), any())).willReturn(Flowable.error(IllegalArgumentException::new));

        var testSubscriber = service.findByReference(ReferenceType.DOMAIN, REFERENCE_ID).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);
        testSubscriber.assertError(TechnicalManagementException.class);
    }

    @Test
    @DisplayName("Should return empty when password history disabled in settings")
    void historyDisabled() {
        PasswordPolicy passwordSettings = new PasswordPolicy();
        passwordSettings.setPasswordHistoryEnabled(false);

        var testObserver = service
                .addPasswordToHistory(ReferenceType.DOMAIN, REFERENCE_ID, user, password , new DefaultUser(), passwordSettings)
                .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }

    private PasswordPolicy getPasswordSettings(int oldPasswords) {
        PasswordPolicy passwordSettings = new PasswordPolicy();
        passwordSettings.setPasswordHistoryEnabled(true);
        passwordSettings.setOldPasswords((short) oldPasswords);
        passwordSettings.setReferenceId(REFERENCE_ID);
        passwordSettings.setReferenceType(DOMAIN);
        return passwordSettings;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void checkPasswordInHistory(boolean matches) {
        PasswordPolicy passwordSettings = getPasswordSettings(0);

        given(repository.findUserHistory(DOMAIN, REFERENCE_ID, userId)).willReturn(fromIterable(List.of(new PasswordHistory())));
        given(passwordEncoder.matches(any(), any())).willReturn(matches);

        var testObserver = service
                .passwordAlreadyUsed(ReferenceType.DOMAIN, REFERENCE_ID, userId, password, passwordSettings).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(matches);
    }

    @ParameterizedTest
    @MethodSource
    void checkPasswordReturnsFalseWithNoSettings(PasswordPolicy passwordPolicy) {
        var testObserver = service
                .passwordAlreadyUsed(ReferenceType.DOMAIN, REFERENCE_ID, userId, password, passwordPolicy).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(false);
    }

    @SuppressWarnings("unused")
    static Stream<PasswordPolicy> checkPasswordReturnsFalseWithNoSettings() {
        return Stream.of(null, new PasswordPolicy());
    }
}
