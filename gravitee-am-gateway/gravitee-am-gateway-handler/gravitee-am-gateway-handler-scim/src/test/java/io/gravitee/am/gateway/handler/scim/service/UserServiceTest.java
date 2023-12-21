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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.gravitee.am.common.scim.Schema;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.exception.UniquenessException;
import io.gravitee.am.gateway.handler.scim.model.GraviteeUser;
import io.gravitee.am.gateway.handler.scim.model.Operation;
import io.gravitee.am.gateway.handler.scim.model.PatchOp;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.scim.service.impl.UserServiceImpl;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.PasswordHistory;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.RateLimiterService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.am.service.VerifyAttemptService;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.validators.email.EmailValidatorImpl;
import io.gravitee.am.service.validators.user.UserValidator;
import io.gravitee.am.service.validators.user.UserValidatorImpl;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.UUID;
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
import java.util.Map;
import java.util.Set;

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
public class UserServiceTest {

    public static final String PASSWORD = "user-password";
    @InjectMocks
    private UserService userService = new UserServiceImpl();

    @Spy
    private UserValidator userValidator = new UserValidatorImpl(
            NAME_STRICT_PATTERN,
            NAME_LAX_PATTERN,
            USERNAME_PATTERN,
            new EmailValidatorImpl(EMAIL_PATTERN)
    );

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserActivityService userActivityService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private Domain domain;

    @Mock
    private RoleService roleService;

    @Mock
    private GroupService groupService;

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

    @Before
    public void setUp() {
        when(passwordHistoryService.addPasswordToHistory(any(), any(), any(), any(), any(), any())).thenReturn(Maybe.just(new PasswordHistory()));
        when(userRepository.findByExternalIdAndSource(any(), any(), any(), any())).thenReturn(Maybe.empty());
    }

    @Test
    public void shouldCreateUser_no_user_provider() {
        final String domainId = "domain";

        User newUser = mock(User.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");
        when(domain.getId()).thenReturn(domainId);
        when(userRepository.findByUsernameAndSource(eq(ReferenceType.DOMAIN), anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());

        ArgumentCaptor<io.gravitee.am.model.User> newUserDefinition = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        when(userRepository.create(newUserDefinition.capture())).thenReturn(Single.just(new io.gravitee.am.model.User()));

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        assertFalse(newUserDefinition.getValue().isInternal());
    }

    @Test
    public void shouldCreateUser_invalid_roles() {
        final String domainId = "domain";

        User newUser = mock(User.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");
        when(newUser.getRoles()).thenReturn(Arrays.asList("role-wrong-1", "role-wrong-2"));

        when(domain.getId()).thenReturn(domainId);
        when(userRepository.findByUsernameAndSource(eq(ReferenceType.DOMAIN), anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(roleService.findByIdIn(newUser.getRoles())).thenReturn(Single.just(Collections.emptySet()));

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidValueException.class);
    }

    @Test
    public void shouldNotCreateUserWhenUsernameAlreadyUsed() {
        var externalId = "external-id";
        var user = new io.gravitee.am.model.User();
        user.setExternalId(externalId);
        var pwd = UUID.randomUUID().toString();

        when(userRepository.findByUsernameAndSource(eq(ReferenceType.DOMAIN), anyString(), anyString(), anyString())).thenReturn(Maybe.just(user));
        when(passwordService.isValid(any(), any(), any())).thenReturn(true);
        when(domain.getId()).thenReturn("domain");

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

        when(userRepository.findByExternalIdAndSource(any(), any(), any(), any())).thenReturn(Maybe.just(user));
        when(userRepository.findByUsernameAndSource(eq(ReferenceType.DOMAIN), anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(passwordService.isValid(any(), any(), any())).thenReturn(true);
        when(domain.getId()).thenReturn("domain");

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
    public void shouldCreateUser() {
        final String domainId = "domain";

        User newUser = mock(User.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");
        when(newUser.getRoles()).thenReturn(Arrays.asList("role-1", "role-2"));

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        io.gravitee.am.model.User createdUser = mock(io.gravitee.am.model.User.class);

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(domain.getId()).thenReturn(domainId);
        when(userRepository.findByUsernameAndSource(eq(ReferenceType.DOMAIN), anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(roleService.findByIdIn(newUser.getRoles())).thenReturn(Single.just(roles));

        ArgumentCaptor<io.gravitee.am.model.User> newUserDefinition = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        when(userRepository.create(newUserDefinition.capture())).thenReturn(Single.just(createdUser));

        TestObserver<User> testObserver = userService.create(newUser, null, "/", null, new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        assertTrue(newUserDefinition.getValue().isInternal());
    }

    @Test
    public void shouldUpdateUser_status_enabled() {
        io.gravitee.am.model.User existingUser = mock(io.gravitee.am.model.User.class);
        when(existingUser.getId()).thenReturn("user-id");
        when(existingUser.getSource()).thenReturn("user-idp");
        when(existingUser.getUsername()).thenReturn("username");

        User scimUser = mock(User.class);
        when(scimUser.getPassword()).thenReturn(PASSWORD);
        when(scimUser.isActive()).thenReturn(true);

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
        assertTrue(userCaptor.getValue().isEnabled());
    }

    @Test
    public void shouldUpdateUser_roles_entitlements() {
        io.gravitee.am.model.User existingUser = mock(io.gravitee.am.model.User.class);
        when(existingUser.getId()).thenReturn("user-id");
        when(existingUser.getSource()).thenReturn("user-idp");
        when(existingUser.getRoles()).thenReturn(Arrays.asList("r1"));
        when(existingUser.getEntitlements()).thenReturn(Arrays.asList("e1"));
        when(existingUser.getSource()).thenReturn("user-idp");

        when(existingUser.getUsername()).thenReturn("username");

        User scimUser = mock(User.class);
        when(scimUser.getPassword()).thenReturn(PASSWORD);
        when(scimUser.isActive()).thenReturn(true);
        when(scimUser.getRoles()).thenReturn(Arrays.asList("r2"));
        when(scimUser.getEntitlements()).thenReturn(Arrays.asList("e1", "e2"));

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        final Role r2 = new Role();
        r2.setId("r2");
        when(roleService.findByIdIn(any())).thenReturn(Single.just(Set.of(r2)));
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
        when(existingUser.getAdditionalInformation()).thenReturn(Map.of("attr1", "value-attr1"));

        User scimUser = mock(User.class);
        when(scimUser.getPassword()).thenReturn(PASSWORD);
        when(scimUser.isActive()).thenReturn(true);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.update(anyString(), any())).thenReturn(Single.just(idpUser));

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

        User scimUser = mock(User.class);
        when(scimUser.isActive()).thenReturn(true);

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.update(anyString(), any())).thenReturn(Single.just(idpUser));

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

        User scimUser = mock(User.class);
        when(scimUser.getPassword()).thenReturn(PASSWORD);
        when(scimUser.isActive()).thenReturn(true);

        when(userRepository.findById(existingUser.getId())).thenReturn(Maybe.just(existingUser));
        ArgumentCaptor<io.gravitee.am.model.User> userCaptor = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);

        TestObserver<User> testObserver = userService.update(existingUser.getId(), scimUser, null, "/", null, null).test();
        testObserver.assertError(InvalidValueException.class);

        verify(userRepository, never()).update(userCaptor.capture());
        verify(identityProviderManager, never()).getUserProvider(anyString());
    }

    @Test
    public void shouldPatchUser() throws Exception {
        final String domainName = "domainName";
        final String userId = "userId";

        ObjectNode userNode = mock(ObjectNode.class);
        when(userNode.get("displayName")).thenReturn(new TextNode("my user"));

        Operation operation = mock(Operation.class);
        doAnswer(invocation -> {
            ObjectNode arg0 = invocation.getArgument(0);
            Assert.assertTrue(arg0.get("displayName").asText().equals("my user"));
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
        when(patchedUser.getAdditionalInformation()).thenReturn(Map.of("attr1", "value-attr1"));

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);
        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        when(domain.getName()).thenReturn(domainName);
        when(objectMapper.convertValue(any(), eq(ObjectNode.class))).thenReturn(userNode);
        when(objectMapper.treeToValue(userNode, User.class)).thenReturn(patchUser);
        when(groupService.findByMember(userId)).thenReturn(Flowable.empty());
        when(userRepository.findById(userId)).thenReturn(Maybe.just(patchedUser));
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        doAnswer(invocation -> {
            io.gravitee.am.model.User userToUpdate = invocation.getArgument(0);
            Assert.assertTrue(userToUpdate.getDisplayName().equals("my user 2"));
            Assert.assertTrue(userToUpdate.getAdditionalInformation().containsKey("attr1"));
            return Single.just(userToUpdate);
        }).when(userRepository).update(any(), any());

        TestObserver<User> testObserver = userService.patch(userId, patchOp, null, "/", null, null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(g -> "my user 2".equals(g.getDisplayName()));
    }

    @Test
    public void shouldPatchUser_customGraviteeUser() throws Exception {
        final String domainName = "domainName";
        final String userId = "userId";

        ObjectNode userNode = mock(ObjectNode.class);
        when(userNode.get(Schema.SCHEMA_URI_CUSTOM_USER)).thenReturn(new TextNode("test"));
        when(userNode.has(Schema.SCHEMA_URI_CUSTOM_USER)).thenReturn(true);

        Operation operation = mock(Operation.class);
        doAnswer(invocation -> {
            ObjectNode arg0 = invocation.getArgument(0);
            Assert.assertTrue(arg0.get(Schema.SCHEMA_URI_CUSTOM_USER).asText().equals("test"));
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

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);
        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        when(domain.getName()).thenReturn(domainName);
        when(objectMapper.convertValue(any(), eq(ObjectNode.class))).thenReturn(userNode);
        when(objectMapper.treeToValue(userNode, GraviteeUser.class)).thenReturn(patchUser);
        when(groupService.findByMember(userId)).thenReturn(Flowable.empty());
        when(userRepository.findById(userId)).thenReturn(Maybe.just(patchedUser));
        when(identityProviderManager.getIdentityProvider(anyString())).thenReturn(new IdentityProvider());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
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

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.delete(any())).thenReturn(complete());

        when(userRepository.findById(userId)).thenReturn(Maybe.just(endUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(userRepository.delete(userId)).thenReturn(complete());
        when(userActivityService.deleteByDomainAndUser(domain.getId(), userId)).thenReturn(complete());
        when(rateLimiterService.deleteByUser(any())).thenReturn(complete());
        when(passwordHistoryService.deleteByUser(userId)).thenReturn(complete());
        when(verifyAttemptService.deleteByUser(any())).thenReturn(complete());


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

        UserProvider userProvider = mock(UserProvider.class);

        when(userRepository.findById(userId)).thenReturn(Maybe.just(endUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());
        when(userRepository.delete(userId)).thenReturn(complete());
        when(userActivityService.deleteByDomainAndUser(domain.getId(), userId)).thenReturn(complete());
        when(rateLimiterService.deleteByUser(any())).thenReturn(complete());
        when(passwordHistoryService.deleteByUser(any())).thenReturn(complete());
        when(verifyAttemptService.deleteByUser(any())).thenReturn(complete());

        var testObserver = userService.delete(userId, null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        verify(userRepository, times(1)).delete(userId);
        verify(identityProviderManager, times(1)).getUserProvider(anyString());
        verify(userProvider, never()).delete(anyString());
    }
}
