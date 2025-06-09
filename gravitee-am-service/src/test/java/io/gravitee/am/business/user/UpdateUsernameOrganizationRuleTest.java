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

package io.gravitee.am.business.user;


import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.User;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.validators.email.EmailValidatorImpl;
import io.gravitee.am.service.validators.user.UserValidator;
import io.gravitee.am.service.validators.user.UserValidatorImpl;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.model.ReferenceType.ORGANIZATION;
import static io.gravitee.am.service.validators.email.EmailValidatorImpl.EMAIL_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.NAME_LAX_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.NAME_STRICT_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.USERNAME_PATTERN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class UpdateUsernameOrganizationRuleTest {

    public static final String ORG_ID = "org#1";
    public static final String PASSWORD = "password";
    public static final String NEW_USERNAME = "newUsername";
    public static final String USERNAME = "username";

    @Mock
    private io.gravitee.am.service.OrganizationUserService commonUserService;

    @Mock
    private AuditService auditService;

    @Mock
    private UserProvider userProvider;

    private UpdateUsernameOrganizationRule rule;

    private UserValidator userValidator = new UserValidatorImpl(
            NAME_STRICT_PATTERN,
            NAME_LAX_PATTERN,
            USERNAME_PATTERN,
            new EmailValidatorImpl(EMAIL_PATTERN, true)
    );

    @BeforeEach
    public void initRule() {
        this.rule = new UpdateUsernameOrganizationRule(userValidator, commonUserService::findByUsernameAndSource, commonUserService::update, auditService);
    }

    @Test
    void must_not_reset_username_with_existing_username() {
        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setReferenceId(ORG_ID);
        user.setReferenceType(ORGANIZATION);

        when(commonUserService.findByUsernameAndSource(any(), eq(NEW_USERNAME), eq(user.getSource())))
                .thenReturn(Maybe.just(user));

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.just(userProvider), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidUserException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    void must_not_reset_username_user_provider_does_not_exist() {
        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setReferenceId(ORG_ID);
        user.setReferenceType(ORGANIZATION);

        when(commonUserService.findByUsernameAndSource(any(), eq(NEW_USERNAME), eq(user.getSource())))
                .thenReturn(Maybe.empty());

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.error(new UserProviderNotFoundException("")), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(UserProviderNotFoundException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    void must_not_reset_username_does_not_exist() {
        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setReferenceId(ORG_ID);
        user.setReferenceType(ORGANIZATION);

        when(commonUserService.findByUsernameAndSource(any(), eq(NEW_USERNAME), eq(user.getSource())))
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
        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setReferenceId(ORG_ID);
        user.setReferenceType(ORGANIZATION);

        when(commonUserService.findByUsernameAndSource(any(), eq(NEW_USERNAME), eq(user.getSource())))
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
    void must_rollback_username_if_userService_fails() {
        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setFactors(List.of());
        user.setReferenceId(ORG_ID);
        user.setReferenceType(ORGANIZATION);

        when(commonUserService.findByUsernameAndSource(any(), eq(NEW_USERNAME), eq(user.getSource())))
                .thenReturn(Maybe.empty());
        when(commonUserService.update(user)).thenReturn(Single.error(new TechnicalManagementException("an unexpected error has occurred")));

        final UserProvider userProvider = mock(UserProvider.class);

        final var defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");
        final var idpUserUpdated = new DefaultUser(NEW_USERNAME);
        defaultUser.setId("idp-user-id");

        when(userProvider.findByUsername(any())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(any(), any())).thenReturn(Single.just(idpUserUpdated));

        var credential = new Credential();
        credential.setUsername(user.getUsername());

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.just(userProvider), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(TechnicalManagementException.class);

        verify(commonUserService, times(1)).update(any());
        verify(userProvider, times(2)).updateUsername(any(), any());
    }

    @Test
    void must_reset_username() {
        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setFactors(List.of());
        user.setReferenceId(ORG_ID);
        user.setReferenceType(ORGANIZATION);

        when(commonUserService.findByUsernameAndSource(any(), eq(NEW_USERNAME), eq(user.getSource())))
                .thenReturn(Maybe.empty());
        when(commonUserService.update(user)).thenReturn(Single.just(user));

        final UserProvider userProvider = mock(UserProvider.class);

        final var defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");
        final var idpUserUpdated = new DefaultUser(NEW_USERNAME);
        defaultUser.setId("idp-user-id");

        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(any(), anyString())).thenReturn(Single.just(idpUserUpdated));

        var observer = rule.updateUsername(NEW_USERNAME, null, (User user1) -> Single.just(userProvider), () -> Single.just(user)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();

        verify(commonUserService, times(1)).update(any());
        verify(userProvider, times(1)).updateUsername(any(), anyString());
    }
}
