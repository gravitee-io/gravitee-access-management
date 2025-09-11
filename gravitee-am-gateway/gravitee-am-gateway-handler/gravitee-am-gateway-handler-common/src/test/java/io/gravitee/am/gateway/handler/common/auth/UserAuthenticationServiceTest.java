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
import io.gravitee.am.common.exception.authentication.AccountEnforcePasswordException;
import io.gravitee.am.common.exception.authentication.AccountLockedException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationService;
import io.gravitee.am.gateway.handler.common.auth.user.impl.UserAuthenticationServiceImpl;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserIdentity;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.CommonUserRepository;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private SubjectManager subjectManager;

    @Mock
    private Domain domain;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private RulesEngine rulesEngine;

    @Test
    public void shouldConnect_unknownUser() {
        String domainId = "Domain";
        String username = "foo";
        String source = "SRC";
        String idp = "LDAP_1";
        String id = "id";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getUsername()).thenReturn(username);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        additionalInformation.put("last_identity", idp);
        additionalInformation.put("op_id_token", "somevalue");
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);

        User createdUser = mock(User.class);
        when(createdUser.isEnabled()).thenReturn(true);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.empty());
        when(userService.findByDomainAndUsernameAndSource(domainId, username, source)).thenReturn(Maybe.empty());
        when(userService.create(any())).thenReturn(Single.just(createdUser));
        when(userService.enhance(createdUser)).thenReturn(Single.just(createdUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, times(1)).create(argThat(u -> u.getAdditionalInformation().containsKey("op_id_token")));
        verify(userService, never()).update(any());
    }

    @Test
    public void shouldConnect_knownUser_populateLastPasswordReset_fromUpdatedAt() {
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
        final Date updatedAt = new Date(1_725_000_000_000L);
        foundUser.setUpdatedAt(updatedAt);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, times(1)).update(argThat(u -> updatedAt.equals(u.getLastPasswordReset())), any());
    }

    @Test
    public void shouldConnect_knownUser_populateLastPasswordReset_whenUpdatedAtNull() {
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
        // updatedAt is intentionally left null
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, times(1)).update(argThat(u -> u.getLastPasswordReset() != null), any());
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
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, never()).create(any());
        verify(userService, times(1)).update(argThat(u -> u.isAccountNonLocked()), any());
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
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, never()).create(any());
        verify(userService, times(1)).update(argThat(u -> u.isAccountNonLocked()), any());
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
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(domain.getId()).thenReturn(domainId);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.empty());
        when(userService.findByDomainAndUsernameAndSource(domainId, username, source)).thenReturn(Maybe.empty());
        when(userService.create(any())).thenReturn(Single.just(createdUser));
        when(userService.enhance(createdUser)).thenReturn(Single.just(createdUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(user1 -> user1.getRoles().size() == 2);
    }

    @Test
    public void shouldConnect_user_withDynaicRoles_update() {
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

        when(domain.getId()).thenReturn(domainId);
        final User foundUser = spy(new User());
        when(foundUser.isAccountNonLocked()).thenReturn(true);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenAnswer(i -> Single.just(i.getArguments()[0]));
        when(userService.enhance(any())).thenAnswer(i -> Single.just(i.getArguments()[0]));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(user1 -> user1.getDynamicRoles().size() == 2);
        verify(userService).update(any(), argThat(actions -> actions.updateDynamicRole()
                && !(actions.updateAddresses() || actions.updateRole() || actions.updateEntitlements() || actions.updateAttributes())));
    }

    @Test
    public void shouldConnect_user_withDynamicRoles_unchanged() {
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

        when(domain.getId()).thenReturn(domainId);
        final User foundUser = spy(new User());
        foundUser.setAccountNonLocked(true);
        foundUser.setDynamicRoles(Arrays.asList("idp-role", "idp2-role"));
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenAnswer(i -> Single.just(i.getArguments()[0]));
        when(userService.enhance(any())).thenAnswer(i -> Single.just(i.getArguments()[0]));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(user1 -> user1.getDynamicRoles().size() == 2);
        verify(userService).update(any(), argThat(actions -> !actions.updateDynamicRole()
                && !(actions.updateAddresses() || actions.updateRole() || actions.updateEntitlements() || actions.updateAttributes())));
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
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(user1 -> user1.getRoles().size() == 2);
        verify(userService).update(any(), argThat(actions -> !(actions.updateDynamicRole() || actions.updateAddresses() || actions.updateRole()
                || actions.updateEntitlements() || actions.updateAttributes())));

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
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(existingUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService).update(argThat(user1 -> "token2".equals(user1.getAdditionalInformation().get("op_id_token"))), any());
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
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(existingUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver<User> testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userService).update(argThat(user1 -> !user1.getAdditionalInformation().containsKey("op_id_token")), any());
    }

    @Test
    public void shouldConnect_accountLinking() {
        String domainId = "Domain";
        String source = "SRC";
        String id = "id";
        String linkedAccount = "linkedAccountId";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);

        User updatedUser = mock(User.class);
        when(updatedUser.isEnabled()).thenReturn(true);

        when(domain.getId()).thenReturn(domainId);
        final User foundUser = new User();
        foundUser.setId(linkedAccount);
        foundUser.setAccountNonLocked(true);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.getAttribute(ConstantKeys.LINKED_ACCOUNT_ID_CONTEXT_KEY)).thenReturn(linkedAccount);
        when(userService.findById(linkedAccount)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, never()).create(any());
        verify(userService, times(1)).update(argThat(u -> {
            return u.getIdentities() != null &&
                    id.equals(u.getIdentities().get(0).getUserId()) &&
                    source.equals(u.getIdentities().get(0).getProviderId());
        }), argThat(CommonUserRepository.UpdateActions::updateIdentities));
    }

    @Test
    public void shouldConnect_accountLinking_multipleIdentities() {
        String domainId = "Domain";
        String source = "SRC2";
        String id = "id2";
        String linkedAccount = "linkedAccountId";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);
        User updatedUser = mock(User.class);
        when(updatedUser.isEnabled()).thenReturn(true);
        when(domain.getId()).thenReturn(domainId);

        final UserIdentity userIdentity = new UserIdentity();
        userIdentity.setUserId("id1");
        userIdentity.setProviderId("SRC1");

        final User foundUser = new User();
        foundUser.setId(linkedAccount);
        foundUser.setAccountNonLocked(true);
        foundUser.setIdentities(List.of(userIdentity));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.getAttribute(ConstantKeys.LINKED_ACCOUNT_ID_CONTEXT_KEY)).thenReturn(linkedAccount);
        when(userService.findById(linkedAccount)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, never()).create(any());
        verify(userService, times(1)).update(argThat(u -> {
            return u.getIdentities() != null && u.getIdentities().size() == 2;
        }), any());
    }

    @Test
    public void shouldConnect_accountLinking_existingIdentity() {
        String domainId = "Domain";
        String source = "SRC1";
        String id = "id1";
        String linkedAccount = "linkedAccountId";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        additionalInformation.put("newKey", "newValue");
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);
        User updatedUser = mock(User.class);
        when(updatedUser.isEnabled()).thenReturn(true);
        when(domain.getId()).thenReturn(domainId);

        final UserIdentity userIdentity = new UserIdentity();
        userIdentity.setUserId(id);
        userIdentity.setProviderId(source);

        final User foundUser = new User();
        foundUser.setId(linkedAccount);
        foundUser.setAccountNonLocked(true);
        foundUser.setIdentities(List.of(userIdentity));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.getAttribute(ConstantKeys.LINKED_ACCOUNT_ID_CONTEXT_KEY)).thenReturn(linkedAccount);
        when(userService.findById(linkedAccount)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, never()).create(any());
        verify(userService, times(1)).update(argThat(u -> {
            return u.getIdentities() != null &&
                    u.getIdentities().size() == 1 &&
                    "newValue".equals(u.getIdentities().get(0).getAdditionalInformation().get("newKey"));
        }), any());
    }

    @Test
    public void shouldConnect_accountLinking_sameSource() {
        String domainId = "Domain";
        String source = "SRC1";
        String id = "id1";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        additionalInformation.put("newKey", "newValue");
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);
        User updatedUser = mock(User.class);
        when(updatedUser.isEnabled()).thenReturn(true);
        when(domain.getId()).thenReturn(domainId);

        final UserIdentity userIdentity = new UserIdentity();
        userIdentity.setUserId(id);
        userIdentity.setProviderId(source);

        final User foundUser = new User();
        foundUser.setId(id);
        foundUser.setAccountNonLocked(true);
        foundUser.setIdentities(List.of(userIdentity));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, never()).create(any());
        verify(userService, times(1)).update(argThat(u -> u.getIdentities() != null &&
                u.getIdentities().size() == 1 &&
                "newValue".equals(u.getIdentities().get(0).getAdditionalInformation().get("newKey"))), argThat(updateActions -> updateActions.updateIdentities()));
    }


    @Test
    public void shouldNotConnect_accountLinking_differentSource() {
        String domainId = "Domain";
        String source = "SRC1";
        String id = "id1";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        additionalInformation.put("newKey", "newValue");
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);
        User updatedUser = mock(User.class);
        when(updatedUser.isEnabled()).thenReturn(true);
        when(domain.getId()).thenReturn(domainId);

        final UserIdentity userIdentity = new UserIdentity();
        userIdentity.setUserId(id);
        userIdentity.setProviderId("SRC");

        final User foundUser = new User();
        foundUser.setId(id);
        foundUser.setAccountNonLocked(true);
        foundUser.setIdentities(List.of(userIdentity));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, never()).create(any());
        verify(userService, times(1)).update(any(), argThat(updateActions -> !updateActions.updateIdentities())); //
    }

    @Test
    public void shouldConnect_accountLinking_differentProviderIdentity() {
        String domainId = "Domain";
        String source = "SRC1";
        String lastIdentity = "LDAP_1";
        String id = "id1";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getId()).thenReturn(id);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        additionalInformation.put("last_identity", lastIdentity);
        additionalInformation.put("newKey", "newValue");
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);
        User updatedUser = mock(User.class);
        when(updatedUser.isEnabled()).thenReturn(true);
        when(domain.getId()).thenReturn(domainId);

        final UserIdentity userIdentity = new UserIdentity();
        userIdentity.setUserId(id);
        userIdentity.setProviderId(lastIdentity);

        final User foundUser = new User();
        foundUser.setId(id);
        foundUser.setAccountNonLocked(true);
        foundUser.setIdentities(List.of(userIdentity));

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(userService.findByDomainAndExternalIdAndSource(domainId, id, source)).thenReturn(Maybe.just(foundUser));
        when(userService.update(any(), any())).thenReturn(Single.just(updatedUser));
        when(userService.enhance(updatedUser)).thenReturn(Single.just(updatedUser));
        when(rulesEngine.fire(any(), any(), any(), any())).thenReturn(Single.just(executionContext));

        TestObserver testObserver = userAuthenticationService.connect(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userService, never()).create(any());
        verify(userService, times(1)).update(any(), argThat(updateActions -> updateActions.updateIdentities())); //
    }

    @Test
    public void shouldNotLoadPreAuthenticatedUser_subjectRequest_userDoesNotExist() {
        var request = mock(Request.class);

        when(userService.findById(any())).thenReturn(Maybe.empty());

        TestObserver<User> testObserver = userAuthenticationService.loadPreAuthenticatedUser("some_id", request).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNotComplete();
        testObserver.assertFailure(UserNotFoundException.class);
    }

    @Test
    public void shouldNotLoadPreAuthenticatedUserBySub_subjectRequest_userDoesNotExist() {
        var request = mock(Request.class);

        when(subjectManager.findUserBySub(any())).thenReturn(Maybe.empty());

        TestObserver<User> testObserver = userAuthenticationService.loadPreAuthenticatedUserBySub(new JWT(), request).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNotComplete();
        testObserver.assertFailure(AccountLockedException.class);
    }

    @Test
    public void shouldNotLoadPreAuthenticatedUserBySub_subjectRequest() {
        final User existingUser = new User();
        existingUser.setId(UUID.randomUUID().toString());
        existingUser.setUsername("username");
        existingUser.setAccountNonLocked(false);

        final var jwt = new JWT();
        jwt.setSub(existingUser.getId());

        var request = mock(Request.class);

        when(subjectManager.findUserBySub(jwt)).thenReturn(Maybe.just(existingUser));

        TestObserver<User> testObserver = userAuthenticationService.loadPreAuthenticatedUserBySub(jwt, request).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertValue(user1 -> user1.equals(existingUser));
    }

    @Test
    public void shouldLoadPreAuthenticatedUserBySub_subjectRequest_enhance_defer() {
        final User existingUser = new User();
        existingUser.setId(UUID.randomUUID().toString());
        existingUser.setUsername("username");
        existingUser.setAccountNonLocked(true);

        var request = mock(Request.class);

        final var jwt = new JWT();
        jwt.setSub(existingUser.getId());

        when(subjectManager.findUserBySub(jwt)).thenReturn(Maybe.just(existingUser));
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

        TestObserver<User> testObserver = userAuthenticationService.loadPreAuthenticatedUserBySub(jwt, request).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        when(userService.update(eq(existingUser), any())).thenReturn(Single.just(existingUser));

        TestObserver<User> testObserver = userAuthenticationService.loadPreAuthenticatedUser(existingUser.getId(), request).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertValue(user1 -> user1.equals(existingUser));
    }

    @Test
    public void shouldLoadPreAuthenticatedUserBySub_subjectRequest_enhance_defer_with_AuthenticationProvider() {
        final User existingUser = new User();
        existingUser.setId(UUID.randomUUID().toString());
        existingUser.setUsername("username");
        existingUser.setAccountNonLocked(true);

        final var jwt = new JWT();
        jwt.setSub(existingUser.getId());

        var request = mock(Request.class);

        when(subjectManager.findUserBySub(jwt)).thenReturn(Maybe.just(existingUser));
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
        when(userService.update(eq(existingUser), any())).thenReturn(Single.just(existingUser));

        TestObserver<User> testObserver = userAuthenticationService.loadPreAuthenticatedUserBySub(jwt, request).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertValue(user1 -> user1.equals(existingUser));
    }

    @Test
    public void shouldConnectWithPasswordless_nominalCase() {
        final String userId = "userId";
        final Client client = initClient();
        final User user = initUser(client);
        when(userService.findById(userId)).thenReturn(Maybe.just(user));
        when(userService.update(any())).thenReturn(Single.just(user));
        when(userService.enhance(any())).thenReturn(Single.just(user));

        TestObserver testObserver = userAuthenticationService.connectWithPasswordless(userId, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldConnectWithPasswordless_policyDisabled_1() {
        final String userId = "userId";
        final LoginSettings loginSettings = new LoginSettings();
        loginSettings.setInherited(false);
        loginSettings.setPasswordlessEnabled(false);
        final Client client = initClient();
        client.setLoginSettings(loginSettings);
        client.setLoginSettings(loginSettings);
        final User user = initUser(client);
        final Date lastLogin = new Date(Instant.now().minus(90, ChronoUnit.DAYS).toEpochMilli());
        user.setLastLoginWithCredentials(lastLogin);
        when(userService.findById(userId)).thenReturn(Maybe.just(user));
        when(userService.update(any())).thenReturn(Single.just(user));
        when(userService.enhance(any())).thenReturn(Single.just(user));

        TestObserver testObserver = userAuthenticationService.connectWithPasswordless(userId, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldConnectWithPasswordless_policyDisabled_2() {
        final LoginSettings loginSettings = new LoginSettings();
        loginSettings.setInherited(false);
        loginSettings.setPasswordlessEnabled(true);
        loginSettings.setPasswordlessEnforcePasswordEnabled(false);
        final String userId = "userId";
        final Client client = initClient();
        client.setLoginSettings(loginSettings);
        final User user = initUser(client);
        final Date lastLogin = new Date(Instant.now().minus(90, ChronoUnit.DAYS).toEpochMilli());
        user.setLastLoginWithCredentials(lastLogin);
        when(userService.findById(userId)).thenReturn(Maybe.just(user));
        when(userService.update(any())).thenReturn(Single.just(user));
        when(userService.enhance(any())).thenReturn(Single.just(user));

        TestObserver testObserver = userAuthenticationService.connectWithPasswordless(userId, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldConnectWithPasswordless_policyDisabled_3() {
        final LoginSettings loginSettings = new LoginSettings();
        loginSettings.setInherited(false);
        loginSettings.setPasswordlessEnabled(true);
        loginSettings.setPasswordlessEnforcePasswordEnabled(true);
        final String userId = "userId";
        final Client client = initClient();
        client.setLoginSettings(loginSettings);
        final User user = initUser(client);
        final Date lastLogin = new Date(Instant.now().minus(90, ChronoUnit.DAYS).toEpochMilli());
        user.setLastLoginWithCredentials(lastLogin);
        when(userService.findById(userId)).thenReturn(Maybe.just(user));
        when(userService.update(any())).thenReturn(Single.just(user));
        when(userService.enhance(any())).thenReturn(Single.just(user));

        TestObserver testObserver = userAuthenticationService.connectWithPasswordless(userId, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldConnectWithPasswordless_policyDisabled_4() {
        final LoginSettings loginSettings = new LoginSettings();
        loginSettings.setInherited(false);
        loginSettings.setPasswordlessEnabled(false);
        loginSettings.setPasswordlessEnforcePasswordEnabled(true);
        loginSettings.setPasswordlessEnforcePasswordMaxAge(30);
        final String userId = "userId";
        final Client client = initClient();
        client.setLoginSettings(loginSettings);
        final User user = initUser(client);
        final Date lastLogin = new Date(Instant.now().minus(90, ChronoUnit.SECONDS).toEpochMilli());
        user.setLastLoginWithCredentials(lastLogin);
        when(userService.findById(userId)).thenReturn(Maybe.just(user));
        when(userService.update(any())).thenReturn(Single.just(user));
        when(userService.enhance(any())).thenReturn(Single.just(user));

        TestObserver testObserver = userAuthenticationService.connectWithPasswordless(userId, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldConnectWithPasswordless_policyNotEvaluated() {
        final LoginSettings loginSettings = new LoginSettings();
        loginSettings.setInherited(false);
        loginSettings.setPasswordlessEnabled(true);
        loginSettings.setPasswordlessEnforcePasswordEnabled(true);
        loginSettings.setPasswordlessEnforcePasswordMaxAge(30);
        final Client client = initClient();
        client.setLoginSettings(loginSettings);
        final String userId = "userId";
        final User user = initUser(client);
        final Date lastLogin = new Date(Instant.now().minus(15, ChronoUnit.SECONDS).toEpochMilli());
        user.setLastLoginWithCredentials(lastLogin);
        when(userService.findById(userId)).thenReturn(Maybe.just(user));
        when(userService.update(any())).thenReturn(Single.just(user));
        when(userService.enhance(any())).thenReturn(Single.just(user));

        TestObserver testObserver = userAuthenticationService.connectWithPasswordless(userId, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldNotConnectWithPasswordless_userIndefinitelyLocked() {
        final String userId = "userId";
        final Client client = initClient();
        final User user = initUser(client);
        user.setAccountNonLocked(false);

        when(userService.findById(userId)).thenReturn(Maybe.just(user));

        TestObserver testObserver = userAuthenticationService.connectWithPasswordless(userId, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNotComplete();
        testObserver.assertError(AccountLockedException.class);
    }

    @Test
    public void shouldNotConnectWithPasswordless_userDisabled() {
        final String userId = "userId";
        final Client client = initClient();
        final User user = initUser(client);
        user.setEnabled(false);

        when(userService.findById(userId)).thenReturn(Maybe.just(user));

        TestObserver testObserver = userAuthenticationService.connectWithPasswordless(userId, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNotComplete();
        testObserver.assertError(AccountDisabledException.class);
    }

    @Test
    public void shouldNotConnectWithPasswordless_policyEvaluated() {
        final LoginSettings loginSettings = new LoginSettings();
        loginSettings.setInherited(false);
        loginSettings.setPasswordlessEnabled(true);
        loginSettings.setPasswordlessEnforcePasswordEnabled(true);
        loginSettings.setPasswordlessEnforcePasswordMaxAge(30);
        final String userId = "userId";
        final Client client = initClient();
        client.setLoginSettings(loginSettings);
        final User user = initUser(client);
        final Date lastLogin = new Date(Instant.now().minus(45, ChronoUnit.SECONDS).toEpochMilli());
        user.setLastLoginWithCredentials(lastLogin);
        when(userService.findById(userId)).thenReturn(Maybe.just(user));

        TestObserver testObserver = userAuthenticationService.connectWithPasswordless(userId, client).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNotComplete();
        testObserver.assertError(AccountEnforcePasswordException.class);
    }

    private Client initClient() {
        final Client client = new Client();
        TreeSet<ApplicationIdentityProvider> identityProviders = new TreeSet<>();
        ApplicationIdentityProvider appIdp = new ApplicationIdentityProvider();
        appIdp.setIdentity(UUID.randomUUID().toString());
        identityProviders.add(appIdp);
        client.setIdentityProviders(identityProviders);
        return client;
    }

    private User initUser(Client client) {
        final User user = new User();
        user.setSource(client.getIdentityProviders().first().getIdentity());
        return user;
    }
}
