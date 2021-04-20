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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.scim.service.impl.UserServiceImpl;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Role;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.RoleService;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    @InjectMocks
    private UserService userService = new UserServiceImpl();

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

    @Test
    public void shouldCreateUser_invalid_identity_provider() {
        final String domainId = "domain";

        User newUser = mock(User.class);
        when(newUser.getSource()).thenReturn("unknown-idp");
        when(newUser.getUserName()).thenReturn("username");

        when(domain.getId()).thenReturn(domainId);
        when(userRepository.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
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
        when(userRepository.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
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
        when(userRepository.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(userRepository.create(any())).thenReturn(Single.just(createdUser));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(roleService.findByIdIn(newUser.getRoles())).thenReturn(Single.just(roles));

        TestObserver<User> testObserver = userService.create(newUser, "/").test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void shouldUpdateUser_status_enabled() {
        final String domainId = "domain";

        io.gravitee.am.model.User existingUser = mock(io.gravitee.am.model.User.class);
        when(existingUser.getId()).thenReturn("user-id");
        when(existingUser.getId()).thenReturn("user-external-id");
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
        when(groupService.findByMember(existingUser.getId())).thenReturn(Single.just(Collections.emptyList()));

        TestObserver<User> testObserver = userService.update(existingUser.getId(), scimUser, "/").test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();

        verify(userRepository, times(1)).update(userCaptor.capture());
        assertTrue(userCaptor.getValue().isEnabled());
    }
}
