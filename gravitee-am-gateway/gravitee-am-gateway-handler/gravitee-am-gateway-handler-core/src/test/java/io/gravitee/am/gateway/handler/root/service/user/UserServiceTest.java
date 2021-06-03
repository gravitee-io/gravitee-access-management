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
package io.gravitee.am.gateway.handler.root.service.user;

import io.gravitee.am.common.exception.authentication.AccountInactiveException;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.root.service.user.impl.UserServiceImpl;
import io.gravitee.am.gateway.handler.root.service.user.model.ForgotPasswordParameters;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.exception.EnforceUserIdentityException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    @InjectMocks
    private UserService userService = new UserServiceImpl();

    @Mock
    private Domain domain;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private io.gravitee.am.gateway.handler.common.user.UserService commonUserService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private CredentialService credentialService;

    @Mock
    private AuditService auditService;

    @Test
    public void shouldNotResetPassword_userInactive() {
        Client client = mock(Client.class);
        User user = mock(User.class);

        when(user.isInactive()).thenReturn(true);

        TestObserver testObserver = userService.resetPassword(client, user).test();
        testObserver.assertNotComplete();
        testObserver.assertError(AccountInactiveException.class);
    }

    @Test
    public void shouldResetPassword_userInactive_forceRegistration() {
        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(true);
        when(user.getSource()).thenReturn("default-idp");

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn("idp-id");

        AccountSettings accountSettings = mock(AccountSettings.class);
        when(accountSettings.isCompleteRegistrationWhenResetPassword()).thenReturn(true);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));
        when(userProvider.update(anyString(), any())).thenReturn(Single.just(idpUser));

        when(domain.getAccountSettings()).thenReturn(accountSettings);
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        TestObserver testObserver = userService.resetPassword(client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, never()).deleteByUserId(any(), any(), any());
    }

    @Test
    public void shouldResetPassword_userActive() {
        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn("default-idp");

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn("idp-id");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));
        when(userProvider.update(anyString(), any())).thenReturn(Single.just(idpUser));

        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        TestObserver testObserver = userService.resetPassword(client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, never()).deleteByUserId(any(), any(), any());
    }

    @Test
    public void shouldResetPassword_externalIdEmpty() {
        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn("default-idp");

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn("idp-id");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));
        when(userProvider.update(anyString(), any())).thenReturn(Single.just(idpUser));

        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        TestObserver testObserver = userService.resetPassword(client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, never()).deleteByUserId(any(), any(), any());
    }

    @Test
    public void shouldResetPassword_idpUserNotFound() {
        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn("default-idp");

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn("idp-id");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.empty());
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        TestObserver testObserver = userService.resetPassword(client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userProvider, times(1)).create(any());
        verify(credentialService, never()).deleteByUserId(any(), any(), any());
    }

    @Test
    public void shouldNotForgotPassword_userInactive() {
        when(domain.getId()).thenReturn("domain-id");

        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.isInactive()).thenReturn(true);
        when(user.getSource()).thenReturn("idp-id");

        UserProvider userProvider = mock(UserProvider.class);

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()),any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));

        TestObserver testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(AccountInactiveException.class);

        verify(credentialService, never()).deleteByUserId(any(), any(), any());
    }

    @Test
    public void shouldNotForgotPassword_wrongIdp() {
        when(domain.getId()).thenReturn("domain-id");

        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.getSource()).thenReturn("idp-id");

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()),any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.empty());

        TestObserver testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UserInvalidException.class);
    }

    @Test
    public void shouldForgotPassword_userInactive_forceRegistration() {
        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.isInactive()).thenReturn(true);
        when(user.getSource()).thenReturn("idp-id");

        UserProvider userProvider = mock(UserProvider.class);

        AccountSettings accountSettings = mock(AccountSettings.class);
        when(accountSettings.isCompleteRegistrationWhenResetPassword()).thenReturn(true);

        when(domain.getId()).thenReturn("domain-id");
        when(domain.getAccountSettings()).thenReturn(accountSettings);

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()),any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));

        TestObserver testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, never()).deleteByUserId(any(), any(), any());
    }

    @Test
    public void shouldForgotPassword_userActive() {
        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn("idp-id");

        UserProvider userProvider = mock(UserProvider.class);

        when(domain.getId()).thenReturn("domain-id");

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()),any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));

        TestObserver testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

    }

    @Test
    public void shouldForgotPassword_MultipleMatch_NoMultiFieldForm() {
        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.isInactive()).thenReturn(false);
        when(user.getEmail()).thenReturn("test@test.com");

        UserProvider userProvider = mock(UserProvider.class);

        when(domain.getId()).thenReturn("domain-id");
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()),any(FilterCriteria.class))).thenReturn(Single.just(Arrays.asList(user, user)));

        TestObserver testObserver = userService.forgotPassword(
                new ForgotPasswordParameters(user.getEmail(), false, false),
                client,
                mock(io.gravitee.am.identityprovider.api.User.class)).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldNotForgotPassword_MultipleMatch_ConfirmIdentityForm() {
        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");

        UserProvider userProvider = mock(UserProvider.class);

        when(domain.getId()).thenReturn("domain-id");
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()),any(FilterCriteria.class))).thenReturn(Single.just(Arrays.asList(user, user)));

        TestObserver testObserver = userService.forgotPassword(
                new ForgotPasswordParameters(user.getEmail(), true, true),
                client,
                mock(io.gravitee.am.identityprovider.api.User.class)).test();

        testObserver.assertNotComplete();
        testObserver.assertError(EnforceUserIdentityException.class);
    }

    @Test
    public void shouldForgotPassword_userNotFound_fallback_idp() {
        Client client = mock(Client.class);
        when(client.getIdentities()).thenReturn(Collections.singleton("idp-1"));

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);
        when(idpUser.getEmail()).thenReturn("test@test.com");

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByEmail(user.getEmail())).thenReturn(Maybe.just(idpUser));

        when(domain.getId()).thenReturn("domain-id");

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()),any(FilterCriteria.class))).thenReturn(Single.just(Collections.emptyList()));
        when(identityProviderManager.getUserProvider("idp-1")).thenReturn(Maybe.just(userProvider));
        when(commonUserService.create(any())).thenReturn(Single.just(user));

        TestObserver testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldNotForgotPassword_userNotFound_noIdp_client() {
        Client client = mock(Client.class);
        when(client.getIdentities()).thenReturn(Collections.emptySet());

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");

        when(domain.getId()).thenReturn("domain-id");
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()),any(FilterCriteria.class))).thenReturn(Single.just(Collections.emptyList()));

        TestObserver testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);

    }

    @Test
    public void shouldNotForgotPassword_userNotFound_idpNotFound() {
        Client client = mock(Client.class);
        when(client.getIdentities()).thenReturn(Collections.singleton("idp-1"));

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");


        when(domain.getId()).thenReturn("domain-id");

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()),any(FilterCriteria.class))).thenReturn(Single.just(Collections.emptyList()));
        when(identityProviderManager.getUserProvider("idp-1")).thenReturn(Maybe.empty());

        TestObserver testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);

    }

    @Test
    public void shouldNotForgotPassword_userNotFound() {
        Client client = mock(Client.class);
        when(client.getIdentities()).thenReturn(Collections.singleton("idp-1"));

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByEmail(user.getEmail())).thenReturn(Maybe.empty());

        when(domain.getId()).thenReturn("domain-id");

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()),any(FilterCriteria.class))).thenReturn(Single.just(Collections.emptyList()));
        when(identityProviderManager.getUserProvider("idp-1")).thenReturn(Maybe.just(userProvider));

        TestObserver testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);

    }

    @Test
    public void shouldResetPassword_delete_passwordless_devices() {
        AccountSettings accountSettings = mock(AccountSettings.class);
        when(accountSettings.isDeletePasswordlessDevicesAfterResetPassword()).thenReturn(true);

        Client client = mock(Client.class);
        when(client.getAccountSettings()).thenReturn(accountSettings);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn("default-idp");

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn("idp-id");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));
        when(userProvider.update(anyString(), any())).thenReturn(Single.just(idpUser));

        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());
        when(credentialService.deleteByUserId(any(), any(), any())).thenReturn(Completable.complete());

        TestObserver testObserver = userService.resetPassword(client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, times(1)).deleteByUserId(any(), any(), any());
    }
}
