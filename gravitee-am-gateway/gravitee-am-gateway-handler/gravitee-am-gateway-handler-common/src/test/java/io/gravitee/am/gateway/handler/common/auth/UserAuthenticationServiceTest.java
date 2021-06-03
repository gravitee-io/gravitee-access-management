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
package io.gravitee.am.gateway.handler.common.auth;

import io.gravitee.am.common.exception.authentication.AccountDisabledException;
import io.gravitee.am.gateway.handler.common.auth.user.impl.UserAuthenticationServiceImpl;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationService;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserAuthenticationServiceTest {

    @InjectMocks
    private UserAuthenticationService userAuthenticationService = new UserAuthenticationServiceImpl();

    @Mock
    private UserService userService;

    @Mock
    private Domain domain;

    @Test
    public void shouldConnect_unknownUser() {
        String domainId = "Domain";
        String username = "foo";
        String source = "SRC";
        String id = "id";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getUsername()).thenReturn(username);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);

        User createdUser = mock(User.class);
        when(createdUser.isEnabled()).thenReturn(true);

        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.empty());
        when(userService.findByDomainAndUsernameAndSource(domainId, username, source)).thenReturn(Maybe.empty());
        when(userService.create(any())).thenReturn(Single.just(createdUser));
        when(userService.enhance(createdUser)).thenReturn(Single.just(createdUser));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, times(1)).create(any());
        verify(userService, never()).update(any());
    }

    @Test
    public void shouldConnect_knownUser() {
        String domainId = "Domain";
        String source = "SRC";
        String id = "id";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);

        User updatedUser = mock(User.class);
        when(updatedUser.isEnabled()).thenReturn(true);

        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(mock(User.class)));
        when(userService.update(any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, never()).create(any());
        verify(userService, times(1)).update(any());
    }

    @Test
    public void shouldNotConnect_accountDisabled() {
        String domainId = "Domain";
        String source = "SRC";
        String id = "id";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);

        User updatedUser = mock(User.class);
        when(updatedUser.isEnabled()).thenReturn(false);

        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(mock(User.class)));
        when(userService.update(any())).thenReturn(Single.just(updatedUser));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(AccountDisabledException.class);
    }

    @Test
    public void shouldConnect_unknownUser_withRoles() {
        String domainId = "Domain";
        String username = "foo";
        String source = "SRC";
        String id = "id";

        Role role1= new Role();
        role1.setId("idp-role");
        Role role2 = new Role();
        role2.setId("idp2-role");
        Set<Role> roles = new HashSet<>(Arrays.asList(role1, role2));

        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getUsername()).thenReturn(username);
        when(user.getId()).thenReturn(id);
        when(user.getRoles()).thenReturn(Arrays.asList("idp-role", "idp2-role"));
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);

        User createdUser = mock(User.class);
        when(createdUser.isEnabled()).thenReturn(true);
        when(createdUser.getRoles()).thenReturn(Arrays.asList("idp-role", "idp2-role"));

        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.empty());
        when(userService.findByDomainAndUsernameAndSource(domainId, username, source)).thenReturn(Maybe.empty());
        when(userService.create(any())).thenReturn(Single.just(createdUser));
        when(userService.enhance(createdUser)).thenReturn(Single.just(createdUser));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(user1 -> user1.getRoles().size() == 2);
    }

    @Test
    public void shouldConnect_knownUser_withRoles() {
        String domainId = "Domain";
        String username = "foo";
        String source = "SRC";
        String id = "id";

        Role role1= new Role();
        role1.setId("idp-role");
        Role role2 = new Role();
        role2.setId("idp2-role");
        Set<Role> roles = new HashSet<>(Arrays.asList(role1, role2));

        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getUsername()).thenReturn(username);
        when(user.getId()).thenReturn(id);
        when(user.getRoles()).thenReturn(Arrays.asList("idp-role", "idp2-role"));
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);

        User updatedUser = mock(User.class);
        when(updatedUser.isEnabled()).thenReturn(true);
        when(updatedUser.getRoles()).thenReturn(Arrays.asList("idp-role", "idp2-role"));

        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(mock(User.class)));
        when(userService.update(any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(user1 -> user1.getRoles().size() == 2);
    }

    @Test
    public void shouldConnect_knownUser_withRoles_fromGroup() {
        String domainId = "Domain";
        String username = "foo";
        String source = "SRC";
        String id = "id";

        Group group = mock(Group.class);

        Role role1= new Role();
        role1.setId("idp-role");
        Role role2 = new Role();
        role2.setId("idp2-role");
        Set<Role> roles = new HashSet<>(Arrays.asList(role1, role2));

        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getUsername()).thenReturn(username);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);

        User updatedUser = mock(User.class);
        when(updatedUser.isEnabled()).thenReturn(true);
        when(updatedUser.getRoles()).thenReturn(Arrays.asList("group-role", "group2-role"));

        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(mock(User.class)));
        when(userService.update(any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(user1 -> user1.getRoles().size() == 2);
    }
}
