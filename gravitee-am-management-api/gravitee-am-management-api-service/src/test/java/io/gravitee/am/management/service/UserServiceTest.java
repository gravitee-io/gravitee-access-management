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

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.management.service.impl.UserServiceImpl;
import io.gravitee.am.model.*;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.*;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.RoleNotFoundException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.jsonwebtoken.JwtBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    public static final String DOMAIN_ID = "domain#1";
    @InjectMocks
    private UserService userService = new UserServiceImpl();

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
    private JwtBuilder jwtBuilder;

    @Mock
    private MembershipService membershipService;

    @Before
    public void setUp() {
        ((UserServiceImpl) userService).setExpireAfter(24 * 3600);
    }

    @Test
    public void shouldCreateUser_invalid_identity_provider() {
        final String domain = "domain";

        Domain domain1 = mock(Domain.class);
        when(domain1.getId()).thenReturn(domain);

        NewUser newUser = mock(NewUser.class);
        when(newUser.getUsername()).thenReturn("username");
        when(newUser.getSource()).thenReturn("unknown-idp");

        when(domainService.findById(domain)).thenReturn(Maybe.just(domain1));
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.empty());

        TestObserver<User> testObserver = userService.create(domain, newUser).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UserProviderNotFoundException.class);
        verify(commonUserService, never()).create(any());
    }

    @Test
    public void shouldNotCreateUser_unknown_client() {
        final String domain = "domain";

        Domain domain1 = mock(Domain.class);
        when(domain1.getId()).thenReturn(domain);

        NewUser newUser = mock(NewUser.class);
        when(newUser.getUsername()).thenReturn("username");
        when(newUser.getSource()).thenReturn("idp");
        when(newUser.getClient()).thenReturn("client");

        UserProvider userProvider = mock(UserProvider.class);

        when(domainService.findById(domain)).thenReturn(Maybe.just(domain1));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(applicationService.findById(newUser.getClient())).thenReturn(Maybe.empty());
        when(applicationService.findByDomainAndClientId(domain, newUser.getClient())).thenReturn(Maybe.empty());

        TestObserver<User> testObserver = userService.create(domain, newUser).test();
        testObserver.assertNotComplete();
        testObserver.assertError(ClientNotFoundException.class);
        verify(commonUserService, never()).create(any());
    }

    @Test
    public void shouldNotCreateUser_invalid_client() {
        final String domain = "domain";

        Domain domain1 = mock(Domain.class);
        when(domain1.getId()).thenReturn(domain);

        NewUser newUser = mock(NewUser.class);
        when(newUser.getUsername()).thenReturn("username");
        when(newUser.getSource()).thenReturn("idp");
        when(newUser.getClient()).thenReturn("client");

        UserProvider userProvider = mock(UserProvider.class);

        Application application = mock(Application.class);
        when(application.getDomain()).thenReturn("other-domain");

        when(domainService.findById(domain)).thenReturn(Maybe.just(domain1));
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(applicationService.findById(newUser.getClient())).thenReturn(Maybe.just(application));

        TestObserver<User> testObserver = userService.create(domain, newUser).test();
        testObserver.assertNotComplete();
        testObserver.assertError(ClientNotFoundException.class);
        verify(commonUserService, never()).create(any());
    }

    @Test
    public void shouldNotCreateUser_user_already_exists() {
        final String domain = "domain";

        Domain domain1 = mock(Domain.class);
        when(domain1.getId()).thenReturn(domain);

        NewUser newUser = mock(NewUser.class);
        when(newUser.getUsername()).thenReturn("username");
        when(newUser.getSource()).thenReturn("idp");

        when(domainService.findById(domain)).thenReturn(Maybe.just(domain1));
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.just(new User()));

        TestObserver<User> testObserver = userService.create(domain, newUser).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UserAlreadyExistsException.class);
        verify(commonUserService, never()).create(any());
    }

    @Test
    public void shouldPreRegisterUser() {
        shouldPreRegisterUser(false, false);
    }

    @Test
    public void shouldPreRegisterUser_dynamicUserRegistration_domainLevel() {
        shouldPreRegisterUser(true, false);
    }

    @Test
    public void shouldPreRegisterUser_dynamicUserRegistration_clientLevel() {
        shouldPreRegisterUser(true, true);
    }

    @Test
    public void shouldNotUpdateUser_unknown_client() {
        final String domain = "domain";
        final String id = "id";

        User user = mock(User.class);
        when(user.getSource()).thenReturn("idp");

        UpdateUser updateUser = mock(UpdateUser.class);
        when(updateUser.getClient()).thenReturn("client");

        UserProvider userProvider = mock(UserProvider.class);

        when(commonUserService.findById(eq(ReferenceType.DOMAIN), eq(domain), eq(id))).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(applicationService.findById(updateUser.getClient())).thenReturn(Maybe.empty());
        when(applicationService.findByDomainAndClientId(domain, updateUser.getClient())).thenReturn(Maybe.empty());

        TestObserver<User> testObserver = userService.update(domain, id, updateUser).test();
        testObserver.assertNotComplete();
        testObserver.assertError(ClientNotFoundException.class);
    }

    @Test
    public void shouldNotUpdateUser_invalid_client() {
        final String domain = "domain";
        final String id = "id";

        User user = mock(User.class);
        when(user.getSource()).thenReturn("idp");

        UpdateUser updateUser = mock(UpdateUser.class);
        when(updateUser.getClient()).thenReturn("client");

        UserProvider userProvider = mock(UserProvider.class);

        Application application = mock(Application.class);
        when(application.getDomain()).thenReturn("other-domain");

        when(commonUserService.findById(eq(ReferenceType.DOMAIN), eq(domain), eq(id))).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(applicationService.findById(updateUser.getClient())).thenReturn(Maybe.just(application));

        TestObserver<User> testObserver = userService.update(domain, id, updateUser).test();
        testObserver.assertNotComplete();
        testObserver.assertError(ClientNotFoundException.class);
    }

    @Test
    public void shouldResetPassword_externalIdEmpty() {
        final String domain = "domain";
        final String password = "password";

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn("idp-id");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.just(idpUser));
        when(userProvider.update(anyString(), any())).thenReturn(Single.just(idpUser));

        when(commonUserService.findById(eq(ReferenceType.DOMAIN), eq(domain), eq("user-id"))).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        TestObserver testObserver = userService.resetPassword(domain, user.getId(), password).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldResetPassword_idpUserNotFound() {
        final String domain = "domain";
        final String password = "password";

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");

        io.gravitee.am.identityprovider.api.User idpUser = mock(io.gravitee.am.identityprovider.api.DefaultUser.class);
        when(idpUser.getId()).thenReturn("idp-id");

        UserProvider userProvider = mock(UserProvider.class);
        when(userProvider.findByUsername(user.getUsername())).thenReturn(Maybe.empty());
        when(userProvider.create(any())).thenReturn(Single.just(idpUser));

        when(commonUserService.findById(eq(ReferenceType.DOMAIN), eq(domain), eq("user-id"))).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(user.getSource())).thenReturn(Maybe.just(userProvider));
        when(commonUserService.update(any())).thenReturn(Single.just(user));
        when(loginAttemptService.reset(any())).thenReturn(Completable.complete());

        TestObserver testObserver = userService.resetPassword(domain, user.getId(), password).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userProvider, times(1)).create(any());
    }

    @Test
    public void shouldAssignRoles() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        User user = mock(User.class);
        when(user.getId()).thenReturn("user-id");

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(commonUserService.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq("user-id"))).thenReturn(Single.just(user));
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(roles));
        when(commonUserService.update(any())).thenReturn(Single.just(new User()));

        TestObserver testObserver = userService.assignRoles(ReferenceType.DOMAIN, DOMAIN_ID, user.getId(), rolesIds).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(commonUserService, times(1)).update(any());
    }

    @Test
    public void shouldAssignRoles_roleNotFound() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(commonUserService.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq("user-id"))).thenReturn(Single.just(user));
        when(identityProviderManager.userProviderExists(user.getSource())).thenReturn(true);
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(Collections.emptySet()));

        TestObserver testObserver = userService.assignRoles(ReferenceType.DOMAIN, DOMAIN_ID, user.getId(), rolesIds).test();
        testObserver.assertNotComplete();
        testObserver.assertError(RoleNotFoundException.class);
        verify(commonUserService, never()).update(any());
    }

    @Test
    public void shouldRevokeRole() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(commonUserService.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq("user-id"))).thenReturn(Single.just(user));
        when(identityProviderManager.userProviderExists(user.getSource())).thenReturn(true);
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(roles));
        when(commonUserService.update(any())).thenReturn(Single.just(new User()));

        TestObserver testObserver = userService.revokeRoles(ReferenceType.DOMAIN, DOMAIN_ID, user.getId(), rolesIds).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(commonUserService, times(1)).update(any());
    }

    @Test
    public void shouldRevokeRoles_roleNotFound() {
        List<String> rolesIds = Arrays.asList("role-1", "role-2");

        User user = new User();
        user.setId("user-id");
        user.setSource("idp-id");

        Set<Role> roles = new HashSet<>();
        Role role1 = new Role();
        role1.setId("role-1");
        Role role2 = new Role();
        role2.setId("role-2");
        roles.add(role1);
        roles.add(role2);

        when(commonUserService.findById(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq("user-id"))).thenReturn(Single.just(user));
        when(identityProviderManager.userProviderExists(user.getSource())).thenReturn(true);
        when(roleService.findByIdIn(rolesIds)).thenReturn(Single.just(Collections.emptySet()));

        TestObserver testObserver = userService.revokeRoles(ReferenceType.DOMAIN, DOMAIN_ID, user.getId(), rolesIds).test();
        testObserver.assertNotComplete();
        testObserver.assertError(RoleNotFoundException.class);
        verify(commonUserService, never()).update(any());
    }

    private void shouldPreRegisterUser(boolean dynamicUserRegistration, boolean clientLevel) {
        final String domain = "domain";

        AccountSettings accountSettings;
        if (dynamicUserRegistration) {
            accountSettings = mock(AccountSettings.class);
            when(accountSettings.isDynamicUserRegistration()).thenReturn(true);
        } else {
            accountSettings = new AccountSettings();
        }

        JwtBuilder mockJwtBuilder = mock(JwtBuilder.class);
        when(mockJwtBuilder.compact()).thenReturn("token");

        Domain domain1 = mock(Domain.class);
        when(domain1.getId()).thenReturn(domain);
        if (!clientLevel) {
            when(domain1.getAccountSettings()).thenReturn(accountSettings);
        }

        NewUser newUser = mock(NewUser.class);
        when(newUser.getUsername()).thenReturn("username");
        when(newUser.getSource()).thenReturn("idp");
        when(newUser.getClient()).thenReturn("client");
        when(newUser.isPreRegistration()).thenReturn(true);

        User user = mock(User.class);
        when(user.isPreRegistration()).thenReturn(true);

        UserProvider userProvider = mock(UserProvider.class);
        doReturn(Single.just(new DefaultUser(newUser.getUsername()))).when(userProvider).create(any());

        Application client = mock(Application.class);
        when(client.getDomain()).thenReturn("domain");
        if (clientLevel) {
            ApplicationSettings settings = mock(ApplicationSettings.class);
            when(settings.getAccount()).thenReturn(accountSettings);
            when(client.getSettings()).thenReturn(settings);
        }
        when(jwtBuilder.setClaims(anyMap())).thenReturn(mockJwtBuilder);
        when(domainService.findById(domain)).thenReturn(Maybe.just(domain1));
        when(commonUserService.findByDomainAndUsernameAndSource(anyString(), anyString(), anyString())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(anyString())).thenReturn(Maybe.just(userProvider));
        when(applicationService.findById(newUser.getClient())).thenReturn(Maybe.just(client));
        when(commonUserService.create(any())).thenReturn(Single.just(new User()));
        when(commonUserService.findById(any(), any(), any())).thenReturn(Single.just(user));

        TestObserver<User> testObserver = userService.create(domain, newUser).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(commonUserService, times(1)).create(any());
        ArgumentCaptor<User> argument = ArgumentCaptor.forClass(User.class);
        verify(commonUserService).create(argument.capture());

        if (dynamicUserRegistration) {
            Assert.assertNotNull(argument.getValue().getRegistrationUserUri());
            Assert.assertNotNull(argument.getValue().getRegistrationAccessToken());
            Assert.assertEquals("token", argument.getValue().getRegistrationAccessToken());
        } else {
            Assert.assertNull(argument.getValue().getRegistrationUserUri());
            Assert.assertNull(argument.getValue().getRegistrationAccessToken());
        }
    }

    @Test
    public void shouldDeleteUser_without_membership() {
        final String organization = "DEFAULT";
        final String userId = "user-id";
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getSource()).thenReturn("source-idp");
        when(commonUserService.findById(any(), any(), any())).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.empty());
        when(commonUserService.delete(anyString())).thenReturn(Completable.complete());
        when(membershipService.findByMember(any(), any())).thenReturn(Single.just(Collections.emptyList()));

        TestObserver<Void> testObserver = userService.delete(ReferenceType.ORGANIZATION, organization, userId).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(commonUserService, times(1)).delete(any());
        verify(membershipService, never()).delete(anyString());
    }

    @Test
    public void shouldDeleteUser_with_memberships() {
        final String organization = "DEFAULT";
        final String userId = "user-id";

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getSource()).thenReturn("source-idp");

        Membership m1 = mock(Membership.class);
        when(m1.getId()).thenReturn("m1");
        Membership m2 = mock(Membership.class);
        when(m2.getId()).thenReturn("m2");
        Membership m3 = mock(Membership.class);
        when(m3.getId()).thenReturn("m3");

        when(commonUserService.findById(any(), any(), any())).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.empty());
        when(commonUserService.delete(anyString())).thenReturn(Completable.complete());
        when(membershipService.findByMember(any(), any())).thenReturn(Single.just(Arrays.asList(m1, m2, m3)));
        when(membershipService.delete(anyString())).thenReturn(Completable.complete());

        TestObserver<Void> testObserver = userService.delete(ReferenceType.ORGANIZATION, organization, userId).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(commonUserService, times(1)).delete(any());
        verify(membershipService, times(3)).delete(anyString());
    }
}
