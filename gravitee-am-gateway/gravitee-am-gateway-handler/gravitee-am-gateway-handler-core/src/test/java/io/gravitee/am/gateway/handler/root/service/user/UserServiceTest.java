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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.exception.authentication.AccountInactiveException;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManager;
import io.gravitee.am.gateway.handler.root.service.user.impl.UserServiceImpl;
import io.gravitee.am.gateway.handler.root.service.user.model.ForgotPasswordParameters;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.EnrollmentSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.exception.EnforceUserIdentityException;
import io.gravitee.am.service.exception.PasswordHistoryException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserAlreadyVerifiedException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.validators.email.EmailValidator;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@SuppressWarnings("ALL")
@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    public static final String IDP_ID = "idp-id";
    @InjectMocks
    private UserService userService = new UserServiceImpl();

    @Mock
    private Domain domain;

    @Mock
    private EmailValidator emailValidator;

    @Mock
    private EmailService emailService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private io.gravitee.am.gateway.handler.common.user.UserService commonUserService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private CredentialService credentialService;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuditService auditService;

    @Mock
    private PasswordHistoryService passwordHistoryService;

    @Mock
    private JWTParser jwtParser;

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private UserValidator userValidator;

    @Mock
    private DomainReadService domainService;

    @Mock
    private PasswordPolicyManager passwordPolicyManager;

    @Mock
    private SubjectManager subjectManager;

    @Before
    public void before() {
        doReturn(true).when(emailValidator).validate(anyString());
        when(passwordHistoryService.addPasswordToHistory(any(), any(), any(), any(), any(), any())).thenReturn(Maybe.just(new PasswordHistory()));
        when(domain.getId()).thenReturn("id");
    }

    @Test
    public void resetShouldReturnErrorWhenPasswordAlreadyInHistory() {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(true);
        when(user.getSource()).thenReturn("default-idp");
        when(user.getReferenceId()).thenReturn("id");
        when(user.getReferenceType()).thenReturn(ReferenceType.DOMAIN);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);

        AccountSettings accountSettings = mock(AccountSettings.class);
        when(accountSettings.isCompleteRegistrationWhenResetPassword()).thenReturn(true);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));

        when(domain.getAccountSettings()).thenReturn(accountSettings);
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(passwordHistoryService.addPasswordToHistory(any(), any(), any(), any(), any(), any())).thenReturn(Maybe.error(PasswordHistoryException::passwordAlreadyInHistory));

        var testObserver = userService.resetPassword(mock(Client.class), user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(PasswordHistoryException.class);
    }

    @Test
    public void shouldResetPassword_setForceResetPasswordToFalse() {
        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn("default-idp");
        when(user.getReferenceId()).thenReturn("id");
        when(user.getReferenceType()).thenReturn(ReferenceType.DOMAIN);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn("idp-id");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));
        when(userProvider.updatePassword(any(), any())).thenReturn(Single.just(idpUser));

        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        var testObserver = userService.resetPassword(client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(pr -> pr.getUser().getForceResetPassword().equals(Boolean.FALSE));

        verify(user,times(1)).setForceResetPassword(eq(Boolean.FALSE));
        verify(credentialService, never()).deleteByUserId(any(), any(), any());
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldNotResetPassword_userInactive() {
        Client client = mock(Client.class);
        User user = mock(User.class);

        when(user.isInactive()).thenReturn(true);

        var testObserver = userService.resetPassword(client, user).test();
        testObserver.assertNotComplete();
        testObserver.assertError(AccountInactiveException.class);
    }

    @Test
    public void shouldResetPassword_userInactive_forceRegistration() {
        final String clientIdFromClient = "CLIENT_ID_FROM_CLIENT";

        Client client = mock(Client.class);
        when(client.getId()).thenReturn(clientIdFromClient);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(true);
        when(user.getSource()).thenReturn("default-idp");
        when(user.getReferenceId()).thenReturn("id");
        when(user.getReferenceType()).thenReturn(ReferenceType.DOMAIN);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn(IDP_ID);

        AccountSettings accountSettings = mock(AccountSettings.class);
        when(accountSettings.isCompleteRegistrationWhenResetPassword()).thenReturn(true);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));
        when(userProvider.updatePassword(any(), any())).thenReturn(Single.just(idpUser));

        when(domain.getAccountSettings()).thenReturn(accountSettings);
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        var testObserver = userService.resetPassword(client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, never()).deleteByUserId(any(), any(), any());
        verify(tokenService, never()).deleteByUser(any());
        verify(loginAttemptService).reset(argThat(criteria -> criteria.client().equals(clientIdFromClient)));
    }

    @Test
    public void shouldResetPassword_userActive() {
        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn("default-idp");
        when(user.getReferenceId()).thenReturn("id");
        when(user.getReferenceType()).thenReturn(ReferenceType.DOMAIN);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn(IDP_ID);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));
        when(userProvider.updatePassword(any(), any())).thenReturn(Single.just(idpUser));

        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        var testObserver = userService.resetPassword(client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, never()).deleteByUserId(any(), any(), any());
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldResetPassword_externalIdEmpty() {
        Client client = mock(Client.class);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn("default-idp");
        when(user.getReferenceId()).thenReturn("id");
        when(user.getReferenceType()).thenReturn(ReferenceType.DOMAIN);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn(IDP_ID);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));
        when(userProvider.updatePassword(any(), any())).thenReturn(Single.just(idpUser));

        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        var testObserver = userService.resetPassword(client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, never()).deleteByUserId(any(), any(), any());
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldResetPassword_idpUserNotFound() {
        AccountSettings accountSettings = mock(AccountSettings.class);
        when(accountSettings.isInherited()).thenReturn(false);
        when(accountSettings.isCompleteRegistrationWhenResetPassword()).thenReturn(true);
        Client client = mock(Client.class);
        when(client.getAccountSettings()).thenReturn(accountSettings);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn("default-idp");
        when(user.getReferenceId()).thenReturn("id");
        when(user.getReferenceType()).thenReturn(ReferenceType.DOMAIN);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn(IDP_ID);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.empty());
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        var testObserver = userService.resetPassword(client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userProvider, times(1)).create(any());
        verify(credentialService, never()).deleteByUserId(any(), any(), any());
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldNotForgotPassword_userInactive() {
        when(domain.getId()).thenReturn("domain-id");

        Client client = mock(Client.class);
        ApplicationIdentityProvider applicationIdentityProvider = mock(ApplicationIdentityProvider.class);
        when(applicationIdentityProvider.getIdentity()).thenReturn(IDP_ID);
        TreeSet<ApplicationIdentityProvider> applicationIdentityProviders = new TreeSet<>();
        applicationIdentityProviders.add(applicationIdentityProvider);
        when(client.getIdentityProviders()).thenReturn(applicationIdentityProviders);
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.isInactive()).thenReturn(true);
        when(user.getSource()).thenReturn(IDP_ID);

        UserProvider userProvider = mock(UserProvider.class);

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(AccountInactiveException.class);

        verify(credentialService, never()).deleteByUserId(any(), any(), any());
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldNotForgotPassword_wrongIdp() {
        when(domain.getId()).thenReturn("domain-id");

        Client client = mock(Client.class);
        ApplicationIdentityProvider applicationIdentityProvider = mock(ApplicationIdentityProvider.class);
        when(applicationIdentityProvider.getIdentity()).thenReturn(UUID.randomUUID().toString());
        TreeSet<ApplicationIdentityProvider> applicationIdentityProviders = new TreeSet<>();
        applicationIdentityProviders.add(applicationIdentityProvider);
        when(client.getIdentityProviders()).thenReturn(applicationIdentityProviders);

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.getSource()).thenReturn(IDP_ID);

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);
    }

    @Test
    public void shouldForgotPassword_userInactive_forceRegistration() {
        Client client = mock(Client.class);
        ApplicationIdentityProvider applicationIdentityProvider = mock(ApplicationIdentityProvider.class);
        when(applicationIdentityProvider.getIdentity()).thenReturn(IDP_ID);
        TreeSet<ApplicationIdentityProvider> applicationIdentityProviders = new TreeSet<>();
        applicationIdentityProviders.add(applicationIdentityProvider);
        when(client.getIdentityProviders()).thenReturn(applicationIdentityProviders);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.isInactive()).thenReturn(true);
        when(user.getSource()).thenReturn(IDP_ID);
        when(user.isEnabled()).thenReturn(true);

        UserProvider userProvider = mock(UserProvider.class);

        AccountSettings accountSettings = mock(AccountSettings.class);
        when(accountSettings.isCompleteRegistrationWhenResetPassword()).thenReturn(true);

        when(domain.getId()).thenReturn("domain-id");
        when(domain.getAccountSettings()).thenReturn(accountSettings);

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(userProvider.findByUsername("username")).thenReturn(Maybe.just(new DefaultUser("username")));
        when(commonUserService.update(any())).thenReturn(Single.just(user));

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, never()).deleteByUserId(any(), any(), any());
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldForgotPassword_userActive() {
        Client client = mock(Client.class);
        ApplicationIdentityProvider applicationIdentityProvider = mock(ApplicationIdentityProvider.class);
        when(applicationIdentityProvider.getIdentity()).thenReturn(IDP_ID);
        TreeSet<ApplicationIdentityProvider> applicationIdentityProviders = new TreeSet<>();
        applicationIdentityProviders.add(applicationIdentityProvider);
        when(client.getIdentityProviders()).thenReturn(applicationIdentityProviders);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn(IDP_ID);
        when(user.isEnabled()).thenReturn(true);

        UserProvider userProvider = mock(UserProvider.class);

        when(domain.getId()).thenReturn("domain-id");
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(userProvider.findByUsername("username")).thenReturn(Maybe.just(new DefaultUser("username")));
        when(commonUserService.update(any())).thenReturn(Single.just(user));

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldForgotPassword_userActive_noUpdate() {
        Client client = mock(Client.class);
        ApplicationIdentityProvider applicationIdentityProvider = mock(ApplicationIdentityProvider.class);
        when(applicationIdentityProvider.getIdentity()).thenReturn(IDP_ID);
        TreeSet<ApplicationIdentityProvider> applicationIdentityProviders = new TreeSet<>();
        applicationIdentityProviders.add(applicationIdentityProvider);
        when(client.getIdentityProviders()).thenReturn(applicationIdentityProviders);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn(IDP_ID);
        when(user.isEnabled()).thenReturn(true);

        UserProvider userProvider = mock(UserProvider.class);

        when(domain.getId()).thenReturn("domain-id");
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(userProvider.findByUsername("username")).thenReturn(Maybe.empty());

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(commonUserService, never()).update(any());
        verify(tokenService, never()).deleteByUser(any());

    }

    @Test
    public void shouldForgotPassword_MultipleMatch_NoMultiFieldForm() {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("client-id");
        ApplicationIdentityProvider applicationIdentityProvider = mock(ApplicationIdentityProvider.class);
        when(applicationIdentityProvider.getIdentity()).thenReturn(IDP_ID);
        TreeSet<ApplicationIdentityProvider> applicationIdentityProviders = new TreeSet<>();
        applicationIdentityProviders.add(applicationIdentityProvider);
        when(client.getIdentityProviders()).thenReturn(applicationIdentityProviders);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(false);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.isEnabled()).thenReturn(true);
        when(user.getSource()).thenReturn(IDP_ID);

        UserProvider userProvider = mock(UserProvider.class);

        when(domain.getId()).thenReturn("domain-id");
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Arrays.asList(user, user)));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(userProvider.findByUsername("username")).thenReturn(Maybe.just(new DefaultUser("username")));

        var testObserver = userService.forgotPassword(
                new ForgotPasswordParameters(user.getEmail(), false, false),
                client,
                mock(io.gravitee.am.identityprovider.api.User.class)).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldNotForgotPassword_MultipleMatch_ConfirmIdentityForm() {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("client-id");
        ApplicationIdentityProvider applicationIdentityProvider = mock(ApplicationIdentityProvider.class);
        when(applicationIdentityProvider.getIdentity()).thenReturn(IDP_ID);
        TreeSet<ApplicationIdentityProvider> applicationIdentityProviders = new TreeSet<>();
        applicationIdentityProviders.add(applicationIdentityProvider);
        when(client.getIdentityProviders()).thenReturn(applicationIdentityProviders);

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.getSource()).thenReturn(IDP_ID);
        when(domain.getId()).thenReturn("domain-id");
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Arrays.asList(user, user)));

        var testObserver = userService.forgotPassword(
                new ForgotPasswordParameters(user.getEmail(), true, true),
                client,
                mock(io.gravitee.am.identityprovider.api.User.class)).test();

        testObserver.assertNotComplete();
        testObserver.assertError(EnforceUserIdentityException.class);
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldNotForgotPassword_CustomIdentityForm_onlyEmailMatches() {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("client-id");
        final var sortedSet = new TreeSet<ApplicationIdentityProvider>();
        final var appIdpProvider = new ApplicationIdentityProvider();
        appIdpProvider.setIdentity("idp1");
        sortedSet.add(appIdpProvider);
        when(client.getIdentityProviders()).thenReturn(sortedSet);

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");

        when(domain.getId()).thenReturn("domain-id");
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Arrays.asList()));

        final var mockUserProvider = mock(UserProvider.class);
        when(mockUserProvider.findByEmail(eq(user.getEmail()))).thenReturn(Maybe.just(new DefaultUser()));
        when(mockUserProvider.findByUsername(any())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.just(mockUserProvider));

        TestObserver testObserver = userService.forgotPassword(
                new ForgotPasswordParameters(user.getEmail(), "unknownUsername", true, false),
                client,
                mock(io.gravitee.am.identityprovider.api.User.class)).test();

        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);
        verify(commonUserService, never()).findByDomainAndUsernameAndSource(any(), any(), anyString());
    }

    @Test
    public void shouldNotForgotPassword_CustomIdentityForm_onlyUsernameMatches() {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("client-id");
        final var sortedSet = new TreeSet<ApplicationIdentityProvider>();
        final var appIdpProvider = new ApplicationIdentityProvider();
        appIdpProvider.setIdentity("idp1");
        sortedSet.add(appIdpProvider);
        when(client.getIdentityProviders()).thenReturn(sortedSet);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("test");

        when(domain.getId()).thenReturn("domain-id");
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Arrays.asList()));

        final var mockUserProvider = mock(UserProvider.class);
        when(mockUserProvider.findByEmail(any())).thenReturn(Maybe.empty());
        when(mockUserProvider.findByUsername(any())).thenReturn(Maybe.just(new DefaultUser()));
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.just(mockUserProvider));

        TestObserver testObserver = userService.forgotPassword(
                new ForgotPasswordParameters("unknown@acme.fr", user.getUsername(), true, false),
                client,
                mock(io.gravitee.am.identityprovider.api.User.class)).test();

        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);
        verify(commonUserService, never()).findByDomainAndUsernameAndSource(any(), any(), anyString());
    }


    @Test
    public void shouldNotForgotPassword_CustomIdentityForm_EmailAndUsernameMatch_differentUser() {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("client-id");
        final var sortedSet = new TreeSet<ApplicationIdentityProvider>();
        final var appIdpProvider = new ApplicationIdentityProvider();
        appIdpProvider.setIdentity("idp1");
        sortedSet.add(appIdpProvider);
        when(client.getIdentityProviders()).thenReturn(sortedSet);

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.getUsername()).thenReturn("test");

        when(domain.getId()).thenReturn("domain-id");
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Arrays.asList()));

        final var mockUserProvider = mock(UserProvider.class);
        final var userByEmail = new DefaultUser();
        userByEmail.setId("emailid");
        final var userByUsername = new DefaultUser();
        userByUsername.setId("userid");
        when(mockUserProvider.findByEmail(eq(user.getEmail()))).thenReturn(Maybe.just(userByEmail));
        when(mockUserProvider.findByUsername(any())).thenReturn(Maybe.just(userByUsername));
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.just(mockUserProvider));

        TestObserver testObserver = userService.forgotPassword(
                new ForgotPasswordParameters(user.getEmail(), user.getUsername(), true, false),
                client,
                mock(io.gravitee.am.identityprovider.api.User.class)).test();

        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);
        verify(commonUserService, never()).findByDomainAndUsernameAndSource(any(), any(), anyString());
    }

    @Test
    public void shouldForgotPassword_CustomIdentityForm_EmailAndUsernameMatch() {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("client-id");
        final var sortedSet = new TreeSet<ApplicationIdentityProvider>();
        final var appIdpProvider = new ApplicationIdentityProvider();
        appIdpProvider.setIdentity("idp1");
        sortedSet.add(appIdpProvider);
        when(client.getIdentityProviders()).thenReturn(sortedSet);

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.getUsername()).thenReturn("test");

        when(domain.getId()).thenReturn("domain-id");
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Arrays.asList()));

        final var mockUserProvider = mock(UserProvider.class);
        final var userByEmail = new DefaultUser();
        userByEmail.setId("emailid");
        when(mockUserProvider.findByEmail(eq(user.getEmail()))).thenReturn(Maybe.just(userByEmail));
        when(mockUserProvider.findByUsername(any())).thenReturn(Maybe.just(userByEmail));
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.just(mockUserProvider));

        when(commonUserService.findByDomainAndUsernameAndSource(any(), any(), any())).thenReturn(Maybe.just(user));
        when(commonUserService.update(any())).thenReturn(Single.just(user));

        TestObserver testObserver = userService.forgotPassword(
                new ForgotPasswordParameters(user.getEmail(), user.getUsername(), true, false),
                client,
                mock(io.gravitee.am.identityprovider.api.User.class)).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);

        verify(commonUserService).findByDomainAndUsernameAndSource(any(), any(), any());
        verify(commonUserService).update(any());
    }

    @Test
    public void shouldForgotPassword_MultipleMatch_onlyOne_filtered() throws Exception {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("client-id");
        final String localClientId = "idp-client-id";
        when(client.getIdentityProviders()).thenReturn(getApplicationIdentityProviders(localClientId));

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.getSource()).thenReturn(localClientId);
        User user2 = mock(User.class);
        when(user2.getSource()).thenReturn("other-idp-client-id");
        when(user.isEnabled()).thenReturn(true);

        when(domain.getId()).thenReturn("domain-id");
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Arrays.asList(user, user2)));

        UserProvider userProvider = mock(UserProvider.class);
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(userProvider.findByUsername(any())).thenReturn(Maybe.just(new DefaultUser("username")));
        when(commonUserService.update(any())).thenReturn(Single.just(user));

        TestObserver testObserver = userService.forgotPassword(
                new ForgotPasswordParameters(user.getEmail(), true, true),
                client,
                mock(io.gravitee.am.identityprovider.api.User.class)).test();

        // wait for the email service execution
        Thread.sleep(1000);

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        verify(tokenService, never()).deleteByUser(any());
        verify(emailService).send(any(), any(), any());
        verify(auditService).report(argThat(builder -> {
            final Audit audit = builder.build(new ObjectMapper());
            return audit.getType().equals(EventType.FORGOT_PASSWORD_REQUESTED) && audit.getOutcome().getStatus().equals(Status.SUCCESS);
        }));
    }

    @Test
    public void shouldForgotPassword_MultipleMatch_OneToExclude_ConfirmIdentityForm() throws Exception {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("client-id");
        when(client.getIdentityProviders()).thenReturn(getApplicationIdentityProviders("idp-client-id"));

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.getSource()).thenReturn("idp-client-id");
        User user2 = mock(User.class);
        when(user2.getSource()).thenReturn("other-idp-client-id");

        when(domain.getId()).thenReturn("domain-id");
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Arrays.asList(user, user2, user)));

        TestObserver testObserver = userService.forgotPassword(
                new ForgotPasswordParameters(user.getEmail(), true, true),
                client,
                mock(io.gravitee.am.identityprovider.api.User.class)).test();

        // wait for the email service execution
        Thread.sleep(1000);

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(EnforceUserIdentityException.class);
        verify(tokenService, never()).deleteByUser(any());
        verify(emailService, never()).send(any(), any(), any());
        verify(auditService).report(argThat(builder -> {
            final Audit audit = builder.build(new ObjectMapper());
            return audit.getType().equals(EventType.FORGOT_PASSWORD_REQUESTED) && audit.getOutcome().getStatus().equals(Status.FAILURE);
        }));
    }

    @Test
    public void shouldForgotPassword_user_linked_to_inactive_idp_fallback_on_app_idp() {
        Client client = mock(Client.class);
        when(client.getIdentityProviders()).thenReturn(getApplicationIdentityProviders(IDP_ID));

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);
        when(idpUser.getId()).thenReturn(IDP_ID);
        when(idpUser.getEmail()).thenReturn("test@test.com");
        when(idpUser.getUsername()).thenReturn("idp-username");

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.getUsername()).thenReturn("username");
        when(user.getSource()).thenReturn(UUID.randomUUID().toString());// user not linked to the IDP enabled on the app

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByEmail(user.getEmail())).thenReturn(Maybe.just(idpUser));

        when(domain.getId()).thenReturn("domain-id");

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(List.of(user)));
        when(identityProviderManager.getUserProvider(IDP_ID)).thenReturn(Maybe.just(userProvider));
        when(commonUserService.create(any())).thenReturn(Single.just(user));
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(commonUserService.findByDomainAndExternalIdAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(commonUserService, times(1)).create(any());
        verify(commonUserService, never()).update(any());
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldForgotPassword_userNotFound_fallback_idp() {
        Client client = mock(Client.class);
        when(client.getIdentityProviders()).thenReturn(getApplicationIdentityProviders("idp-1"));

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);
        when(idpUser.getId()).thenReturn(IDP_ID);
        when(idpUser.getEmail()).thenReturn("test@test.com");
        when(idpUser.getUsername()).thenReturn("idp-username");

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByEmail(user.getEmail())).thenReturn(Maybe.just(idpUser));

        when(domain.getId()).thenReturn("domain-id");

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.emptyList()));
        when(identityProviderManager.getUserProvider("idp-1")).thenReturn(Maybe.just(userProvider));
        when(commonUserService.create(any())).thenReturn(Single.just(user));
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(commonUserService.findByDomainAndExternalIdAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(commonUserService, times(1)).create(any());
        verify(commonUserService, never()).update(any());
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldForgotPassword_userNotFound_fallback_idp_update_user() {
        Client client = mock(Client.class);
        when(client.getIdentityProviders()).thenReturn(getApplicationIdentityProviders("idp-1"));

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);
        when(idpUser.getEmail()).thenReturn("test@test.com");
        when(idpUser.getUsername()).thenReturn("idp-username");

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByEmail(user.getEmail())).thenReturn(Maybe.just(idpUser));

        when(domain.getId()).thenReturn("domain-id");

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.emptyList()));
        when(identityProviderManager.getUserProvider("idp-1")).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.just(user));

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(commonUserService, never()).findByDomainAndExternalIdAndSource(anyString(), anyString(), anyString());
        verify(commonUserService, never()).create(any());
        verify(commonUserService, times(1)).update(any());
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldNotForgotPassword_userNotFound_noIdp_client() {
        Client client = mock(Client.class);
        when(client.getIdentityProviders()).thenReturn(new TreeSet<>());

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);
        verify(tokenService, never()).deleteByUser(any());

    }

    @Test
    public void shouldNotForgotPassword_userNotFound_idpNotFound() {
        Client client = mock(Client.class);
        when(client.getIdentityProviders()).thenReturn(getApplicationIdentityProviders("idp-1"));

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");


        when(domain.getId()).thenReturn("domain-id");

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.emptyList()));
        when(identityProviderManager.getUserProvider("idp-1")).thenReturn(Maybe.empty());

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);
        verify(tokenService, never()).deleteByUser(any());

    }

    @Test
    public void shouldNotForgotPassword_userNotFound() {
        Client client = mock(Client.class);
        when(client.getIdentityProviders()).thenReturn(getApplicationIdentityProviders("idp-1"));

        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByEmail(user.getEmail())).thenReturn(Maybe.empty());

        when(domain.getId()).thenReturn("domain-id");

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.emptyList()));
        when(identityProviderManager.getUserProvider("idp-1")).thenReturn(Maybe.just(userProvider));

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);
        verify(tokenService, never()).deleteByUser(any());

    }

    private SortedSet<ApplicationIdentityProvider> getApplicationIdentityProviders(String... identities) {
        var set = new TreeSet<ApplicationIdentityProvider>();
        Arrays.stream(identities).forEach(identity -> {
            var patchAppIdp = new ApplicationIdentityProvider();
            patchAppIdp.setIdentity(identity);
            set.add(patchAppIdp);
        });
        return set;
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
        when(user.getReferenceId()).thenReturn("id");
        when(user.getReferenceType()).thenReturn(ReferenceType.DOMAIN);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn(IDP_ID);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));
        when(userProvider.updatePassword(any(), any())).thenReturn(Single.just(idpUser));

        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());
        when(credentialService.deleteByUserId(any(), any(), any())).thenReturn(Completable.complete());

        var testObserver = userService.resetPassword(client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, times(1)).deleteByUserId(any(), any(), any());
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void shouldResetPassword_Invalidate_Tokens() {
        AccountSettings accountSettings = mock(AccountSettings.class);
        when(accountSettings.isResetPasswordInvalidateTokens()).thenReturn(true);

        Client client = mock(Client.class);
        when(client.getAccountSettings()).thenReturn(accountSettings);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn("default-idp");
        when(user.getReferenceId()).thenReturn("id");
        when(user.getReferenceType()).thenReturn(ReferenceType.DOMAIN);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn(IDP_ID);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));
        when(userProvider.updatePassword(any(), any())).thenReturn(Single.just(idpUser));

        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());
        when(tokenService.deleteByUser(any())).thenReturn(Completable.complete());

        var testObserver = userService.resetPassword(client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(tokenService, times(1)).deleteByUser(any());
        verify(credentialService, never()).deleteByUserId(any(), any(), any());
    }

    @Test
    public void mustSetEnrolSkipTime() {
        Client client = mock(Client.class);
        var enrollmentSettings = new EnrollSettings();
        enrollmentSettings.setForceEnrollment(false);
        enrollmentSettings.setSkipTimeSeconds(7200L);
        var mfaSettings = new MFASettings();
        mfaSettings.setEnroll(enrollmentSettings);
        var user = mock(User.class);

        when(client.getMfaSettings()).thenReturn(mfaSettings);
        when(user.getMfaEnrollmentSkippedAt()).thenReturn(null);

        doReturn(Single.just(user)).when(commonUserService).update(user);

        userService.setMfaEnrollmentSkippedTime(client, user);

        verify(commonUserService, times(1)).update(user);
    }

    @Test
    public void mustNotSetEnrolSkipTime_settingsUserExpiredAlreadySet() {
        Client client = mock(Client.class);
        var enrollmentSettings = new EnrollmentSettings();
        enrollmentSettings.setForceEnrollment(false);
        enrollmentSettings.setSkipTimeSeconds(new Date(System.currentTimeMillis() + 86400L).getTime());
        var mfaSettings = new MFASettings();
        mfaSettings.setEnrollment(enrollmentSettings);

        var user = mock(User.class);

        when(client.getMfaSettings()).thenReturn(mfaSettings);
        when(user.getMfaEnrollmentSkippedAt()).thenReturn(new Date());

        userService.setMfaEnrollmentSkippedTime(client, user);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    public void shouldNotForgotPassword_client_has_no_Idp() {
        Client client = mock(Client.class);
        User user = mock(User.class);

        when(client.getIdentityProviders()).thenReturn(new TreeSet<>());

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);
    }

    @Test
    public void shouldNotForgotPassword_client_Idp_not_match_user_Idp() {
        when(domain.getId()).thenReturn("domain-id");

        Client client = mock(Client.class);
        when(client.getIdentityProviders()).thenReturn(new TreeSet<>());

        User user = mock(User.class);
        when(user.getSource()).thenReturn(IDP_ID);

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));

        ApplicationIdentityProvider applicationIdentityProvider = mock(ApplicationIdentityProvider.class);
        when(applicationIdentityProvider.getIdentity()).thenReturn("some-id");
        TreeSet<ApplicationIdentityProvider> applicationIdentityProviders = new TreeSet<>();
        applicationIdentityProviders.add(applicationIdentityProvider);
        when(client.getIdentityProviders()).thenReturn(applicationIdentityProviders);

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);
    }

    @Test
    public void shouldForgotPassword_client_Idp_match_user_idp() {
        Client client = mock(Client.class);
        UserProvider userProvider = mock(UserProvider.class);
        User user = mock(User.class);
        AccountSettings accountSettings = mock(AccountSettings.class);
        ApplicationIdentityProvider applicationIdentityProvider = mock(ApplicationIdentityProvider.class);
        when(applicationIdentityProvider.getIdentity()).thenReturn(IDP_ID);
        TreeSet<ApplicationIdentityProvider> applicationIdentityProviders = new TreeSet<>();
        applicationIdentityProviders.add(applicationIdentityProvider);

        when(user.getUsername()).thenReturn("username");
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.isInactive()).thenReturn(true);
        when(user.isEnabled()).thenReturn(true);
        when(user.getSource()).thenReturn(IDP_ID);
        when(accountSettings.isCompleteRegistrationWhenResetPassword()).thenReturn(true);
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getAccountSettings()).thenReturn(accountSettings);
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(userProvider.findByUsername("username")).thenReturn(Maybe.just(new DefaultUser("username")));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(client.getIdentityProviders()).thenReturn(applicationIdentityProviders);


        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, never()).deleteByUserId(any(), any(), any());
        verify(tokenService, never()).deleteByUser(any());
    }

    @Test
    public void forgotPasswordShouldFail_userDisabled_RegistrationCompleted() {
        when(domain.getId()).thenReturn("domain-id");
        Client client = mock(Client.class);
        AccountSettings accountSettings = mock(AccountSettings.class);
        ApplicationIdentityProvider applicationIdentityProvider = mock(ApplicationIdentityProvider.class);
        when(applicationIdentityProvider.getIdentity()).thenReturn(IDP_ID);
        TreeSet<ApplicationIdentityProvider> applicationIdentityProviders = new TreeSet<>();
        applicationIdentityProviders.add(applicationIdentityProvider);
        when(client.getIdentityProviders()).thenReturn(applicationIdentityProviders);

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.isEnabled()).thenReturn(false);
        when(user.isInactive()).thenReturn(false);
        when(user.getSource()).thenReturn(IDP_ID);

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));

        UserProvider userProvider = mock(UserProvider.class);
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.just(userProvider));

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(AccountInactiveException.class);
        verify(commonUserService, never()).update(any());
    }

    @Test
    public void forgotPasswordShouldFail_userDisabled_userInactive_forgotPasswordNotConfirmRegister() {
        when(domain.getId()).thenReturn("domain-id");
        Client client = mock(Client.class);
        ApplicationIdentityProvider applicationIdentityProvider = mock(ApplicationIdentityProvider.class);
        when(applicationIdentityProvider.getIdentity()).thenReturn(IDP_ID);
        TreeSet<ApplicationIdentityProvider> applicationIdentityProviders = new TreeSet<>();
        applicationIdentityProviders.add(applicationIdentityProvider);
        when(client.getIdentityProviders()).thenReturn(applicationIdentityProviders);
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("username");
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.isInactive()).thenReturn(true);
        when(user.getSource()).thenReturn(IDP_ID);
        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));

        UserProvider userProvider = mock(UserProvider.class);
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.just(userProvider));

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(AccountInactiveException.class);
        verify(commonUserService, never()).update(any());
    }

    @Test
    public void forgotPasswordShouldFail_userDisabled_userInactive_forgotPasswordAllowConfirmRegister() {
        when(domain.getId()).thenReturn("domain-id");
        Client client = mock(Client.class);
        ApplicationIdentityProvider applicationIdentityProvider = mock(ApplicationIdentityProvider.class);
        when(applicationIdentityProvider.getIdentity()).thenReturn(IDP_ID);
        TreeSet<ApplicationIdentityProvider> applicationIdentityProviders = new TreeSet<>();
        applicationIdentityProviders.add(applicationIdentityProvider);
        when(client.getIdentityProviders()).thenReturn(applicationIdentityProviders);
        final var settings = new AccountSettings();
        settings.setCompleteRegistrationWhenResetPassword(true);
        settings.setInherited(false);
        when(client.getAccountSettings()).thenReturn(settings);
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.isEnabled()).thenReturn(false);
        when(user.isInactive()).thenReturn(true);
        when(user.getSource()).thenReturn(IDP_ID);

        when(commonUserService.findByDomainAndCriteria(eq(domain.getId()), any(FilterCriteria.class))).thenReturn(Single.just(Collections.singletonList(user)));

        UserProvider userProvider = mock(UserProvider.class);
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(userProvider.findByUsername(any())).thenReturn(Maybe.just(new DefaultUser()));
        when(commonUserService.update(any())).thenReturn(Single.just(new User()));

        var testObserver = userService.forgotPassword(user.getEmail(), client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        verify(commonUserService).update(any());
    }

    @Test
    public void shouldCheckPassword() {
        final var provider = mock(AuthenticationProvider.class);
        when(provider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.just(mock(io.gravitee.am.identityprovider.api.User.class)));
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.just(mock(UserProvider.class)));
        when(identityProviderManager.get(any())).thenReturn(Maybe.just(provider));

        final TestObserver<Void> observer = userService.checkPassword(mock(User.class), "oldpassword", mock(io.gravitee.am.identityprovider.api.User.class)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();

        verify(provider).loadUserByUsername(any(EndUserAuthentication.class));
    }

    @Test
    public void shouldCheckPassword_InvalidPassword() {
        final var provider = mock(AuthenticationProvider.class);
        when(provider.loadUserByUsername(any(EndUserAuthentication.class))).thenReturn(Maybe.error(new BadCredentialsException()));
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.just(mock(UserProvider.class)));
        when(identityProviderManager.get(any())).thenReturn(Maybe.just(provider));

        final TestObserver<Void> observer = userService.checkPassword(mock(User.class), "oldpassword", mock(io.gravitee.am.identityprovider.api.User.class)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(BadCredentialsException.class);

        verify(provider).loadUserByUsername(any(EndUserAuthentication.class));
    }

    @Test
    public void shouldNotCheckPassword_NoUserProvider() {
        final var provider = mock(AuthenticationProvider.class);
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.empty());

        final TestObserver<Void> observer = userService.checkPassword(mock(User.class), "oldpassword", mock(io.gravitee.am.identityprovider.api.User.class)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(UserProviderNotFoundException.class);

        verify(provider, never()).loadUserByUsername(any(EndUserAuthentication.class));
    }

    @Test
    public void must_comfirm_registration() {
        final User user = new User();
        user.setId("user-id");
        user.setPreRegistration(true);
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("id");

        final Client client = new Client();
        client.setId("clientId");

        final JWT jwt = new JWT(Map.of(
                "sub", user.getId(),
                "aud", client.getId()
        ));

        when(jwtParser.parse("someToken")).thenReturn(jwt);
        when(commonUserService.findById(jwt.getSub())).thenReturn(Maybe.just(user));
        when(clientSyncService.findById(jwt.getAud())).thenReturn(Maybe.just(client));
        when(clientSyncService.findByClientId(jwt.getAud())).thenReturn(Maybe.empty());
        when(commonUserService.update(user)).thenReturn(Single.just(user));

        var testObserver = userService.confirmVerifyRegistration("someToken").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(userToken -> userToken.getUser().equals(user) && userToken.getUser().isEnabled() && userToken.getClient().equals(client));

        verify(auditService, times(1)).report(any());
    }

    @Test
    public void must_raise_UserAlreadyVerifiedException_when_user_is_registered() {
        final User user = new User();
        user.setId("user-id");
        user.setUsername("username");
        user.setPreRegistration(true);
        user.setRegistrationCompleted(true);

        final Client client = new Client();
        client.setId("clientId");

        final JWT jwt = new JWT(Map.of(
                "sub", user.getId(),
                "aud", client.getId()
        ));

        when(jwtParser.parse("someToken")).thenReturn(jwt);
        when(commonUserService.findById(jwt.getSub())).thenReturn(Maybe.just(user));
        when(clientSyncService.findById(jwt.getAud())).thenReturn(Maybe.just(client));
        when(clientSyncService.findByClientId(jwt.getAud())).thenReturn(Maybe.empty());

        var testObserver = userService.confirmVerifyRegistration("someToken").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(UserAlreadyVerifiedException.class);

        verify(auditService, times(1)).report(any());
    }

    @Test
    public void must_raise_UserAlreadyVerifiedException_when_user_is_not_in_prerigistration() {
        final User user = new User();
        user.setId("user-id");
        user.setUsername("username");
        user.setPreRegistration(false);
        user.setRegistrationCompleted(true);

        final Client client = new Client();
        client.setId("clientId");

        final JWT jwt = new JWT(Map.of(
                "sub", user.getId(),
                "aud", client.getId()
        ));

        when(jwtParser.parse("someToken")).thenReturn(jwt);
        when(commonUserService.findById(jwt.getSub())).thenReturn(Maybe.just(user));
        when(clientSyncService.findById(jwt.getAud())).thenReturn(Maybe.just(client));
        when(clientSyncService.findByClientId(jwt.getAud())).thenReturn(Maybe.empty());

        var testObserver = userService.confirmVerifyRegistration("someToken").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(UserAlreadyVerifiedException.class);

        verify(auditService, times(1)).report(any());
    }


    @Test
    public void must_not_register_user_since_not_valid() {
        when(userValidator.validate(any())).thenReturn(
                Completable.defer(() -> Completable.error(new IllegalArgumentException("User is not valid")))
        );

        when(commonUserService.findByDomainAndUsernameAndSource(any(), anyString(), anyString())).thenReturn(
                Maybe.empty()
        );

        final User user = new User();
        user.setUsername("username");
        user.setPreRegistration(false);
        user.setRegistrationCompleted(true);

        final Client client = new Client();
        client.setId("clientId");

        var testObserver = userService.register(client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(IllegalArgumentException.class);
    }

    @Test
    public void must_not_register_user_since_user_already_exist() {
        when(userValidator.validate(any())).thenReturn(Completable.complete());

        final User user = new User();
        user.setId("user-id");
        user.setUsername("username");
        user.setPreRegistration(false);
        user.setRegistrationCompleted(true);

        when(commonUserService.findByDomainAndUsernameAndSource(any(), anyString(), anyString())).thenReturn(
                Maybe.just(user)
        );

        final Client client = new Client();
        client.setId("clientId");

        var testObserver = userService.register(client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(UserAlreadyExistsException.class);

        verify(auditService, times(1)).report(any());
    }

    @Test
    public void must_not_register_user_since_no_user_provider_exist() {
        when(userValidator.validate(any())).thenReturn(Completable.complete());

        final User user = new User();
        user.setId("user-id");
        user.setUsername("username");
        user.setPreRegistration(false);
        user.setRegistrationCompleted(true);

        when(commonUserService.findByDomainAndUsernameAndSource(any(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());

        final Client client = new Client();
        client.setId("clientId");

        var testObserver = userService.register(client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(UserProviderNotFoundException.class);

        verify(auditService, times(1)).report(any());
    }

    @Test
    public void must_register_user() {
        when(userValidator.validate(any())).thenReturn(Completable.complete());

        final User user = new User();
        user.setId("user-id");
        user.setUsername("username");
        user.setPreRegistration(false);
        user.setRegistrationCompleted(true);
        final DefaultUser idpUser = new DefaultUser(user.getUsername());
        idpUser.setId(user.getId());

        when(commonUserService.findByDomainAndUsernameAndSource(any(), anyString(), anyString())).thenReturn(Maybe.empty());

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.create(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        final Client client = new Client();
        client.setId("clientId");

        var testObserver = userService.register(client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(registrationResponse -> {
            return !registrationResponse.isAutoLogin() && registrationResponse.getUser().equals(user);
        });

        verify(auditService, times(1)).report(any());
    }

    @Test
    public void must_register_user_with_email_registration() {
        when(userValidator.validate(any())).thenReturn(Completable.complete());

        AccountSettings accountSettings = mock(AccountSettings.class);
        when(accountSettings.isSendVerifyRegistrationAccountEmail()).thenReturn(true);
        when(domain.getAccountSettings()).thenReturn(accountSettings);

        final User user = new User();
        user.setId("user-id");
        user.setUsername("username");
        user.setPreRegistration(false);
        user.setRegistrationCompleted(true);
        final DefaultUser idpUser = new DefaultUser(user.getUsername());
        idpUser.setId(user.getId());

        when(commonUserService.findByDomainAndUsernameAndSource(any(), anyString(), anyString())).thenReturn(Maybe.empty());

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.create(any())).thenReturn(Single.just(user));
        when(commonUserService.enhance(any())).thenReturn(Single.just(user));
        final Client client = new Client();
        client.setId("clientId");

        var testObserver = userService.register(client, user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(registrationResponse -> {
            return !registrationResponse.isAutoLogin() && registrationResponse.getUser().equals(user) && !registrationResponse.getUser().isEnabled();
        });

        verify(emailService, times(1)).send(any(), any(), any(), any());
        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldInitializeUserWhenRegistered() {
        when(userValidator.validate(any())).thenReturn(Completable.complete());
        when(commonUserService.findByDomainAndUsernameAndSource(any(), anyString(), anyString()))
                .thenReturn(Maybe.empty());

        final DefaultUser idpUser = new DefaultUser("username");
        idpUser.setId("external-idp-id");
        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));

        final org.mockito.ArgumentCaptor<User> userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        when(commonUserService.create(userCaptor.capture())).thenAnswer(inv -> {
            User toCreate = userCaptor.getValue();
            return Single.just(toCreate);
        });
        when(commonUserService.enhance(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        final Client client = new Client();
        client.setId("clientId");
        final User input = new User();
        input.setUsername("username");
        input.setPassword("raw-password");

        var testObserver = userService.register(client, input).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        final User user = userCaptor.getValue();
        Assertions.assertNull(user.getPassword());
        Assertions.assertEquals("external-idp-id", user.getExternalId());
        Assertions.assertNotNull(user.getSource());
        Assertions.assertEquals(ReferenceType.DOMAIN, user.getReferenceType());
        Assertions.assertEquals("id", user.getReferenceId());
        Assertions.assertTrue(user.isInternal());
        Assertions.assertNotNull(user.getCreatedAt());
        Assertions.assertNotNull(user.getUpdatedAt());
        Assertions.assertEquals(user.getCreatedAt(), user.getUpdatedAt());
        Assertions.assertNotNull(user.getLastPasswordReset());
    }
}
