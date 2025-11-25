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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.utils.MovingFactorUtils;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.service.impl.UserServiceImpl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserIdentity;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.PasswordPolicyService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.InvalidPasswordException;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.PasswordHistoryException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.validators.email.EmailValidatorImpl;
import io.gravitee.am.service.validators.user.UserValidator;
import io.gravitee.am.service.validators.user.UserValidatorImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.service.validators.email.EmailValidatorImpl.EMAIL_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.NAME_LAX_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.NAME_STRICT_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.USERNAME_PATTERN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@SuppressWarnings("ReactiveStreamsUnusedPublisher")
@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    public static final String DOMAIN_ID = "domain#1";
    public static final String PASSWORD = "password";
    public static final String NEW_USERNAME = "newUsername";
    public static final String USERNAME = "username";

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

    @Mock
    private CredentialService credentialService;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    @Spy
    private UserValidator userValidator = new UserValidatorImpl(
            NAME_STRICT_PATTERN,
            NAME_LAX_PATTERN,
            USERNAME_PATTERN,
            new EmailValidatorImpl(EMAIL_PATTERN, true)
    );

    @Mock
    private IdentityProvider identityProvider;

    @BeforeEach
    void setUp() {
        ((UserServiceImpl) userService).setExpireAfter(24 * 3600);
        lenient().when(passwordHistoryService.addPasswordToHistory(any(), any(), any(), any(), any(), any())).thenReturn(Maybe.never());
        lenient().when(identityProviderManager.getIdentityProvider(any())).thenReturn(Optional.of(new IdentityProvider()));
    }

    @Test
    void shouldCreateUser_invalid_identity_provider() {
        String domainId = "domain";
        String clientId = "clientId";

        Domain domain = new Domain();
        domain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername(USERNAME);
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
    void shouldCreateUser_client_is_null() {
        String domainId = "domain";
        String clientId = "clientId";

        Domain domain = new Domain();
        domain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername(USERNAME);
        newUser.setSource("idp");
        newUser.setClient(clientId);
        newUser.setPassword("myPassword");

        UserProvider userProvider = mock(UserProvider.class);
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(applicationService.findById(newUser.getClient())).thenReturn(Maybe.empty());
        when(applicationService.findByDomainAndClientId(domainId, newUser.getClient())).thenReturn(Maybe.empty());
        when(passwordService.isValid(anyString(), eq(null), any())).thenReturn(true);
        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));
        User user = new User();
        user.setReferenceType(DOMAIN);
        user.setReferenceId("domain");
        when(commonUserService.create(any())).thenReturn(Single.just(user));
        when(passwordPolicyService.retrievePasswordPolicy(any(), any(), any())).thenReturn(Maybe.empty());

        userService.create(domain, newUser, null)
                .test()
                .assertComplete()
                .assertNoErrors();

        verify(commonUserService, times(1)).create(any());
        ArgumentCaptor<User> argument = ArgumentCaptor.forClass(User.class);
        verify(commonUserService).create(argument.capture());
    }

    @Test
    void shouldNotCreateUser_invalid_client() {
        String domainId = "domain";

        Domain domain = new Domain();
        domain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername(USERNAME);
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
    void shouldNotCreateUserWhenUsernameIsNull() {
        String domainId = "domain";

        Domain domain = new Domain();
        domain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername(null);
        newUser.setSource("idp");
        newUser.setClient("client");
        newUser.setPassword("MyPassword");

        userService.create(domain, newUser, null)
                .test()
                .assertNotComplete()
                .assertError(UserInvalidException.class);
        verify(commonUserService, never()).findByDomainAndUsernameAndSource(any(), any(), any());
        verify(commonUserService, never()).create(any());
    }

    @Test
    void shouldNotCreateUser_user_already_exists() {
        String domainId = "domain";
        String clientId = "clientId";

        Domain domain = new Domain();
        domain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername(USERNAME);
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
    void shouldPreRegisterUser() throws InterruptedException {

        String domainId = "domain";

        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setDynamicUserRegistration(false);

        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAccountSettings(accountSettings);

        NewUser newUser = new NewUser();
        newUser.setUsername(USERNAME);
        newUser.setSource("idp");
        newUser.setClient("client");
        newUser.setPreRegistration(true);

        User preRegisteredUser = new User();
        preRegisteredUser.setId("userId");
        preRegisteredUser.setReferenceId("domain");
        preRegisteredUser.setPreRegistration(true);
        preRegisteredUser.setReferenceType(DOMAIN);
        preRegisteredUser.setReferenceId("domain");

        UserProvider userProvider = mock(UserProvider.class);
        doReturn(Single.just(new DefaultUser(newUser.getUsername()))).when(userProvider).create(any());

        Application client = new Application();
        client.setDomain("domain");
        when(passwordPolicyService.retrievePasswordPolicy(any(), any(), any())).thenReturn(Maybe.empty());
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
    void shouldPreRegisterUser_dynamicUserRegistration_domainLevel() {

        String domainId = "domain";

        AccountSettings accountSettings;
        accountSettings = new AccountSettings();
        accountSettings.setDynamicUserRegistration(true);

        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAccountSettings(accountSettings);

        NewUser newUser = new NewUser();
        newUser.setUsername(USERNAME);
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
        User user = new User();
        user.setReferenceType(DOMAIN);
        user.setReferenceId("domain");
        when(commonUserService.create(any())).thenReturn(Single.just(user));
        when(domainService.buildUrl(any(Domain.class), eq("/confirmRegistration"))).thenReturn("http://localhost:8092/test/confirmRegistration");
        when(emailService.getEmailTemplate(eq(Template.REGISTRATION_CONFIRMATION), any())).thenReturn(Maybe.just(new Email()));
        when(passwordPolicyService.retrievePasswordPolicy(any(), any(), any())).thenReturn(Maybe.empty());

        userService.create(domain, newUser, null)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(commonUserService, times(1)).create(any());
        ArgumentCaptor<User> argument = ArgumentCaptor.forClass(User.class);
        verify(commonUserService).create(argument.capture());

        assertNotNull(argument.getValue().getRegistrationUserUri());
        assertEquals("http://localhost:8092/test/confirmRegistration", argument.getValue().getRegistrationUserUri());

        Assert.assertNotNull(argument.getValue().getRegistrationAccessToken());
        assertEquals("token", argument.getValue().getRegistrationAccessToken());
    }

    @Test
    void shouldPreRegisterUser_dynamicUserRegistration_clientLevel() {

        String domainId = "domain";

        AccountSettings accountSettings = new AccountSettings();
        accountSettings.setDynamicUserRegistration(true);
        accountSettings.setInherited(false);

        Domain domain = new Domain();
        domain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername(USERNAME);
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

        User user = new User();
        user.setReferenceId("domain");
        user.setReferenceType(DOMAIN);
        when(commonUserService.create(any())).thenReturn(Single.just(user));
        when(domainService.buildUrl(any(Domain.class), eq("/confirmRegistration"))).thenReturn("http://localhost:8092/test/confirmRegistration");
        when(emailService.getEmailTemplate(eq(Template.REGISTRATION_CONFIRMATION), any())).thenReturn(Maybe.just(new Email()));
        when(passwordPolicyService.retrievePasswordPolicy(any(), any(), any())).thenReturn(Maybe.empty());

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
    void shouldResetPassword_externalIdEmpty() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setReferenceId("domain");
        user.setReferenceType(DOMAIN);

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
        when(tokenService.deleteByUser(any())).thenReturn(Completable.complete());
        when(passwordHistoryService.addPasswordToHistory(any(), any(), any(), any(), any(), any())).thenReturn(Maybe.just(new PasswordHistory()));
        when(passwordPolicyService.retrievePasswordPolicy(any(), any(), any())).thenReturn(Maybe.empty());


        userService.resetPassword(domain, user.getId(), PASSWORD, null)
                .test()
                .assertComplete()
                .assertNoErrors();

<<<<<<< HEAD:gravitee-am-management-api/gravitee-am-management-api-service/src/test/java/io/gravitee/am/management/service/UserServiceTest.java
        verify(tokenService).deleteByUser(any());
=======
        verify(tokenService).deleteByUser(any(), any());
    }

    @Test
    void should_delete_user() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setExternalId("ext-user-id");
        user.setSource("idp-id");
        user.setReferenceId("domain");
        user.setReferenceType(ReferenceType.DOMAIN);

        when(userRepository.findById(any(Reference.class), any(UserId.class))).thenReturn(Maybe.just(user));

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.delete(any())).thenReturn(Completable.complete());
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(userActivityManagementService.deleteByDomainAndUser(any(), any())).thenReturn(Completable.complete());
        when(userRepository.delete(anyString())).thenReturn(Completable.complete());
        when(passwordHistoryService.deleteByUser(any(), anyString())).thenReturn(Completable.complete());
        when(certificateCredentialService.deleteByUserId(any(), anyString())).thenReturn(Completable.complete());
        when(credentialService.deleteByUserId(any(), anyString())).thenReturn(Completable.complete());

        when(eventService.create(any())).thenAnswer(invocation -> Single.just(invocation.getArguments()[0]));
        when(tokenService.deleteByUser(any(), any())).thenReturn(Completable.complete());

        userService.delete(domain, user.getId(), null)
                .test()
                .assertComplete()
                .assertNoErrors();

        // Verify that both certificate and WebAuthn credentials are deleted
        verify(certificateCredentialService, times(1)).deleteByUserId(domain, user.getId());
        verify(credentialService, times(1)).deleteByUserId(domain, user.getId());

        verify(tokenService).deleteByUser(any(), any());
        verify(auditService).report(argThat(auditBuilder -> auditBuilder.build(new ObjectMapper()).getOutcome().getStatus() == Status.SUCCESS));
        verify(eventService).create(argThat(arg -> arg.getType() == Type.USER && arg.getPayload().getId().equals(user.getId())));
>>>>>>> 4b5ae7d12 (fix: am-6085 delete WebAuthn credentials on user deletion):gravitee-am-management-api/gravitee-am-management-api-service/src/test/java/io/gravitee/am/management/service/ManagementUserServiceTest.java
    }

    @Test
    void shouldResetPassword_idpUserNotFound() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setReferenceId("domain");
        user.setReferenceType(DOMAIN);

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
        when(tokenService.deleteByUser(any())).thenReturn(Completable.complete());
        when(passwordHistoryService.addPasswordToHistory(any(), any(), any(), any(), any(), any())).thenReturn(Maybe.just(new PasswordHistory()));
        when(passwordPolicyService.retrievePasswordPolicy(any(), any(), any())).thenReturn(Maybe.empty());

        userService.resetPassword(domain, user.getId(), PASSWORD, null)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(userProvider, times(1)).create(any());
        verify(tokenService).deleteByUser(any());
    }

    @Test
    void shouldAssignRoles() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        User user = new User();
        user.setId("user-id");
        user.setReferenceId("domain");
        user.setReferenceType(DOMAIN);

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(commonUserService.findById(eq(DOMAIN), eq(DOMAIN_ID), eq("user-id"))).thenReturn(Single.just(user));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(roles));
        when(commonUserService.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));

        userService.assignRoles(DOMAIN, DOMAIN_ID, user.getId(), rolesIds)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(commonUserService, times(1)).update(any());
    }

    @Test
    void shouldAssignRoles_roleNotFound() {
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
    void shouldRevokeRole() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setReferenceId("domain");
        user.setReferenceType(DOMAIN);


        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(commonUserService.findById(eq(DOMAIN), eq(DOMAIN_ID), eq("user-id"))).thenReturn(Single.just(user));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(roles));
        when(commonUserService.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));

        userService.revokeRoles(DOMAIN, DOMAIN_ID, user.getId(), rolesIds)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(commonUserService, times(1)).update(any());
    }

    @Test
    void shouldRevokeRoles_roleNotFound() {
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
    void shouldNotCreate_invalid_password() {
        Domain domain = new Domain();
        domain.setId("domainId");
        String password = "myPassword";
        NewUser newUser = new NewUser();
        newUser.setUsername("Username");
        newUser.setSource("source");
        newUser.setPassword(password);

        when(passwordPolicyService.retrievePasswordPolicy(any(), any(), any())).thenReturn(Maybe.empty());
        doReturn(Maybe.empty()).when(commonUserService).findByDomainAndUsernameAndSource(anyString(), anyString(), anyString());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(mock(UserProvider.class)));
        userService.create(domain, newUser, null)
                .test()
                .assertNotComplete()
                .assertError(InvalidPasswordException.class);
        verify(passwordService, times(1)).isValid(eq(password), eq(null), any());
        verify(userValidator, never()).validate(any());
        verify(auditService).report(argThat(builder -> Status.FAILURE.equals(builder.build(new ObjectMapper()).getOutcome().getStatus())));
    }

    @Test
    void shouldNotResetPassword_invalid_password() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setReferenceId("domain");
        user.setReferenceType(DOMAIN);

        when(commonUserService.findById(eq(DOMAIN), eq(domain.getId()), eq("user-id"))).thenReturn(Single.just(user));
        when(passwordPolicyService.retrievePasswordPolicy(any(), any(), any())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(mock(UserProvider.class)));

        userService.resetPassword(domain, user.getId(), PASSWORD, null)
                .test()
                .assertNotComplete()
                .assertError(InvalidPasswordException.class);
        verify(passwordService, times(1)).isValid(eq(PASSWORD), eq(null), any());
    }

    @Test
    void resetShouldReturnErrorWhenPasswordAlreadyInHistory() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setReferenceType(DOMAIN);
        user.setReferenceId("domain");

        when(passwordPolicyService.retrievePasswordPolicy(any(), any(), any())).thenReturn(Maybe.empty());
        when(passwordService.isValid(eq(PASSWORD), eq(null), any())).thenReturn(true);
        when(commonUserService.findById(DOMAIN, domain.getId(), "user-id")).thenReturn(Single.just(user));
        when(passwordHistoryService.addPasswordToHistory(any(), any(), any(), any(), any(), any())).thenReturn(Maybe.error(PasswordHistoryException::passwordAlreadyInHistory));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(mock(UserProvider.class)));

        var observer = userService.resetPassword(domain, user.getId(), PASSWORD, null)
                .test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(PasswordHistoryException.class);
    }

    @Test
    void must_not_reset_username_username_invalid() {
        var observer = userService.updateUsername(DOMAIN, "domain", "any-id", "", null).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidUserException.class);

        verify(commonUserService, times(0)).update(any());
    }

    @Test
    void must_not_reset_username_user_not_found_by_id() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);


        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.error(new UserNotFoundException(user.getId())));
        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), NEW_USERNAME, null).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(UserNotFoundException.class);

        verify(commonUserService, times(0)).update(any());
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

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.just(user));

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), NEW_USERNAME, null).test();

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

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), NEW_USERNAME, null).test();

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

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.empty());
        final UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.error(new UserNotFoundException("Could not find user")));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), NEW_USERNAME, null).test();

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

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.empty());

        final UserProvider userProvider = mock(UserProvider.class);
        final DefaultUser defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");
        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(any(), anyString())).thenReturn(Single.error(new InvalidUserException("Could not update find user")));

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), NEW_USERNAME, null).test();

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

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
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

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));

        var credential = new Credential();
        credential.setUsername(user.getUsername());

        when(credentialService.findByUsername(any(), anyString(), eq(user.getUsername()))).thenReturn(Flowable.just(credential));
        when(credentialService.update(credential)).thenReturn(Single.just(credential));

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), NEW_USERNAME, null).test();

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

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
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

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));

        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        when(credentialService.findByUsername(any(), anyString(), eq(user.getUsername()))).thenReturn(Flowable.empty());

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), NEW_USERNAME, null).test();

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

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
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

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));

        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        var credential = new Credential();
        credential.setUsername(user.getUsername());

        when(credentialService.findByUsername(any(), anyString(), eq(user.getUsername()))).thenReturn(Flowable.just(credential));
        when(credentialService.update(credential)).thenReturn(Single.just(credential));

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), NEW_USERNAME, null).test();

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

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
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

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));

        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());
        when(credentialService.findByUsername(any(), anyString(), eq(user.getUsername()))).thenReturn(Flowable.empty());

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), NEW_USERNAME, null).test();

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

    @Test
    void must_update_user_with_user_provider() {
        var domain = new Domain();
        domain.setId("domain");


        var user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setReferenceType(DOMAIN);
        user.setReferenceId("domain");

        var updatedUser = new UpdateUser();
        updatedUser.setFirstName("New firstName");
        updatedUser.setLastName("New lastName");
        updatedUser.setEmail("john@doe.com");

        var additionalInformation = new HashMap<String, Object>();
        additionalInformation.put("customClaim", "claim");
        updatedUser.setAdditionalInformation(additionalInformation);

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.update(DOMAIN, domain.getId(), user.getId(), updatedUser)).thenReturn(Single.just(user));

        final UserProvider userProvider = mock(UserProvider.class);

        final var defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");

        final var idpUserUpdated = new DefaultUser(NEW_USERNAME);
        defaultUser.setId("idp-user-id");

        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.update(any(), any())).thenReturn(Single.just(idpUserUpdated));

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        var observer = userService.update(domain.getId(), user.getId(), updatedUser).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();

        verify(commonUserService, times(1)).update(DOMAIN, domain.getId(), user.getId(), updatedUser);
        verify(userProvider, times(1)).update(any(), any());
    }

    @Test
    void must_update_user_with_user_provider_even_if_user_absent() {
        var domain = new Domain();
        domain.setId("domain");

        var user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setReferenceType(DOMAIN);
        user.setReferenceId("domain");

        var updatedUser = new UpdateUser();
        updatedUser.setSource("idp-user-id");

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.update(DOMAIN, domain.getId(), user.getId(), updatedUser)).thenReturn(Single.just(user));

        final UserProvider userProvider = mock(UserProvider.class);

        final var defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");

        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.error(new UserNotFoundException("User not found in idp")));

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        var observer = userService.update(domain.getId(), user.getId(), updatedUser).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();

        verify(commonUserService, times(1)).update(DOMAIN, domain.getId(), user.getId(), updatedUser);
    }

    @Test
    void must_update_user_without_provider() {
        var domain = new Domain();
        domain.setId("domain");

        var user = new User();
        user.setId("user-id");
        user.setSource("idp-user-id");
        user.setReferenceType(DOMAIN);
        user.setReferenceId("domain");

        var updatedUser = new UpdateUser();
        updatedUser.setSource("idp-user-id");

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.update(DOMAIN, domain.getId(), user.getId(), updatedUser))
                .thenReturn(Single.just(user));

        final UserProvider userProvider = mock(UserProvider.class);

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());
        var observer = userService.update(domain.getId(), user.getId(), updatedUser).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();

        verify(commonUserService, times(1)).update(DOMAIN, domain.getId(), user.getId(), updatedUser);
        verify(userProvider, times(0)).update(any(), any());
    }

    @Test
    void must_not_update_user_with_user_with_unexpected_error() {
        var domain = new Domain();
        domain.setId("domain");

        var user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);

        var updatedUser = new UpdateUser();
        user.setId("user-id");
        user.setSource("idp-user-id");
        user.setUsername(USERNAME);

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));

        final UserProvider userProvider = mock(UserProvider.class);

        final var defaultUser = new DefaultUser(user.getUsername());
        defaultUser.setId("idp-user-id");

        when(userProvider.findByUsername(anyString()))
                .thenReturn(Maybe.error(new IOException("Other issue")));

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        var observer = userService.update(domain.getId(), user.getId(), updatedUser).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(IOException.class);
    }

    @Test
    void must_reset_username_and_unlock_user() {
        Domain domain = new Domain();
        domain.setId("domain");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setAccountLockedAt(new Date());
        user.setAccountLockedUntil(new Date());
        user.setAccountNonLocked(false);
        user.setReferenceId(domain.getId());
        user.setReferenceType(DOMAIN);

        when(commonUserService.findById(DOMAIN, domain.getId(), user.getId()))
                .thenReturn(Single.just(user));
        when(commonUserService.findByUsernameAndSource(DOMAIN, domain.getId(), NEW_USERNAME, user.getSource()))
                .thenReturn(Maybe.empty());
        when(commonUserService.update(user)).thenReturn(Single.just(user));

        final UserProvider userProvider = mock(UserProvider.class);
        final DefaultUser defaultUser = new DefaultUser(NEW_USERNAME);
        defaultUser.setId("idp-user-id");
        when(userProvider.findByUsername(anyString())).thenReturn(Maybe.just(defaultUser));
        when(userProvider.updateUsername(any(), anyString())).thenReturn(Single.just(defaultUser));

        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());
        when(credentialService.findByUsername(any(), anyString(), eq(user.getUsername()))).thenReturn(Flowable.empty());

        var observer = userService.updateUsername(DOMAIN, domain.getId(), user.getId(), NEW_USERNAME, null).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();

        verify(commonUserService, times(1)).update(argThat(argument -> {
            assertEquals(NEW_USERNAME, argument.getUsername());
            assertTrue(argument.isAccountNonLocked());
            assertNull(argument.getAccountLockedUntil());
            assertNull(argument.getAccountLockedAt());
            return true;
        }));
        verify(userProvider, times(1)).updateUsername(any(), anyString());
        verify(loginAttemptService, times(1)).reset(any());
    }

    @Test
    void must_not_send_registration_confirmation_domain_not_found() {
        var domain = new Domain();
        domain.setId("domain");

        var user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setPreRegistration(true);
        user.setRegistrationCompleted(false);

        when(domainService.findById(domain.getId())).thenReturn(Maybe.empty());

        userService.sendRegistrationConfirmation(domain.getId(), user.getId(), null)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(DomainNotFoundException.class);
    }

    @Test
    void must_not_send_registration_confirmation_pre_registration_disabled() {
        var domain = new Domain();
        domain.setId("domain");

        var user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setPreRegistration(false);
        user.setRegistrationCompleted(false);

        when(domainService.findById(domain.getId())).thenReturn(Maybe.just(domain));
        when(userService.findById(ReferenceType.DOMAIN, domain.getId(), user.getId())).thenReturn(Single.just(user));

        userService.sendRegistrationConfirmation(domain.getId(), user.getId(), null)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(UserInvalidException.class)
                .assertError(throwable -> "Pre-registration is disabled for the user user-id".equals(throwable.getMessage()));;
    }

    @Test
    void shouldUnlinkIdentity_nominalCase() {
        String extraUserId = "user-id-2";
        UserIdentity userIdentity = new UserIdentity();
        userIdentity.setUserId(extraUserId);
        List<UserIdentity> userIdentities = Arrays.asList(userIdentity);

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setIdentities(userIdentities);
        user.setReferenceId("domain");
        user.setReferenceType(DOMAIN);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(commonUserService.findById(eq("user-id"))).thenReturn(Maybe.just(user));
        when(commonUserService.update(userCaptor.capture())).thenReturn(Single.just(user));
        userService.unlinkIdentity(user.getId(), extraUserId, null)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(commonUserService, times(1)).update(any());
        User expectedUser = userCaptor.getValue();
        Assert.assertTrue(expectedUser.getIdentities().isEmpty());
    }

    @Test
    void shouldUnlinkIdentity_noLinkedAccount() {
        String extraUserId = "user-id-2";
        UserIdentity userIdentity = new UserIdentity();
        userIdentity.setUserId(extraUserId);

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setIdentities(null);

        when(commonUserService.findById(eq("user-id"))).thenReturn(Maybe.just(user));
        userService.unlinkIdentity(user.getId(), extraUserId, null)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(commonUserService, never()).update(any());
    }

    @Test
    void must_not_send_registration_confirmation_user_already_registered() {
        var domain = new Domain();
        domain.setId("domain");

        var user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setUsername(USERNAME);
        user.setPreRegistration(true);
        user.setRegistrationCompleted(true);

        when(domainService.findById(domain.getId())).thenReturn(Maybe.just(domain));
        when(userService.findById(ReferenceType.DOMAIN, domain.getId(), user.getId())).thenReturn(Single.just(user));

        userService.sendRegistrationConfirmation(domain.getId(), user.getId(), null)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertError(UserInvalidException.class)
                .assertError(throwable -> "Registration is completed for the user user-id".equals(throwable.getMessage()));
    }

    @ParameterizedTest
    @MethodSource
    void must_send_the_good_template_based_on_configuration(Domain domain, Application application, Template template, String registrationUri) {

        var user = new User();
        user.setId("user-id");
        user.setSource("idp-id");
        user.setClient("client-id");
        user.setReferenceType(DOMAIN);
        user.setReferenceId(domain.getId());
        user.setUsername(USERNAME);
        user.setPreRegistration(true);
        user.setRegistrationCompleted(false);
        user.setRegistrationUserUri(registrationUri);

        when(domainService.findById(domain.getId())).thenReturn(Maybe.just(domain));
        when(userService.findById(ReferenceType.DOMAIN, domain.getId(), user.getId())).thenReturn(Single.just(user));
        when(applicationService.findById(user.getClient())).thenReturn(Maybe.just(application));

        userService.sendRegistrationConfirmation(domain.getId(), user.getId(), null)
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(emailService, times(1)).send(domain, application, template, user);
    }

    @Test
    public void shouldNotUpdateUserWithoutAnId(){
        TestObserver<User> observer = new TestObserver<>();
        userService.update(ReferenceType.DOMAIN, "domain_id", null, new UpdateUser(), new DefaultUser())
                .subscribe(observer);
        observer.assertError(throwable -> throwable.getMessage().equals("User id is required"));
    }

    @Test
    public void shouldThrowAnExceptionOnUpdateExternalUserWithForceResetPassword() {
        // given
        UpdateUser updateUser = new UpdateUser();
        updateUser.setForceResetPassword(true);

        User user = new User();
        user.setInternal(false);

        Mockito.when(userService.findById(any(), any(), any())).thenReturn(Single.just(user));

        // when
        TestObserver<User> observer = new TestObserver<>();
        userService.update(ReferenceType.DOMAIN, "id", "id", updateUser, new DefaultUser())
                .subscribe(observer);

        // then
        observer.assertError(throwable -> throwable.getMessage().equals("forceResetPassword is forbidden on external users"));
    }

    @Test
    public void shouldThrowAnExceptionAndReportAuditIfPasswordIsInvalidOnCreate(){
        // given
        String domainId = "domainId";
        String clientId = "clientId";
        Application client = new Application();
        client.setId(clientId);
        client.setDomain(domainId);

        Domain domain = new Domain();
        domain.setId(domainId);

        NewUser newUser = new NewUser();
        newUser.setUsername(USERNAME);
        newUser.setSource("unknown-idp");
        newUser.setPassword("123");
        newUser.setClient(clientId);

        // when
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(mock(UserProvider.class)));
        when(applicationService.findById(newUser.getClient())).thenReturn(Maybe.just(client));
        when(passwordPolicyService.retrievePasswordPolicy(any(), any(), any())).thenReturn(Maybe.just(new PasswordPolicy()));
        when(passwordService.isValid(anyString(),any(),any())).thenReturn(false);

        TestObserver<User> observer = new TestObserver<>();
        userService.create(domain, newUser, new DefaultUser()).subscribe(observer);

        // then
        observer.assertError(throwable -> throwable.getMessage().equals("The provided password does not meet the password policy requirements."));
        verify(auditService,atMostOnce()).report(any());
        verify(auditService).report(argThat(builder -> Status.FAILURE.equals(builder.build(new ObjectMapper()).getOutcome().getStatus())));
        verify(auditService).report(argThat(builder -> builder.build(new ObjectMapper()).getType().equals(EventType.USER_CREATED)));
    }

    @Test
    public void shouldThrowAnExceptionAndReportAuditIfPasswordIsInvalidOnResetPassword(){
        // given
        String domainId = "domainId";
        String clientId = "clientId";
        Application client = new Application();
        client.setId(clientId);
        client.setDomain(domainId);

        Domain domain = new Domain();
        domain.setId(domainId);

        User user = new User();
        user.setId("user-id");
        user.setUsername(USERNAME);
        user.setSource("unknown-idp");
        user.setPassword("myPassword");
        user.setClient(clientId);
        user.setReferenceType(DOMAIN);
        user.setReferenceId(domain.getId());

        // when
        when(userService.findById(any(), anyString(), anyString())).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(mock(UserProvider.class)));
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(Optional.of(new IdentityProvider()));
        when(applicationService.findById(user.getClient())).thenReturn(Maybe.just(client));
        when(passwordPolicyService.retrievePasswordPolicy(any(), any(), any())).thenReturn(Maybe.just(new PasswordPolicy()));

        TestObserver<Void> observer = new TestObserver<>();
        userService.resetPassword(domain, user.getId(),"123", new DefaultUser()).subscribe(observer);

        // then
        observer.assertError(throwable -> throwable instanceof InvalidPasswordException);
        verify(auditService,atMostOnce()).report(any());
        verify(auditService).report(argThat(builder -> Status.FAILURE.equals(builder.build(new ObjectMapper()).getOutcome().getStatus())));
        verify(auditService).report(argThat(builder -> builder.build(new ObjectMapper()).getType().equals(EventType.USER_PASSWORD_RESET)));
    }


    private static Stream<Arguments> must_send_the_good_template_based_on_configuration() {

        var domainId = "domain-id";
        var applicationId = "application-id";

        return Stream.of(
                Arguments.of(createDomain(domainId, null), createApplication(domainId, applicationId, null), Template.REGISTRATION_CONFIRMATION, null),
                Arguments.of(createDomain(domainId, new AccountSettings()), createApplication(domainId, applicationId, null), Template.REGISTRATION_CONFIRMATION, null),
                Arguments.of(createDomain(domainId, createAccountSetting(false, true)), createApplication(domainId, applicationId, null), Template.REGISTRATION_VERIFY, "/verifyRegistration"),
                Arguments.of(createDomain(domainId, createAccountSetting(false, true)), createApplication(domainId, applicationId, createAccountSetting(false, true)), Template.REGISTRATION_VERIFY, "/verifyRegistration"),
                Arguments.of(createDomain(domainId, new AccountSettings()), createApplication(domainId, applicationId, createAccountSetting(false, true)), Template.REGISTRATION_VERIFY, "/verifyRegistration"),
                Arguments.of(createDomain(domainId, createAccountSetting(false, true)), createApplication(domainId, applicationId, createAccountSetting(false, false)), Template.REGISTRATION_CONFIRMATION, null),
                Arguments.of(createDomain(domainId, createAccountSetting(false, true)), createApplication(domainId, applicationId, createAccountSetting(false, false)), Template.REGISTRATION_CONFIRMATION, null)

        );
    }

    private static Domain createDomain(String domainId, AccountSettings accountSettings) {
        var domain = new Domain();

        domain.setId(domainId);
        domain.setAccountSettings(accountSettings);

        return domain;
    }

    private static Application createApplication(String domainId, String applicationId, AccountSettings accountSettings) {

        var applicationSettings = new ApplicationSettings();
        applicationSettings.setAccount(accountSettings);

        var application = new Application();

        application.setId(applicationId);
        application.setDomain(domainId);
        application.setSettings(applicationSettings);

        return application;
    }

    private static AccountSettings createAccountSetting(boolean inherited, boolean sendVerifyRegistrationAccountEmail) {
        var accountSettings = new AccountSettings();

        accountSettings.setInherited(inherited);
        accountSettings.setSendVerifyRegistrationAccountEmail(sendVerifyRegistrationAccountEmail);

        return accountSettings;
    }
}
