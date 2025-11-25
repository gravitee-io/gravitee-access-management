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
package io.gravitee.am.gateway.handler.scim.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.scim.Schema;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.email.EmailService;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManager;
import io.gravitee.am.gateway.handler.common.role.RoleManager;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.service.RevokeTokenGatewayService;
import io.gravitee.am.gateway.handler.common.service.UserActivityGatewayService;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.exception.UniquenessException;
import io.gravitee.am.gateway.handler.scim.model.GraviteeUser;
import io.gravitee.am.gateway.handler.scim.model.ListResponse;
import io.gravitee.am.gateway.handler.scim.model.Operation;
import io.gravitee.am.gateway.handler.scim.model.PatchOp;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.scim.service.impl.ProvisioningUserServiceImpl;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.gateway.handler.common.service.mfa.RateLimiterService;
import io.gravitee.am.gateway.handler.common.service.mfa.VerifyAttemptService;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.validators.email.EmailValidatorImpl;
import io.gravitee.am.service.validators.user.UserValidator;
import io.gravitee.am.service.validators.user.UserValidatorImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.core.json.Json;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.gravitee.am.service.validators.email.EmailValidatorImpl.EMAIL_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.NAME_LAX_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.NAME_STRICT_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.USERNAME_PATTERN;
import static io.reactivex.rxjava3.core.Completable.complete;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doAnswer;
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
@RunWith(MockitoJUnitRunner.class)
public class ProvisioningUserServiceTest {

    public static final String PASSWORD = "user-password";
    @InjectMocks
    private ProvisioningUserService userService = new ProvisioningUserServiceImpl();

    @Spy
    private UserValidator userValidator = new UserValidatorImpl(
            NAME_STRICT_PATTERN,
            NAME_LAX_PATTERN,
            USERNAME_PATTERN,
            new EmailValidatorImpl(EMAIL_PATTERN, true)
    );

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserActivityGatewayService userActivityService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Spy
    private Domain domain = new Domain();

    @Mock
    private ScimGroupService groupService;

    @Mock
    private PasswordService passwordService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private PasswordHistoryService passwordHistoryService;

    @Mock
    private VerifyAttemptService verifyAttemptService;

    @Mock
    private PasswordPolicyManager passwordPolicyManager;

    @Mock
    private RoleManager roleManager;

    private static final String DOMAIN_ID = "domain";

    @Mock
    private RevokeTokenGatewayService tokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private CredentialGatewayService credentialService;

    @Before
    public void setUp() {
        when(passwordHistoryService.addPasswordToHistory(any(), any(), any(), any(), any())).thenReturn(Maybe.just(new PasswordHistory()));
        when(userRepository.findByExternalIdAndSource(any(), any(), any())).thenReturn(Maybe.empty());
        domain.setId(DOMAIN_ID);
    }

    @Test
    public void shouldListUsers() {
        final List<io.gravitee.am.model.User> users = IntStream.range(0, 10).mapToObj(i -> {
            final io.gravitee.am.model.User user = new io.gravitee.am.model.User();
            user.setUsername("" + i);
            return user;
        }).toList();

        final Page page = new Page(users, 0, users.size());
        final String domainID = "any-domain-id";
        when(domain.getId()).thenReturn(domainID);
        when(userRepository.findAllScim(new Reference(ReferenceType.DOMAIN, domainID), 0, 10)).thenReturn(Single.just(page));
        when(groupService.findByMember(any())).thenReturn(Flowable.empty());

        TestObserver<ListResponse<io.gravitee.am.gateway.handler.scim.model.User>> observer = userService.list(null, 0, 10, "").test();
        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValue(listResp -> 10 == listResp.getItemsPerPage()
                &&
                listResp.getResources().stream().map(io.gravitee.am.gateway.handler.scim.model.User::getUserName).collect(Collectors.joining(","))
                        .equals(users.stream().map(io.gravitee.am.model.User::getUsername).collect(Collectors.joining(","))));

        verify(userRepository, times(1)).findAllScim(new Reference(ReferenceType.DOMAIN, domainID), 0, 10);
    }

    @Test
    public void shouldCreateUser_no_user_provider() {

        User newUser = mock(User.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");
        when(newUser.getPassword()).thenReturn(null);

        when(userRepository.findByUsernameAndSource(any(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());

        ArgumentCaptor<io.gravitee.am.model.User> newUserDefinition = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(DOMAIN_ID);
        when(userRepository.create(newUserDefinition.capture())).thenReturn(Single.just(user));

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        assertFalse(newUserDefinition.getValue().isInternal());
        assertTrue(newUserDefinition.getValue().isEnabled());
    }

    @Test
    public void shouldCreateUser_invalid_roles() {
        User newUser = mock(User.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");
        when(newUser.getRoles()).thenReturn(Arrays.asList("role-wrong-1", "role-wrong-2"));

        when(userRepository.findByUsernameAndSource(any(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(roleManager.findByIdIn(newUser.getRoles())).thenReturn(Flowable.empty());

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidValueException.class);
    }

    @Test
    public void shouldCreateUser() {
        innerCreateUser(null);
    }

    @Test
    public void shouldCreateUser_WithPassword() {
        innerCreateUser(UUID.randomUUID().toString());
    }

    @Test
    public void shouldNotCreateUserWhenUsernameIsNull() {
        User newUser = mock(User.class);
        when(newUser.getUserName()).thenReturn(null);

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertError(UserInvalidException.class);

        verify(userRepository, never()).create(any());
        verify(userRepository, never()).findByUsernameAndSource(any(), anyString(), anyString());
        verify(userRepository, never()).findByExternalIdAndSource(any(), anyString(), anyString());
    }
    @Test
    public void shouldNotCreateUserWhenUsernameAlreadyUsed() {
        var externalId = "external-id";
        var user = new io.gravitee.am.model.User();
        user.setExternalId(externalId);
        var pwd = UUID.randomUUID().toString();

        when(userRepository.findByUsernameAndSource(any(), anyString(), anyString())).thenReturn(Maybe.just(user));
        when(passwordService.isValid(any(), any(), any())).thenReturn(true);

        User newUser = mock(User.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username-1");
        when(newUser.getPassword()).thenReturn(pwd);
        when(newUser.getExternalId()).thenReturn(externalId);
        when(newUser.getRoles()).thenReturn(Arrays.asList("role-1", "role-2"));

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertError(UniquenessException.class);

        verify(userRepository, never()).create(any());
    }

    @Test
    public void shouldNotCreateUserWhenExternalIdAlreadyUsed() {
        var externalId = "external-id-3";
        var user = new io.gravitee.am.model.User();
        user.setExternalId(externalId);
        var pwd = UUID.randomUUID().toString();

        when(userRepository.findByExternalIdAndSource(any(), any(), any())).thenReturn(Maybe.just(user));
        when(userRepository.findByUsernameAndSource(any(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(passwordService.isValid(any(), any(), any())).thenReturn(true);

        User newUser = mock(User.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username-1");
        when(newUser.getPassword()).thenReturn(pwd);
        when(newUser.getExternalId()).thenReturn(externalId);
        when(newUser.getRoles()).thenReturn(Arrays.asList("role-1", "role-2"));

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertError(UniquenessException.class);

        verify(userRepository, never()).create(any());
    }

    private void innerCreateUser(String pwd) {

        User newUser = mock(User.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");
        when(newUser.getPassword()).thenReturn(pwd);
        when(newUser.getRoles()).thenReturn(Arrays.asList("role-1", "role-2"));

        if (pwd != null) {
            when(passwordService.isValid(any(), any(), any())).thenReturn(true);
        }

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        io.gravitee.am.model.User createdUser = new io.gravitee.am.model.User();
        createdUser.setReferenceId(DOMAIN_ID);
        createdUser.setReferenceType(ReferenceType.DOMAIN);

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(userRepository.findByUsernameAndSource(any(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(roleManager.findByIdIn(newUser.getRoles())).thenReturn(Flowable.fromIterable(roles));

        ArgumentCaptor<io.gravitee.am.model.User> newUserDefinition = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        when(userRepository.create(newUserDefinition.capture())).thenReturn(Single.just(createdUser));

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        assertTrue(newUserDefinition.getValue().isInternal());
        if (pwd == null) {
            assertFalse(newUserDefinition.getValue().isEnabled());
        } else {
            assertTrue(newUserDefinition.getValue().isEnabled());
        }
    }

    @Test
    public void shouldUpdateUser_status_enabled() {
        io.gravitee.am.model.User existingUser = mock(io.gravitee.am.model.User.class);
        when(existingUser.getId()).thenReturn("user-id");
        when(existingUser.getSource()).thenReturn("user-idp");
        when(existingUser.getUsername()).thenReturn("username");
        when(existingUser.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(existingUser.getReferenceId()).thenReturn(DOMAIN_ID);

        User scimUser = mock(User.class);
        when(scimUser.getPassword()).thenReturn(PASSWORD);
        when(scimUser.isActive()).thenReturn(true);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        when(userRepository.findById(existingUser.getId())).thenReturn(Maybe.just(existingUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        ArgumentCaptor<io.gravitee.am.model.User> userCaptor = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        when(userRepository.update(any(), any())).thenReturn(Single.just(existingUser));
        when(groupService.findByMember(existingUser.getId())).thenReturn(Flowable.empty());
        when(passwordService.isValid(eq(PASSWORD), any(), any())).thenReturn(true);

        TestObserver<User> testObserver = userService.update(existingUser.getId(), scimUser, null, "/", null, null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        verify(userRepository, times(1)).update(userCaptor.capture(), any());
        verify(userProvider).create(any());
        verify(userProvider, never()).update(anyString(), any());
        verify(userProvider, never()).updatePassword(any(), eq(PASSWORD));
        verify(tokenService, never()).deleteByUser(any());
        assertTrue(userCaptor.getValue().isEnabled());
    }

    @Test
    public void shouldUpdateUser_status_disabled_and_tokens_revoked() {
        io.gravitee.am.model.User existingUser = mock(io.gravitee.am.model.User.class);
        when(existingUser.getId()).thenReturn("user-id");
        when(existingUser.getSource()).thenReturn("user-idp");
        when(existingUser.getUsername()).thenReturn("username");

        User scimUser = mock(User.class);
        when(scimUser.getPassword()).thenReturn(PASSWORD);
        when(scimUser.isActive()).thenReturn(false);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(userRepository.findById(existingUser.getId())).thenReturn(Maybe.just(existingUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        when(tokenService.deleteByUser(any())).thenReturn(Completable.complete());
        ArgumentCaptor<io.gravitee.am.model.User> userCaptor = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        when(userRepository.update(any(), any())).thenReturn(Single.just(existingUser));
        when(groupService.findByMember(existingUser.getId())).thenReturn(Flowable.empty());
        when(passwordService.isValid(eq(PASSWORD), any(), any())).thenReturn(true);

        TestObserver<User> testObserver = userService.update(existingUser.getId(), scimUser, null, "/", null, null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        verify(userRepository, times(1)).update(userCaptor.capture(), any());
        verify(userProvider).create(any());
        verify(userProvider, never()).update(anyString(), any());
        verify(userProvider, never()).updatePassword(any(), eq(PASSWORD));
        verify(tokenService, times(1)).deleteByUser(any());
        assertFalse(userCaptor.getValue().isEnabled());
    }

    @Test
    public void shouldUpdateUser_roles_entitlements() {
        io.gravitee.am.model.User existingUser = new io.gravitee.am.model.User();
        existingUser.setId("user-id");
        existingUser.setSource("user-idp");
        existingUser.setRoles(List.of("r1"));
        existingUser.setEntitlements(List.of("e1"));
        existingUser.setSource("user-idp");
        existingUser.setUsername("username");
        existingUser.setReferenceType(ReferenceType.DOMAIN);
        existingUser.setReferenceId(DOMAIN_ID);

        User scimUser = mock(User.class);
        when(scimUser.getPassword()).thenReturn(PASSWORD);
        when(scimUser.isActive()).thenReturn(true);
        when(scimUser.getRoles()).thenReturn(List.of("r2"));
        when(scimUser.getEntitlements()).thenReturn(Arrays.asList("e1", "e2"));

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        final Role r2 = new Role();
        r2.setId("r2");
        when(roleManager.findByIdIn(any())).thenReturn(Flowable.just(r2));
        when(userRepository.findById(existingUser.getId())).thenReturn(Maybe.just(existingUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        ArgumentCaptor<io.gravitee.am.model.User> userCaptor = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        when(userRepository.update(any(), any())).thenReturn(Single.just(existingUser));
        when(groupService.findByMember(existingUser.getId())).thenReturn(Flowable.empty());
        when(passwordService.isValid(eq(PASSWORD), any(), any())).thenReturn(true);

        TestObserver<User> testObserver = userService.update(existingUser.getId(), scimUser, null, "/", null, null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        verify(userRepository, times(1)).update(userCaptor.capture(), argThat(actions -> actions.updateRole()
                && actions.updateEntitlements()
                && !(actions.updateDynamicRole() || actions.updateAddresses() || actions.updateAttributes())));
        verify(userProvider).create(any());
        verify(userProvider, never()).update(anyString(), any());
        verify(userProvider, never()).updatePassword(any(), eq(PASSWORD));
        assertTrue(userCaptor.getValue().isEnabled());
    }

    @Test
    public void shouldUpdate_UserWithExtId_and_password() {
        io.gravitee.am.model.User existingUser = mock(io.gravitee.am.model.User.class);
        when(existingUser.getId()).thenReturn("user-id");
        when(existingUser.getSource()).thenReturn("user-idp");
        when(existingUser.getExternalId()).thenReturn("user-extid");
        when(existingUser.getUsername()).thenReturn("username");
        when(existingUser.getReferenceId()).thenReturn(DOMAIN_ID);
        when(existingUser.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(existingUser.getAdditionalInformation()).thenReturn(Map.of("attr1", "value-attr1"));

        User scimUser = mock(User.class);
        when(scimUser.getPassword()).thenReturn(PASSWORD);
        when(scimUser.isActive()).thenReturn(true);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.update(anyString(), any())).thenReturn(Single.just(idpUser));

        when(userRepository.findById(existingUser.getId())).thenReturn(Maybe.just(existingUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        ArgumentCaptor<io.gravitee.am.model.User> userCaptor = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        when(userRepository.update(any(), any())).thenReturn(Single.just(existingUser));
        when(groupService.findByMember(existingUser.getId())).thenReturn(Flowable.empty());
        when(passwordService.isValid(eq(PASSWORD), any(), any())).thenReturn(true);

        TestObserver<User> testObserver = userService.update(existingUser.getId(), scimUser, null, "/", null, null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        verify(userRepository, times(1)).update(userCaptor.capture(), any());
        verify(userProvider, never()).create(any());
        verify(userProvider).update(anyString(), any());
        verify(userProvider, never()).updatePassword(any(), eq(PASSWORD));
        assertTrue(userCaptor.getValue().isEnabled());
        assertTrue(userCaptor.getValue().getAdditionalInformation().containsKey("attr1"));
    }

    @Test
    public void shouldUpdate_UserWithExtId_and_NoPassword() {
        io.gravitee.am.model.User existingUser = mock(io.gravitee.am.model.User.class);
        when(existingUser.getId()).thenReturn("user-id");
        when(existingUser.getSource()).thenReturn("user-idp");
        when(existingUser.getExternalId()).thenReturn("user-extid");
        when(existingUser.getUsername()).thenReturn("username");
        when(existingUser.getReferenceId()).thenReturn(DOMAIN_ID);
        when(existingUser.getReferenceType()).thenReturn(ReferenceType.DOMAIN);

        User scimUser = mock(User.class);
        when(scimUser.isActive()).thenReturn(true);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.update(anyString(), any())).thenReturn(Single.just(idpUser));

        when(userRepository.findById(existingUser.getId())).thenReturn(Maybe.just(existingUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        ArgumentCaptor<io.gravitee.am.model.User> userCaptor = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        when(userRepository.update(any(), any())).thenReturn(Single.just(existingUser));
        when(groupService.findByMember(existingUser.getId())).thenReturn(Flowable.empty());

        TestObserver<User> testObserver = userService.update(existingUser.getId(), scimUser, null, "/", null, null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        verify(userRepository, times(1)).update(userCaptor.capture(), any());
        verify(userProvider, never()).create(any());
        verify(userProvider).update(anyString(), any());
        verify(userProvider, never()).updatePassword(any(), eq(PASSWORD));
        assertTrue(userCaptor.getValue().isEnabled());
    }

    @Test
    public void shouldNotUpdateUser_unknownIdentityProvider() {
        io.gravitee.am.model.User existingUser = mock(io.gravitee.am.model.User.class);
        when(existingUser.getId()).thenReturn("user-id");
        when(existingUser.getUsername()).thenReturn("username");
        when(existingUser.getReferenceId()).thenReturn(DOMAIN_ID);
        when(existingUser.getReferenceType()).thenReturn(ReferenceType.DOMAIN);

        User scimUser = mock(User.class);
        when(scimUser.getPassword()).thenReturn(PASSWORD);
        when(scimUser.isActive()).thenReturn(true);

        when(userRepository.findById(existingUser.getId())).thenReturn(Maybe.just(existingUser));
        when(identityProviderManager.getIdentityProvider(any())).thenReturn(null);
        ArgumentCaptor<io.gravitee.am.model.User> userCaptor = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        when(passwordService.isValid(anyString(),any(),any())).thenReturn(true);
        TestObserver<User> testObserver = userService.update(existingUser.getId(), scimUser, null, "/", null, null).test();
        testObserver.assertError(InvalidValueException.class);
        testObserver.assertError(throwable -> throwable.getMessage().equals("Identity Provider [null] can not be found."));

        verify(userRepository, never()).update(userCaptor.capture());
        verify(identityProviderManager, never()).getUserProvider(anyString());
    }

    @Test
    public void shouldPatchUser() throws Exception {
        final String userId = "userId";

        ObjectNode userNode = mock(ObjectNode.class);
        when(userNode.get("displayName")).thenReturn(new TextNode("my user"));

        Operation operation = mock(Operation.class);
        doAnswer(invocation -> {
            ObjectNode arg0 = invocation.getArgument(0);
            Assert.assertEquals("my user", arg0.get("displayName").asText());
            return null;
        }).when(operation).apply(any());

        PatchOp patchOp = mock(PatchOp.class);
        when(patchOp.getOperations()).thenReturn(Collections.singletonList(operation));

        User patchUser = mock(User.class);
        when(patchUser.getDisplayName()).thenReturn("my user 2");

        io.gravitee.am.model.User patchedUser = mock(io.gravitee.am.model.User.class);
        when(patchedUser.getId()).thenReturn(userId);
        when(patchedUser.getSource()).thenReturn("user-idp");
        when(patchedUser.getUsername()).thenReturn("username");
        when(patchedUser.getDisplayName()).thenReturn("my user 2");
        when(patchedUser.getReferenceId()).thenReturn(DOMAIN_ID);
        when(patchedUser.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(patchedUser.getAdditionalInformation()).thenReturn(Map.of("attr1", "value-attr1"));

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);
        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        when(objectMapper.convertValue(any(), eq(ObjectNode.class))).thenReturn(userNode);
        when(objectMapper.treeToValue(userNode, User.class)).thenReturn(patchUser);
        when(groupService.findByMember(userId)).thenReturn(Flowable.empty());
        when(userRepository.findById(userId)).thenReturn(Maybe.just(patchedUser));
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(tokenService.deleteByUser(any())).thenReturn(Completable.complete());
        doAnswer(invocation -> {
            io.gravitee.am.model.User userToUpdate = invocation.getArgument(0);
            Assert.assertEquals("my user 2", userToUpdate.getDisplayName());
            Assert.assertTrue(userToUpdate.getAdditionalInformation().containsKey("attr1"));
            return Single.just(userToUpdate);
        }).when(userRepository).update(any(), any());

        TestObserver<User> testObserver = userService.patch(userId, patchOp, null, "/", null, null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(g -> "my user 2".equals(g.getDisplayName()));
    }

    @Test
    public void shouldNotPatchUser_Invalid_patch_structure() throws Exception {
        final String userId = "userId";

        ObjectNode userNode = mock(ObjectNode.class);

        PatchOp patchOp = Json.decodeValue("""
                    { "schemas":
                           ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                         "Operations":[
                           {
                            "op":"add",
                            "path":"name",
                            "value": {
                                "givenName": "Poppy",
                                "familyName": "Seed",
                                "displayName": "Poppy Seed"
                            }
                           }
                         ]
                       }
                    """, PatchOp.class);

        io.gravitee.am.model.User patchedUser = mock(io.gravitee.am.model.User.class);
        when(patchedUser.getId()).thenReturn(userId);
        when(patchedUser.getUsername()).thenReturn("username");
        when(patchedUser.getDisplayName()).thenReturn("my user 2");
        when(patchedUser.getAdditionalInformation()).thenReturn(Map.of("attr1", "value-attr1"));

        when(objectMapper.convertValue(any(), eq(ObjectNode.class))).thenReturn(userNode);
        when(objectMapper.treeToValue(userNode, User.class)).thenThrow(mock(UnrecognizedPropertyException.class));
        when(groupService.findByMember(userId)).thenReturn(Flowable.empty());
        when(userRepository.findById(userId)).thenReturn(Maybe.just(patchedUser));

        TestObserver<User> testObserver = userService.patch(userId, patchOp, null, "/", null, null).test();
        testObserver.assertError(err -> err instanceof InvalidValueException);
        verify(userRepository, never()).update(any(), any());
    }

    @Test
    public void shouldPatchUser_customGraviteeUser() throws Exception {
        final String userId = "userId";

        ObjectNode userNode = mock(ObjectNode.class);
        when(userNode.get(Schema.SCHEMA_URI_CUSTOM_USER)).thenReturn(new TextNode("test"));
        when(userNode.has(Schema.SCHEMA_URI_CUSTOM_USER)).thenReturn(true);

        Operation operation = mock(Operation.class);
        doAnswer(invocation -> {
            ObjectNode arg0 = invocation.getArgument(0);
            Assert.assertEquals("test", arg0.get(Schema.SCHEMA_URI_CUSTOM_USER).asText());
            return null;
        }).when(operation).apply(any());

        PatchOp patchOp = mock(PatchOp.class);
        when(patchOp.getOperations()).thenReturn(Collections.singletonList(operation));

        GraviteeUser patchUser = mock(GraviteeUser.class);
        Map<String, Object> additionalInformation = Collections.singletonMap("customClaim", "customValue");
        when(patchUser.getAdditionalInformation()).thenReturn(additionalInformation);

        io.gravitee.am.model.User patchedUser = mock(io.gravitee.am.model.User.class);
        when(patchedUser.getId()).thenReturn(userId);
        when(patchedUser.getSource()).thenReturn("user-idp");
        when(patchedUser.getUsername()).thenReturn("username");
        when(patchedUser.getDisplayName()).thenReturn("my user 2");
        when(patchedUser.getReferenceId()).thenReturn(DOMAIN_ID);
        when(patchedUser.getReferenceType()).thenReturn(ReferenceType.DOMAIN);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);
        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        when(objectMapper.convertValue(any(), eq(ObjectNode.class))).thenReturn(userNode);
        when(objectMapper.treeToValue(userNode, GraviteeUser.class)).thenReturn(patchUser);
        when(groupService.findByMember(userId)).thenReturn(Flowable.empty());
        when(userRepository.findById(userId)).thenReturn(Maybe.just(patchedUser));
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(tokenService.deleteByUser(any())).thenReturn(Completable.complete());
        doAnswer(invocation -> {
            io.gravitee.am.model.User userToUpdate = invocation.getArgument(0);
            Assert.assertTrue(userToUpdate.getAdditionalInformation().containsKey("customClaim"));
            return Single.just(userToUpdate);
        }).when(userRepository).update(any(), any());

        TestObserver<User> testObserver = userService.patch(userId, patchOp, null, "/", null, null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(u -> ((GraviteeUser) u).getAdditionalInformation().containsKey("customClaim"));
    }

    @Test
    public void shouldDeleteUser() {
        final String userId = "userId";

        io.gravitee.am.model.User endUser = mock(io.gravitee.am.model.User.class);
        when(endUser.getId()).thenReturn(userId);
        when(endUser.getExternalId()).thenReturn("user-external-id");
        when(endUser.getSource()).thenReturn("user-idp");
        when(endUser.getReferenceId()).thenReturn(DOMAIN_ID);
        when(endUser.getReferenceType()).thenReturn(ReferenceType.DOMAIN);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.delete(any())).thenReturn(complete());

        when(userRepository.findById(userId)).thenReturn(Maybe.just(endUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(userRepository.delete(userId)).thenReturn(complete());
        when(userActivityService.deleteByDomainAndUser(domain, userId)).thenReturn(complete());
        when(rateLimiterService.deleteByUser(any())).thenReturn(complete());
        when(passwordHistoryService.deleteByUser(any(), eq(userId))).thenReturn(complete());
        when(verifyAttemptService.deleteByUser(any())).thenReturn(complete());
        when(credentialService.deleteByUserId(any(), eq(userId))).thenReturn(complete());


        var testObserver = userService.delete(userId, null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        verify(userRepository, times(1)).delete(userId);
        verify(identityProviderManager, times(1)).getUserProvider(anyString());
        verify(userProvider, times(1)).delete(anyString());
    }

    @Test
    public void shouldDeleteUser_noExternalProvider() {
        final String userId = "userId";

        io.gravitee.am.model.User endUser = mock(io.gravitee.am.model.User.class);
        when(endUser.getId()).thenReturn(userId);
        when(endUser.getSource()).thenReturn("user-idp");
        when(endUser.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(endUser.getReferenceId()).thenReturn(DOMAIN_ID);

        UserProvider userProvider = mock(UserProvider.class);

        when(userRepository.findById(userId)).thenReturn(Maybe.just(endUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());
        when(userRepository.delete(userId)).thenReturn(complete());
        when(userActivityService.deleteByDomainAndUser(domain, userId)).thenReturn(complete());
        when(rateLimiterService.deleteByUser(any())).thenReturn(complete());
        when(passwordHistoryService.deleteByUser(any(), any())).thenReturn(complete());
        when(verifyAttemptService.deleteByUser(any())).thenReturn(complete());
        when(credentialService.deleteByUserId(any(), eq(userId))).thenReturn(complete());

        var testObserver = userService.delete(userId, null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        verify(userRepository, times(1)).delete(userId);
        verify(identityProviderManager, times(1)).getUserProvider(anyString());
        verify(userProvider, never()).delete(anyString());
    }

    @Test
    public void shouldThrowAnExceptionAndReportAuditIfPasswordIsInvalidOnCreateUser(){
        // given
        String clientId = "clientId";
        Client client = new Client();
        client.setId(clientId);

        User user = new User();
        user.setUserName("username");
        user.setSource("unknown-idp");
        user.setPassword("123");

        // when
        when(identityProviderManager.getIdentityProvider(any())).thenReturn(new IdentityProvider());
        when(passwordPolicyManager.getPolicy(any(),any())).thenReturn(Optional.of(new PasswordPolicy()));
        when(passwordService.isValid(anyString(),any(),any())).thenReturn(false);

        TestObserver<User> observer = new TestObserver<>();
        userService.create(user,"unknown-idp", "", new DefaultUser(), client).subscribe(observer);

        // then
        observer.assertError(throwable -> throwable.getMessage().equals("The provided password does not meet the password policy requirements."));
        verify(auditService,atMostOnce()).report(any());
        verify(auditService).report(argThat(builder -> Status.FAILURE.equals(builder.build(new ObjectMapper()).getOutcome().getStatus())));
        verify(auditService).report(argThat(builder -> builder.build(new ObjectMapper()).getType().equals(EventType.USER_CREATED)));
    }

    @Test
    public void shouldThrowAnExceptionAndReportAuditIfPasswordIsInvalidOnPatchUser() throws JsonProcessingException {
        // given
        Client client = new Client();
        client.setId("clientId");

        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setId("user-id");
        user.setUsername("username");
        user.setSource("unknown-idp");
        user.setPassword("myPassword");
        user.setReferenceId("domainId");
        user.setReferenceType(ReferenceType.DOMAIN);

        ObjectNode userNode = mock(ObjectNode.class);
        when(userNode.get(Schema.SCHEMA_URI_CUSTOM_USER)).thenReturn(new TextNode("test"));
        when(userNode.has(Schema.SCHEMA_URI_CUSTOM_USER)).thenReturn(true);

        GraviteeUser patchUser = mock(GraviteeUser.class);
        Map<String, Object> additionalInformation = Collections.singletonMap("customClaim", "customValue");
        when(patchUser.getAdditionalInformation()).thenReturn(additionalInformation);
        when(patchUser.getPassword()).thenReturn("newPass");

        Operation operation = mock(Operation.class);
        doAnswer(invocation -> {
            ObjectNode arg0 = invocation.getArgument(0);
            Assert.assertEquals("test", arg0.get(Schema.SCHEMA_URI_CUSTOM_USER).asText());
            return null;
        }).when(operation).apply(any());

        PatchOp patchOp = mock(PatchOp.class);

        // when
        when(userRepository.findById("user-id")).thenReturn(Maybe.just(user));
        when(groupService.findByMember("user-id")).thenReturn(Flowable.empty());
        when(patchOp.getOperations()).thenReturn(Collections.singletonList(operation));
        when(objectMapper.convertValue(any(), eq(ObjectNode.class))).thenReturn(userNode);
        when(objectMapper.treeToValue(userNode, GraviteeUser.class)).thenReturn(patchUser);
        when(passwordService.isValid(anyString(),any(),any())).thenReturn(false);

        TestObserver<User> observer = new TestObserver<>();
        userService.patch(user.getId(), patchOp, "unknown-idp","", new DefaultUser(), client).subscribe(observer);

        // then
        observer.assertError(throwable -> throwable.getMessage().equals("The provided password does not meet the password policy requirements."));
        verify(auditService,atMostOnce()).report(any());
        verify(auditService).report(argThat(builder -> Status.FAILURE.equals(builder.build(new ObjectMapper()).getOutcome().getStatus())));
        verify(auditService).report(argThat(builder -> builder.build(new ObjectMapper()).getType().equals(EventType.USER_UPDATED)));
    }

    @Test
    public void shouldThrowAnExceptionAndReportAuditIfPasswordIsInvalidOnUpdateUser(){
        // given
        io.gravitee.am.model.User existingUser = new io.gravitee.am.model.User();
        existingUser.setId("user-id");
        existingUser.setUsername("username");
        existingUser.setPassword("myPassword");
        existingUser.setReferenceId("domainId");
        existingUser.setReferenceType(ReferenceType.DOMAIN);

        User scimUser = mock(User.class);
        when(scimUser.getPassword()).thenReturn(PASSWORD);
        when(scimUser.isActive()).thenReturn(true);

        // when
        when(userRepository.findById(existingUser.getId())).thenReturn(Maybe.just(existingUser));
        when(passwordService.isValid(anyString(),any(),any())).thenReturn(false);

        TestObserver<User> testObserver = userService.update(existingUser.getId(), scimUser, null, "", new DefaultUser(), new Client()).test();
        testObserver.assertError(InvalidValueException.class);

        // then
        testObserver.assertError(throwable -> throwable.getMessage().equals("The provided password does not meet the password policy requirements."));
        verify(auditService,atMostOnce()).report(any());
        verify(auditService).report(argThat(builder -> Status.FAILURE.equals(builder.build(new ObjectMapper()).getOutcome().getStatus())));
        verify(auditService).report(argThat(builder -> builder.build(new ObjectMapper()).getType().equals(EventType.USER_UPDATED)));
    }

    @Test
    public void shouldCreateUser_with_lastPasswordReset() {

        String lastPasswordResetDate = new Date().toInstant().toString();

        GraviteeUser newUser = mock(GraviteeUser.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");
        when(newUser.getPassword()).thenReturn(null);
        Map<String, Object> ai = new HashMap<>();
        ai.put("lastPasswordReset", lastPasswordResetDate);
        when(newUser.getAdditionalInformation()).thenReturn(ai);

        when(userRepository.findByUsernameAndSource(any(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());

        ArgumentCaptor<io.gravitee.am.model.User> newUserDefinition = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(DOMAIN_ID);
        user.setAdditionalInformation(ai);
        when(userRepository.create(newUserDefinition.capture())).thenReturn(Single.just(user));

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        testObserver.assertValue(u -> ((GraviteeUser) u).getAdditionalInformation().get("lastPasswordReset") != null);
        assertFalse(newUserDefinition.getValue().isInternal());
        assertTrue(newUserDefinition.getValue().isEnabled());
    }

    @Test
    public void shouldNotCreateUser_with_lastPasswordResetInFuture() {
        final var aLongTime = Duration.ofDays(4);
        String lastPasswordResetDate = Instant.now().plus(aLongTime).toString();

        GraviteeUser newUser = mock(GraviteeUser.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");
        when(newUser.getPassword()).thenReturn(null);
        Map<String, Object> ai = new HashMap<>();
        ai.put("lastPasswordReset", lastPasswordResetDate);
        when(newUser.getAdditionalInformation()).thenReturn(ai);

        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(DOMAIN_ID);
        user.setAdditionalInformation(ai);

        userService.create(newUser, null, "/", null, new Client()).test()
                .assertError(ex -> ex instanceof UserInvalidException && ex.getMessage().equals("lastPasswordReset cannot be in the future"));
    }

    @Test
    public void shouldThrowExceptionOnCreateUser_with_invalidLastPasswordReset() {

        String lastPasswordResetDate = "123";

        GraviteeUser newUser = mock(GraviteeUser.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");
        when(newUser.getPassword()).thenReturn(null);
        Map<String, Object> ai = new HashMap<>();
        ai.put("lastPasswordReset", lastPasswordResetDate);
        when(newUser.getAdditionalInformation()).thenReturn(ai);

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertError(err -> err instanceof UserInvalidException);
    }

    @Test
    public void shouldCreateUser_with_preRegistration() {

        GraviteeUser newUser = mock(GraviteeUser.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");
        when(newUser.getPassword()).thenReturn(null);
        Map<String, Object> ai = new HashMap<>();
        ai.put("preRegistration", true);
        when(newUser.getAdditionalInformation()).thenReturn(ai);

        when(userRepository.findByUsernameAndSource(any(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());

        ArgumentCaptor<io.gravitee.am.model.User> newUserDefinition = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(DOMAIN_ID);
        user.setAdditionalInformation(ai);
        user.setPreRegistration(true);
        when(userRepository.create(newUserDefinition.capture())).thenReturn(Single.just(user));

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        testObserver.assertValue(u -> ((GraviteeUser) u).getAdditionalInformation().get("preRegistration") != null);
        assertFalse(newUserDefinition.getValue().isInternal());
        assertTrue(newUserDefinition.getValue().isEnabled());
        verify(emailService, times(1)).send(any(),any(),any());
    }

    @Test
    public void shouldThrowExceptionOnCreateUser_with_invalidPreRegistration() {

        GraviteeUser newUser = mock(GraviteeUser.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");
        when(newUser.getPassword()).thenReturn(null);
        Map<String, Object> ai = new HashMap<>();
        ai.put("lastPasswordReset", "abcd");
        when(newUser.getAdditionalInformation()).thenReturn(ai);

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertError(err -> err instanceof UserInvalidException);
    }


}
