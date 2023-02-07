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
package io.gravitee.am.management.service;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.service.impl.UserServiceImpl;
import io.gravitee.am.model.*;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.validators.email.EmailValidatorImpl;
import io.gravitee.am.service.validators.user.UserValidator;
import io.gravitee.am.service.validators.user.UserValidatorImpl;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.service.validators.email.EmailValidatorImpl.EMAIL_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@SuppressWarnings("ReactiveStreamsUnusedPublisher")
@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    public static final String DOMAIN_ID = "domain#1";
    public static final String PASSWORD = "password";

    @InjectMocks
    private final UserService userService = new UserServiceImpl();

    @Mock
    private PasswordService passwordService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private AuditService auditService;

    @Mock
    private DomainService domainService;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private io.gravitee.am.service.UserService commonUserService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private RoleService roleService;

    @Mock
    private JWTBuilder jwtBuilder;

    @Mock
    private EmailService emailService;

    @Mock
    private EmailManager emailManager;

    @Mock
    private MembershipService membershipService;

    @Mock
    private TokenService tokenService;

    @Mock
    private PasswordHistoryService passwordHistoryService;

    @Spy
    private UserValidator userValidator = new UserValidatorImpl(
            NAME_STRICT_PATTERN,
            NAME_LAX_PATTERN,
            USERNAME_PATTERN,
            new EmailValidatorImpl(EMAIL_PATTERN)
    );

    @Before
    public void setUp() {
        ((UserServiceImpl) userService).setExpireAfter(24 * 3600);
        when(passwordHistoryService.addPasswordToHistory(any(), any(), any(), any(), any(), any())).thenReturn(Maybe.never());
    }

    @Test
    public void shouldCreateUser_invalid_identity_provider() {
        String domainId = "domain";
        String clientId = "clientId";

        Domain domain = new Domain();
        domain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername("username");
        newUser.setSource("unknown-idp");
        newUser.setPassword("myPassword");
        newUser.setClient(clientId);

        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());

        userService.create(domain, newUser, null)
                .test()
                .assertNotComplete()
                .assertError(UserProviderNotFoundException.class);
        verify(commonUserService, never()).create(any());
    }

    @Test
    public void shouldNotCreateUser_unknown_client() {
        String domainId = "domain";
        String clientId = "clientId";

        Domain domain = new Domain();
        domain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername("username");
        newUser.setSource("idp");
        newUser.setClient(clientId);
        newUser.setPassword("myPassword");

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(mock(UserProvider.class)));
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(applicationService.findById(newUser.getClient())).thenReturn(Maybe.empty());
        when(applicationService.findByDomainAndClientId(domainId, newUser.getClient())).thenReturn(Maybe.empty());

        userService.create(domain, newUser, null)
                .test()
                .assertNotComplete()
                .assertError(ClientNotFoundException.class);
        verify(commonUserService, never()).create(any());
    }

    @Test
    public void shouldNotCreateUser_invalid_client() {
        String domainId = "domain";

        Domain domain = new Domain();
        domain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername("username");
        newUser.setSource("idp");
        newUser.setClient("client");
        newUser.setPassword("MyPassword");

        Application application = mock(Application.class);
        when(application.getDomain()).thenReturn("other-domain");

        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(mock(UserProvider.class)));
        when(applicationService.findById(newUser.getClient())).thenReturn(Maybe.just(application));

        userService.create(domain, newUser, null)
                .test()
                .assertNotComplete()
                .assertError(ClientNotFoundException.class);
        verify(commonUserService, never()).create(any());
    }

    @Test
    public void shouldNotCreateUser_user_already_exists() {
        String domainId = "domain";
        String clientId = "clientId";

        Domain domain = new Domain();
        domain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername("username");
        newUser.setSource("idp");
        newUser.setPassword("MyPassword");
        newUser.setClient(clientId);

        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.just(new User()));

        userService.create(domain, newUser, null)
                .test()
                .assertNotComplete()
                .assertError(UserAlreadyExistsException.class);
        verify(commonUserService, never()).create(any());
    }

    @Test
    public void shouldPreRegisterUser() throws InterruptedException {

        String domainId = "domain";

        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setDynamicUserRegistration(false);

        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAccountSettings(accountSettings);

        NewUser newUser = new NewUser();
        newUser.setUsername("username");
        newUser.setSource("idp");
        newUser.setClient("client");
        newUser.setPreRegistration(true);

        User preRegisteredUser = new User();
        preRegisteredUser.setId("userId");
        preRegisteredUser.setReferenceId("domain");
        preRegisteredUser.setPreRegistration(true);

        UserProvider userProvider = mock(UserProvider.class);
        doReturn(Single.just(new DefaultUser(newUser.getUsername()))).when(userProvider).create(any());

        Application client = new Application();
        client.setDomain("domain");
        when(domainService.findById(domainId)).thenReturn(Maybe.just(domain));
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(applicationService.findById(newUser.getClient())).thenReturn(Maybe.just(client));
        when(commonUserService.create(any())).thenReturn(Single.just(preRegisteredUser));
        when(commonUserService.findById(any(), anyString(), anyString())).thenReturn(Single.just(preRegisteredUser));

        userService.create(domain, newUser, null)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(commonUserService, times(1)).create(any());
        ArgumentCaptor<User> argument = ArgumentCaptor.forClass(User.class);
        verify(commonUserService).create(argument.capture());

        // Wait few ms to let time to background thread to be executed.
        Thread.sleep(500);
        verify(emailService).send(any(Domain.class), eq(null), eq(Template.REGISTRATION_CONFIRMATION), any(User.class));

        Assert.assertNull(argument.getValue().getRegistrationUserUri());
        Assert.assertNull(argument.getValue().getRegistrationAccessToken());
    }

    @Test
    public void shouldPreRegisterUser_dynamicUserRegistration_domainLevel() {

        String domainId = "domain";

        AccountSettings accountSettings;
        accountSettings = new AccountSettings();
        accountSettings.setDynamicUserRegistration(true);

        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAccountSettings(accountSettings);

        NewUser newUser = new NewUser();
        newUser.setUsername("username");
        newUser.setSource("idp");
        newUser.setClient("client");
        newUser.setPreRegistration(true);

        UserProvider userProvider = mock(UserProvider.class);
        doReturn(Single.just(new DefaultUser(newUser.getUsername()))).when(userProvider).create(any());

        Application client = new Application();
        client.setDomain("domain");

        when(jwtBuilder.sign(any())).thenReturn("token");
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(applicationService.findById(newUser.getClient())).thenReturn(Maybe.just(client));
        when(commonUserService.create(any())).thenReturn(Single.just(new User()));
        when(domainService.buildUrl(any(Domain.class), eq("/confirmRegistration"))).thenReturn("http://localhost:8092/test/confirmRegistration");
        when(emailService.getEmailTemplate(eq(Template.REGISTRATION_CONFIRMATION), any())).thenReturn(Maybe.just(new Email()));

        userService.create(domain, newUser, null)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(commonUserService, times(1)).create(any());
        ArgumentCaptor<User> argument = ArgumentCaptor.forClass(User.class);
        verify(commonUserService).create(argument.capture());

        Assert.assertNotNull(argument.getValue().getRegistrationUserUri());
        assertEquals("http://localhost:8092/test/confirmRegistration", argument.getValue().getRegistrationUserUri());

        Assert.assertNotNull(argument.getValue().getRegistrationAccessToken());
        assertEquals("token", argument.getValue().getRegistrationAccessToken());
    }

    @Test
    public void shouldPreRegisterUser_dynamicUserRegistration_clientLevel() {

        String domainId = "domain";

        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setDynamicUserRegistration(true);
        accountSettings.setInherited(false);

        Domain domain = new Domain();
        domain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername("username");
        newUser.setSource("idp");
        newUser.setClient("client");
        newUser.setPreRegistration(true);

        UserProvider userProvider = mock(UserProvider.class);
        doReturn(Single.just(new DefaultUser(newUser.getUsername()))).when(userProvider).create(any());

        Application client = new Application();
        client.setDomain("domain");

        ApplicationSettings settings = new ApplicationSettings();
        settings.setAccount(accountSettings);
        client.setSettings(settings);

        when(jwtBuilder.sign(any())).thenReturn("token");
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(applicationService.findById(newUser.getClient())).thenReturn(Maybe.just(client));
        when(commonUserService.create(any())).thenReturn(Single.just(new User()));
        when(domainService.buildUrl(any(Domain.class), eq("/confirmRegistration"))).thenReturn("http://localhost:8092/test/confirmRegistration");
        when(emailService.getEmailTemplate(eq(Template.REGISTRATION_CONFIRMATION), any())).thenReturn(Maybe.just(new Email()));

        userService.create(domain, newUser, null)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(commonUserService, times(1)).create(any());
        ArgumentCaptor<User> argument = ArgumentCaptor.forClass(User.class);
        verify(commonUserService).create(argument.capture());

        Assert.assertNotNull(argument.getValue().getRegistrationUserUri());
        assertEquals("http://localhost:8092/test/confirmRegistration", argument.getValue().getRegistrationUserUri());

        Assert.assertNotNull(argument.getValue().getRegistrationAccessToken());
        assertEquals("token", argument.getValue().getRegistrationAccessToken());
    }

    @Test
    public void shouldNotUpdateUser_unknown_client() {
        String domain = "domain";
        String id = "id";

        User user = new User();
        user.setSource("idp");

        UpdateUser updateUser = new UpdateUser();
        updateUser.setClient("client");

        when(commonUserService.findById(eq(DOMAIN), eq(domain), eq(id))).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(mock(UserProvider.class)));
        when(applicationService.findById(updateUser.getClient())).thenReturn(Maybe.empty());
        when(applicationService.findByDomainAndClientId(domain, updateUser.getClient())).thenReturn(Maybe.empty());

        userService.update(domain, id, updateUser)
                .test()
                .assertNotComplete()
                .assertError(ClientNotFoundException.class);
    }

    @Test
    public void shouldNotUpdateUser_invalid_client() {
        String domain = "domain";
        String id = "id";

        User user = new User();
        user.setSource("idp");

        UpdateUser updateUser = new UpdateUser();
        updateUser.setClient("client");

        Application application = new Application();
        application.setDomain("other-domain");

        when(commonUserService.findById(eq(DOMAIN), eq(domain), eq(id))).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(mock(UserProvider.class)));
        when(applicationService.findById(updateUser.getClient())).thenReturn(Maybe.just(application));

        userService.update(domain, id, updateUser)
                .test()
                .assertNotComplete()
                .assertError(ClientNotFoundException.class);
    }

    @Test
    public void shouldResetPassword_externalIdEmpty() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn("idp-id");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));
        when(userProvider.updatePassword(any(), any())).thenReturn(Single.just(idpUser));

        doReturn(true).when(passwordService).isValid(eq(PASSWORD), eq(null), any());
        when(commonUserService.findById(eq(DOMAIN), eq(domain.getId()), eq("user-id"))).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());
        when(tokenService.deleteByUserId(any())).thenReturn(Completable.complete());
        when(passwordHistoryService.addPasswordToHistory(any(), any(), any(), any(), any(), any())).thenReturn(Maybe.just(new PasswordHistory()));


        userService.resetPassword(domain, user.getId(), PASSWORD, null)
                .test()
                .assertComplete()
                .assertNoErrors();

        verify(tokenService).deleteByUserId(any());
    }

    @Test
    public void shouldResetPassword_idpUserNotFound() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn("idp-id");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.empty());
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        when(passwordService.isValid(eq(PASSWORD), eq(null), any())).thenReturn(true);
        when(commonUserService.findById(eq(DOMAIN), eq(domain.getId()), eq("user-id"))).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());
        when(tokenService.deleteByUserId(any())).thenReturn(Completable.complete());
        when(passwordHistoryService.addPasswordToHistory(any(), any(), any(), any(), any(), any())).thenReturn(Maybe.just(new PasswordHistory()));

        userService.resetPassword(domain, user.getId(), PASSWORD, null)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(userProvider, times(1)).create(any());
        verify(tokenService).deleteByUserId(any());
    }

    @Test
    public void shouldAssignRoles() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        User user = mock(User.class);
        when(user.getId()).thenReturn("user-id");

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(commonUserService.findById(eq(DOMAIN), eq(DOMAIN_ID), eq("user-id"))).thenReturn(Single.just(user));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(roles));
        when(commonUserService.update(any())).thenReturn(Single.just(new User()));

        userService.assignRoles(DOMAIN, DOMAIN_ID, user.getId(), rolesIds)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(commonUserService, times(1)).update(any());
    }

    @Test
    public void shouldAssignRoles_roleNotFound() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");

        when(commonUserService.findById(eq(DOMAIN), eq(DOMAIN_ID), eq("user-id"))).thenReturn(Single.just(user));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(Collections.emptySet()));

        userService.assignRoles(DOMAIN, DOMAIN_ID, user.getId(), rolesIds)
                .test()
                .assertNotComplete()
                .assertError(RoleNotFoundException.class);
        verify(commonUserService, never()).update(any());
    }

    @Test
    public void shouldRevokeRole() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(commonUserService.findById(eq(DOMAIN), eq(DOMAIN_ID), eq("user-id"))).thenReturn(Single.just(user));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(roles));
        when(commonUserService.update(any())).thenReturn(Single.just(new User()));

        userService.revokeRoles(DOMAIN, DOMAIN_ID, user.getId(), rolesIds)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(commonUserService, times(1)).update(any());
    }

    @Test
    public void shouldRevokeRoles_roleNotFound() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");

        when(commonUserService.findById(eq(DOMAIN), eq(DOMAIN_ID), eq("user-id"))).thenReturn(Single.just(user));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(Collections.emptySet()));

        userService.revokeRoles(DOMAIN, DOMAIN_ID, user.getId(), rolesIds)
                .test()
                .assertNotComplete()
                .assertError(RoleNotFoundException.class);
        verify(commonUserService, never()).update(any());
    }

    @Test
    public void shouldNotCreate_invalid_password() {
        Domain domain = new Domain();
        domain.setId("domainId");
        String password = "myPassword";
        NewUser newUser = new NewUser();
        newUser.setUsername("Username");
        newUser.setSource("source");
        newUser.setPassword(password);

        doReturn(Maybe.empty()).when(commonUserService).findByDomainAndUsernameAndSource(anyString(), anyString(), anyString());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(mock(UserProvider.class)));
        userService.create(domain, newUser, null)
                .test()
                .assertNotComplete()
                .assertError(InvalidPasswordException.class);
        verify(passwordService, times(1)).isValid(eq(password), eq(null), any());
    }

    @Test
    public void shouldNotResetPassword_invalid_password() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");

        when(commonUserService.findById(eq(DOMAIN), eq(domain.getId()), eq("user-id"))).thenReturn(Single.just(user));

        userService.resetPassword(domain, user.getId(), PASSWORD, null)
                .test()
                .assertNotComplete()
                .assertError(InvalidPasswordException.class);
        verify(passwordService, times(1)).isValid(eq(PASSWORD), eq(null), any());
    }

    @Test
    public void resetShouldReturnErrorWhenPasswordAlreadyInHistory() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");

        when(passwordService.isValid(eq(PASSWORD), eq(null), any())).thenReturn(true);
        when(commonUserService.findById(DOMAIN, domain.getId(), "user-id")).thenReturn(Single.just(user));
        when(passwordHistoryService.addPasswordToHistory(any(), any(), any(), any(), any(), any())).thenReturn(Maybe.error(PasswordHistoryException::passwordAlreadyInHistory));

        var observer = userService.resetPassword(domain, user.getId(), PASSWORD, null)
                .test();
        observer.awaitTerminalEvent();
        observer.assertError(PasswordHistoryException.class);
    }

    @Test
    public void must_not_reset_username_username_invalid() {
        var observer = userService.updateUsername(DOMAIN, "domain", "any-id", "", null).test();

        observer.awaitTerminalEvent();
        observer.assertError(InvalidUserException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    public void must_not_reset_username_user_not_found_by_id() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername("username");


        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.error(new UserNotFoundException(user.getId())));
        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), user.getUsername(), null).test();

        observer.awaitTerminalEvent();
        observer.assertError(UserNotFoundException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    public void must_not_reset_username_with_existing_username() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername("username");

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), user.getUsername(), user.getSource()))
                .thenReturn(Maybe.just(user));

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), user.getUsername(), null).test();

        observer.awaitTerminalEvent();
        observer.assertError(InvalidUserException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    public void must_not_reset_username_user_provider_does_not_exist() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername("username");

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), user.getUsername(), user.getSource()))
                .thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), user.getUsername(), null).test();

        observer.awaitTerminalEvent();
        observer.assertError(UserProviderNotFoundException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    public void must_not_reset_username_does_not_exist() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername("username");

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), user.getUsername(), user.getSource()))
                .thenReturn(Maybe.empty());
        final UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.error(new UserNotFoundException("Could not find user")));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), user.getUsername(), null).test();

        observer.awaitTerminalEvent();
        observer.assertError(UserNotFoundException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    public void must_not_reset_username_error_when_updating() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername("username");

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), user.getUsername(), user.getSource()))
                .thenReturn(Maybe.empty());

        final UserProvider userProvider = mock(UserProvider.class);
        final DefaultUser defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");
        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(anyString(), anyString())).thenReturn(Completable.error(new InvalidUserException("Could not update find user")));

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), user.getUsername(), null).test();

        observer.awaitTerminalEvent();
        observer.assertError(InvalidUserException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    public void must_rollback_username_if_userService_fails() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername("username");

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), user.getUsername(), user.getSource()))
                .thenReturn(Maybe.empty());
        when(commonUserService.update(user)).thenReturn(Single.error(new TechnicalManagementException("an unexpected error has occurred")));

        final UserProvider userProvider = mock(UserProvider.class);
        final DefaultUser defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");
        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(anyString(), anyString())).thenReturn(Completable.complete());

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), user.getUsername(), null).test();

        observer.awaitTerminalEvent();
        observer.assertComplete();

        verify(commonUserService, times(1)).update(any());
        verify(userProvider, times(2)).updateUsername(anyString(), anyString());
    }

    @Test
    public void must_reset_username() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername("username");

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), user.getUsername(), user.getSource()))
                .thenReturn(Maybe.empty());
        when(commonUserService.update(user)).thenReturn(Single.just(user));

        final UserProvider userProvider = mock(UserProvider.class);
        final DefaultUser defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");
        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(anyString(), anyString())).thenReturn(Completable.complete());

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), user.getUsername(), null).test();

        observer.awaitTerminalEvent();
        observer.assertComplete();

        verify(commonUserService, times(1)).update(any());
        verify(userProvider, times(1)).updateUsername(anyString(), anyString());
    }
}
