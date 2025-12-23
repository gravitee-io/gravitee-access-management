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

package io.gravitee.am.business;


import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.utils.MovingFactorUtils;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.validators.email.EmailValidatorImpl;
import io.gravitee.am.service.validators.user.UserValidator;
import io.gravitee.am.service.validators.user.UserValidatorImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.service.validators.email.EmailValidatorImpl.EMAIL_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.NAME_LAX_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.NAME_STRICT_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.USERNAME_PATTERN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class UpdateUsernameRuleTest {

    public static final String DOMAIN_ID = "domain#1";
    public static final String PASSWORD = "password";
    public static final String NEW_USERNAME = "newUsername";
    public static final String USERNAME = "username";

    private UpdateUsernameRule cut;

    @Mock
    private io.gravitee.am.service.UserService commonUserService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private CredentialService credentialService;

    @Mock
    private AuditService auditService;

    @Mock
    private UserProvider userProvider;

    private UpdateUsernameRule rule;

    private UserValidator userValidator = new UserValidatorImpl(
            NAME_STRICT_PATTERN,
            NAME_LAX_PATTERN,
            USERNAME_PATTERN,
            new EmailValidatorImpl(EMAIL_PATTERN, true)
    );

    @BeforeEach
    public void initRule() {
        this.rule = new UpdateUsernameRule(userValidator, commonUserService, auditService, credentialService, loginAttemptService);
    }

    @Test
    void must_not_reset_username_with_existing_username() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setReferenceId(domain.getId());
        user.setReferenceType(DOMAIN);

        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.just(user));

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.just(userProvider), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidUserException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    void must_not_reset_username_user_provider_does_not_exist() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setReferenceId(domain.getId());
        user.setReferenceType(DOMAIN);

        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.empty());

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.error(new UserProviderNotFoundException("")), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(UserProviderNotFoundException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    void must_not_reset_username_does_not_exist() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setReferenceId(domain.getId());
        user.setReferenceType(DOMAIN);

        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.empty());
        final UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.error(new UserNotFoundException("Could not find user")));

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.just(userProvider), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(UserNotFoundException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    void must_not_reset_username_error_when_updating() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setReferenceId(domain.getId());
        user.setReferenceType(DOMAIN);

        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.empty());

        final UserProvider userProvider = mock(UserProvider.class);
        final DefaultUser defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");
        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(any(), anyString())).thenReturn(Single.error(new InvalidUserException("Could not update find user")));

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.just(userProvider), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidUserException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    void must_not_reset_username_when_userProvider_reports_existing_username() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setReferenceId(domain.getId());
        user.setReferenceType(DOMAIN);

        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.empty());

        final UserProvider userProvider = mock(UserProvider.class);
        final DefaultUser defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");

        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(any(), anyString())).thenReturn(Single.error(new UserAlreadyExistsException(USERNAME)));

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.just(userProvider), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidUserException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    void must_rollback_username_if_userService_fails() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setFactors(List.of());
        user.setReferenceId(domain.getId());
        user.setReferenceType(DOMAIN);

        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.empty());
        when(commonUserService.update(user)).thenReturn(Single.error(new TechnicalManagementException("an unexpected error has occurred")));

        final UserProvider userProvider = mock(UserProvider.class);

        final var defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");
        final var idpUserUpdated = new DefaultUser(NEW_USERNAME);
        defaultUser.setId("idp-user-id");

        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(any(), anyString())).thenReturn(Single.just(idpUserUpdated));

        var credential = new Credential();
        credential.setUsername(user.getUsername());

        when(credentialService.findByUsername(any(), anyString(), eq(user.getUsername()))).thenReturn(Flowable.just(credential));
        when(credentialService.update(credential)).thenReturn(Single.just(credential));

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.just(userProvider), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(TechnicalManagementException.class);

        verify(commonUserService, times(1)).update(any());
        verify(userProvider, times(2)).updateUsername(any(), anyString());

        verify(credentialService, times(2)).findByUsername(any(), anyString(), eq(USERNAME));
        verify(credentialService, times(2)).update(any());
    }

    @Test
    void must_reset_username() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setFactors(List.of());
        user.setReferenceId(domain.getId());
        user.setReferenceType(DOMAIN);

        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.empty());
        when(commonUserService.update(user)).thenReturn(Single.just(user));

        final UserProvider userProvider = mock(UserProvider.class);

        final var defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");
        final var idpUserUpdated = new DefaultUser(NEW_USERNAME);
        defaultUser.setId("idp-user-id");

        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(any(), anyString())).thenReturn(Single.just(idpUserUpdated));

        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        when(credentialService.findByUsername(any(), anyString(), eq(user.getUsername()))).thenReturn(Flowable.empty());

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.just(userProvider), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();

        verify(commonUserService, times(1)).update(any());
        verify(userProvider, times(1)).updateUsername(any(), anyString());
        verify(credentialService, times(1)).findByUsername(any(), anyString(), eq(USERNAME));
        verify(credentialService, never()).update(any());
        verify(loginAttemptService, times(1)).reset(any());
    }

    @Test
    void must_update_user_webauth_credential() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setFactors(List.of());
        user.setReferenceId(domain.getId());
        user.setReferenceType(DOMAIN);

        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.empty());
        when(commonUserService.update(user)).thenReturn(Single.just(user));

        final UserProvider userProvider = mock(UserProvider.class);

        final var defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");
        final var idpUserUpdated = new DefaultUser(NEW_USERNAME);
        defaultUser.setId("idp-user-id");

        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(any(), anyString())).thenReturn(Single.just(idpUserUpdated));

        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        var credential = new Credential();
        credential.setUsername(user.getUsername());

        when(credentialService.findByUsername(any(), anyString(), eq(user.getUsername()))).thenReturn(Flowable.just(credential));
        when(credentialService.update(credential)).thenReturn(Single.just(credential));

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.just(userProvider), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();

        verify(commonUserService, times(1)).update(any());
        verify(userProvider, times(1)).updateUsername(any(), anyString());
        verify(loginAttemptService, times(1)).reset(any());
        verify(credentialService, times(1)).findByUsername(any(), anyString(), eq(USERNAME));
        verify(credentialService, times(1)).update(argThat(argument -> NEW_USERNAME.equals(argument.getUsername())));
    }

    @Test
    void must_update_user_moving_factor() {
        var domain = new Domain();
        domain.setId("domain");

        var enrolledFactor = new EnrolledFactor();
        enrolledFactor.setFactorId("xxx-xxx-xxx");
        enrolledFactor.setStatus(FactorStatus.ACTIVATED);

        var enrolledFactorSecurity = new EnrolledFactorSecurity();
        enrolledFactorSecurity.putData(FactorDataKeys.KEY_MOVING_FACTOR, MovingFactorUtils.generateInitialMovingFactor("user-id"));

        enrolledFactor.setSecurity(enrolledFactorSecurity);

        var user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setFactors(List.of(enrolledFactor));
        user.setReferenceId(domain.getId());
        user.setReferenceType(DOMAIN);

        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.empty());
        when(commonUserService.update(user)).thenReturn(Single.just(user));

        final UserProvider userProvider = mock(UserProvider.class);

        final var defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");
        final var idpUserUpdated = new DefaultUser(NEW_USERNAME);
        defaultUser.setId("idp-user-id");

        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(any(), anyString())).thenReturn(Single.just(idpUserUpdated));

        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());
        when(credentialService.findByUsername(any(), anyString(), eq(user.getUsername()))).thenReturn(Flowable.empty());

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.just(userProvider), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();

        verify(commonUserService, times(1)).update(any());
        verify(userProvider, times(1)).updateUsername(any(), anyString());
        verify(loginAttemptService, times(1)).reset(any());
        verify(credentialService, times(1)).findByUsername(any(), anyString(), eq(USERNAME));

        assertEquals(1, user.getFactors().size());
        assertNotEquals(
                MovingFactorUtils.generateInitialMovingFactor(user.getUsername()),
                user.getFactors().get(0).getSecurity().getAdditionalData().get(FactorDataKeys.KEY_MOVING_FACTOR)
        );
        assertEquals(
                MovingFactorUtils.generateInitialMovingFactor(user.getId()),
                user.getFactors().get(0).getSecurity().getAdditionalData().get(FactorDataKeys.KEY_MOVING_FACTOR)
        );
    }
}
