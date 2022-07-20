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
import io.gravitee.am.common.exception.authentication.AccountLockedException;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationService;
import io.gravitee.am.gateway.handler.common.auth.user.impl.UserAuthenticationServiceImpl;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.gateway.api.Request;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

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

    @Mock
    private IdentityProviderManager identityProviderManager;

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
        additionalInformation.put("op_id_token", "somevalue");
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
        verify(userService, times(1)).create(argThat(u -> u.getAdditionalInformation().containsKey("op_id_token")));
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
        final User foundUser = new User();
        foundUser.setAccountNonLocked(true);

        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, never()).create(any());
        verify(userService, times(1)).update(argThat(u -> u.isAccountNonLocked()));
    }


    @Test
    public void shouldConnect_knownUser_NotLockedAnymore() {
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
        final User foundUser = new User();
        foundUser.setAccountNonLocked(false);
        foundUser.setAccountLockedUntil(new Date(Instant.now().minusSeconds(60).toEpochMilli()));

        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, never()).create(any());
        verify(userService, times(1)).update(argThat(u -> u.isAccountNonLocked()));
    }

    @Test
    public void shouldNotConnect_accountLocked() {
        String domainId = "Domain";
        String source = "SRC";
        String id = "id";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);

        when(domain.getId()).thenReturn(domainId);
        final User foundUser = mock(User.class);
        when(foundUser.isAccountNonLocked()).thenReturn(false);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertError(AccountLockedException.class);
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
        final User foundUser = mock(User.class);
        when(foundUser.isAccountNonLocked()).thenReturn(true);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
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
        final User foundUser = mock(User.class);
        when(foundUser.isAccountNonLocked()).thenReturn(true);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
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

        final User foundUser = mock(User.class);
        when(foundUser.isAccountNonLocked()).thenReturn(true);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(user1 -> user1.getRoles().size() == 2);
    }

    @Test
    public void shouldConnect_knownUser_with_OpIdToken() {
        String domainId = "Domain";
        String username = "foo";
        String source = "SRC";
        String id = "id";

        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getUsername()).thenReturn(username);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        additionalInformation.put("op_id_token", "token2");
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);

        User updatedUser = mock(User.class);
        when(updatedUser.isEnabled()).thenReturn(true);

        when(domain.getId()).thenReturn(domainId);
        final User existingUser = new User();
        HashMap<String, Object> existingAdditionalInformation = new HashMap<>();
        existingAdditionalInformation.put("source", source);
        existingAdditionalInformation.put("op_id_token", "token1");
        existingUser.setAdditionalInformation(existingAdditionalInformation);
        existingUser.setAccountNonLocked(true);

        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(existingUser));
        when(userService.update(any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService).update(argThat(user1 -> "token2".equals(user1.getAdditionalInformation().get("op_id_token"))));
    }

    @Test
    public void shouldConnect_knownUser_with_OpIdToken_removed() {
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

        User updatedUser = mock(User.class);
        when(updatedUser.isEnabled()).thenReturn(true);

        when(domain.getId()).thenReturn(domainId);
        final User existingUser = new User();
        HashMap<String, Object> existingAdditionalInformation = new HashMap<>();
        existingAdditionalInformation.put("source", source);
        existingAdditionalInformation.put("op_id_token", "token1");
        existingUser.setAdditionalInformation(existingAdditionalInformation);
        existingUser.setAccountNonLocked(true);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(existingUser));
        when(userService.update(any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userService).update(argThat(user1 -> !user1.getAdditionalInformation().containsKey("op_id_token")));
    }

    @Test
    public void shouldLoadByUsername_idpUser() {
        String domainId = "Domain";
        String source = "SRC";
        String id = "id";

        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);

        when(domain.getId()).thenReturn(domainId);
        final User existingUser = new User();
        HashMap<String, Object> existingAdditionalInformation = new HashMap<>();
        existingAdditionalInformation.put("source", source);
        existingAdditionalInformation.put("op_id_token", "token1");
        existingUser.setAdditionalInformation(existingAdditionalInformation);
        existingUser.setAccountNonLocked(true);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(existingUser));

        TestObserver<User> testObserver = userAuthenticationService.loadPreAuthenticatedUser(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(user1 -> user1.equals(existingUser));

        verify(userService, times(1)).findByDomainAndExternalIdAndSource("Domain", "id", "SRC");
    }

    @Test
    public void shouldNotLoadPreAuthenticatedUser_idpUser_accountLocked() {
        String domainId = "Domain";
        String source = "SRC";
        String id = "id";

        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);

        when(domain.getId()).thenReturn(domainId);
        final User existingUser = new User();
        HashMap<String, Object> existingAdditionalInformation = new HashMap<>();
        existingAdditionalInformation.put("source", source);
        existingAdditionalInformation.put("op_id_token", "token1");
        existingUser.setAdditionalInformation(existingAdditionalInformation);
        existingUser.setAccountNonLocked(false);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(existingUser));

        TestObserver<User> testObserver = userAuthenticationService.loadPreAuthenticatedUser(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertFailure(AccountLockedException.class);

        verify(userService, times(1)).findByDomainAndExternalIdAndSource("Domain", "id", "SRC");
    }

    @Test
    public void shouldNotLoadPreAuthenticatedUser_subjectRequest_userDoesNotExist() {
        var request = mock(Request.class);

        when(userService.findById(any())).thenReturn(Maybe.empty());

        TestObserver<User> testObserver = userAuthenticationService.loadPreAuthenticatedUser("some_id", request).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertFailure(UserNotFoundException.class);
    }

    @Test
    public void shouldNotLoadPreAuthenticatedUser_subjectRequest() {
        final User existingUser = new User();
        existingUser.setId(UUID.randomUUID().toString());
        existingUser.setUsername("username");
        existingUser.setAccountNonLocked(false);

        var request = mock(Request.class);

        when(userService.findById(existingUser.getId())).thenReturn(Maybe.just(existingUser));

        TestObserver<User> testObserver = userAuthenticationService.loadPreAuthenticatedUser(existingUser.getId(), request).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNotComplete();
        testObserver.assertFailure(AccountLockedException.class);
    }

    @Test
    public void shouldLoadPreAuthenticatedUser_subjectRequest_enhance_defer() {
        final User existingUser = new User();
        existingUser.setId(UUID.randomUUID().toString());
        existingUser.setUsername("username");
        existingUser.setAccountNonLocked(true);

        var request = mock(Request.class);

        when(userService.findById(existingUser.getId())).thenReturn(Maybe.just(existingUser));
        when(identityProviderManager.get(any())).thenReturn(Maybe.just(new AuthenticationProvider() {
            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(Authentication authentication) {
                return Maybe.empty();
            }

            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(String username) {
                return Maybe.empty();
            }
        }));
        when(userService.enhance(existingUser)).thenReturn(Single.just(existingUser));

        TestObserver<User> testObserver = userAuthenticationService.loadPreAuthenticatedUser(existingUser.getId(), request).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertValue(user1 -> user1.equals(existingUser));
    }

    @Test
    public void shouldLoadPreAuthenticatedUser_subjectRequest_enhance_defer_with_AuthenticationProvider() {
        final User existingUser = new User();
        existingUser.setId(UUID.randomUUID().toString());
        existingUser.setUsername("username");
        existingUser.setAccountNonLocked(true);

        var request = mock(Request.class);

        when(userService.findById(existingUser.getId())).thenReturn(Maybe.just(existingUser));
        when(identityProviderManager.get(any())).thenReturn(Maybe.just(new AuthenticationProvider() {
            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(Authentication authentication) {
                var user = new io.gravitee.am.identityprovider.api.DefaultUser();
                user.setUsername(existingUser.getUsername());
                return Maybe.just(user);
            }

            @Override
            public Maybe<io.gravitee.am.identityprovider.api.User> loadUserByUsername(String username) {
                var user = new io.gravitee.am.identityprovider.api.DefaultUser();
                user.setUsername(existingUser.getUsername());
                return Maybe.just(user);
            }
        }));
        when(userService.enhance(existingUser)).thenReturn(Single.just(existingUser));
        when(userService.update(existingUser)).thenReturn(Single.just(existingUser));

        TestObserver<User> testObserver = userAuthenticationService.loadPreAuthenticatedUser(existingUser.getId(), request).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertValue(user1 -> user1.equals(existingUser));
    }
}
