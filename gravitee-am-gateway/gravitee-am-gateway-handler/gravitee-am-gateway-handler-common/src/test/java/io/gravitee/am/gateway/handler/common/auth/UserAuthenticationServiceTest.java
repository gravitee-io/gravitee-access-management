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

import io.gravitee.am.gateway.handler.common.auth.impl.UserAuthenticationServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.authentication.AccountDisabledException;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

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
    private GroupService groupService;

    @Mock
    private RoleService roleService;

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
        when(createdUser.getId()).thenReturn(id);
        when(createdUser.isEnabled()).thenReturn(true);

        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.empty());
        when(userService.findByDomainAndUsernameAndSource(domainId, username, source)).thenReturn(Maybe.empty());
        when(userService.create(any())).thenReturn(Single.just(createdUser));
        when(groupService.findByMember(createdUser.getId())).thenReturn(Single.just(Collections.emptyList()));

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
        when(updatedUser.getId()).thenReturn(id);
        when(updatedUser.isEnabled()).thenReturn(true);

        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(mock(User.class)));
        when(userService.update(any())).thenReturn(Single.just(updatedUser));
        when(groupService.findByMember(updatedUser.getId())).thenReturn(Single.just(Collections.emptyList()));

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
        when(createdUser.getId()).thenReturn(id);
        when(createdUser.isEnabled()).thenReturn(true);
        when(createdUser.getRoles()).thenReturn(Arrays.asList("idp-role", "idp2-role"));

        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.empty());
        when(userService.findByDomainAndUsernameAndSource(domainId, username, source)).thenReturn(Maybe.empty());
        when(userService.create(any())).thenReturn(Single.just(createdUser));
        when(groupService.findByMember(createdUser.getId())).thenReturn(Single.just(Collections.emptyList()));
        when(roleService.findByIdIn(any())).thenReturn(Single.just(roles));

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
        when(updatedUser.getId()).thenReturn(id);
        when(updatedUser.isEnabled()).thenReturn(true);
        when(updatedUser.getRoles()).thenReturn(Arrays.asList("idp-role", "idp2-role"));

        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(mock(User.class)));
        when(userService.update(any())).thenReturn(Single.just(updatedUser));
        when(groupService.findByMember(updatedUser.getId())).thenReturn(Single.just(Collections.emptyList()));
        when(roleService.findByIdIn(any())).thenReturn(Single.just(roles));

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
        when(group.getRoles()).thenReturn(Arrays.asList("group-role", "group-role"));

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
        when(updatedUser.getId()).thenReturn(id);
        when(updatedUser.isEnabled()).thenReturn(true);
        when(updatedUser.getRoles()).thenReturn(Arrays.asList("group-role", "group2-role"));

        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(mock(User.class)));
        when(userService.update(any())).thenReturn(Single.just(updatedUser));
        when(groupService.findByMember(updatedUser.getId())).thenReturn(Single.just(Collections.singletonList(group)));
        when(roleService.findByIdIn(any())).thenReturn(Single.just(roles));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(user1 -> user1.getRoles().size() == 2);
        verify(roleService, times(1)).findByIdIn(any());
    }
}
