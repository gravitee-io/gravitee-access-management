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
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.model.Operation;
import io.gravitee.am.gateway.handler.scim.model.PatchOp;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.scim.service.impl.UserServiceImpl;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.validators.PasswordValidator;
import io.gravitee.am.service.validators.UserValidator;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Assert;
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
import java.util.Set;

import static org.junit.Assert.assertTrue;
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

    @Spy
    private UserValidator userValidator = new UserValidator();

    @Mock
    private UserRepository userRepository;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private Domain domain;

    @Mock
    private RoleService roleService;

    @Mock
    private GroupService groupService;

    @Mock
    private PasswordValidator passwordValidator;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    public void shouldCreateUser_invalid_identity_provider() {
        final String domainId = "domain";

        User newUser = mock(User.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");

        when(domain.getId()).thenReturn(domainId);
        when(userRepository.findByUsernameAndSource(eq(ReferenceType.DOMAIN), anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());

        TestObserver<User> testObserver = userService.create(newUser, "/").test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidValueException.class);
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

        TestObserver<User> testObserver = userService.create(newUser, "/").test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidValueException.class);
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
        when(userRepository.create(any())).thenReturn(Single.just(createdUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(roleService.findByIdIn(newUser.getRoles())).thenReturn(Single.just(roles));

        TestObserver<User> testObserver = userService.create(newUser, "/").test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void shouldUpdateUser_status_enabled() {
        io.gravitee.am.model.User existingUser = mock(io.gravitee.am.model.User.class);
        when(existingUser.getId()).thenReturn("user-id");
        when(existingUser.getSource()).thenReturn("user-idp");
        when(existingUser.getUsername()).thenReturn("username");

        User scimUser = mock(User.class);
        when(scimUser.getPassword()).thenReturn("user-password");
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
        ArgumentCaptor<io.gravitee.am.model.User> userCaptor = ArgumentCaptor.forClass(io.gravitee.am.model.User.class);
        when(userRepository.update(any())).thenReturn(Single.just(existingUser));
        when(groupService.findByMember(existingUser.getId())).thenReturn(Flowable.empty());
        when(passwordValidator.isValid("user-password")).thenReturn(true);

        TestObserver<User> testObserver = userService.update(existingUser.getId(), scimUser, "/").test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        verify(userRepository, times(1)).update(userCaptor.capture());
        assertTrue(userCaptor.getValue().isEnabled());
    }

    @Test
    public void shouldPatchUser() throws Exception {
        final String domainId = "domain";
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

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.User.class);
        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        when(domain.getName()).thenReturn(domainName);
        when(objectMapper.convertValue(any(), eq(ObjectNode.class))).thenReturn(userNode);
        when(objectMapper.treeToValue(userNode, User.class)).thenReturn(patchUser);
        when(groupService.findByMember(userId)).thenReturn(Flowable.empty());
        when(userRepository.findById(userId)).thenReturn(Maybe.just(patchedUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        doAnswer(invocation -> {
            io.gravitee.am.model.User userToUpdate = invocation.getArgument(0);
            Assert.assertTrue(userToUpdate.getDisplayName().equals("my user 2"));
            return Single.just(userToUpdate);
        }).when(userRepository).update(any());

        TestObserver<User> testObserver = userService.patch(userId, patchOp, "/").test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(g -> "my user 2".equals(g.getDisplayName()));
    }

}
