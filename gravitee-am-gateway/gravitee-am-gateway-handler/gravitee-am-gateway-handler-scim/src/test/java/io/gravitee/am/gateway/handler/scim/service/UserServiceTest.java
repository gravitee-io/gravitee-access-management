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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

}
