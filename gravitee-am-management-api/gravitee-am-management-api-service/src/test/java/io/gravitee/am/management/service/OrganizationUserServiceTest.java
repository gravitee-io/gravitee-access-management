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

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.management.service.impl.OrganizationUserServiceImpl;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.RateLimiterService;
import io.gravitee.am.service.VerifyAttemptService;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.impl.PasswordHistoryService;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.validators.email.EmailValidatorImpl;
import io.gravitee.am.service.validators.user.UserValidatorImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.service.validators.user.UserValidatorImpl.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class OrganizationUserServiceTest {

    @InjectMocks
    private final OrganizationUserService organizationUserService = new OrganizationUserServiceImpl();

    @Mock
    private PasswordService passwordService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private AuditService auditService;

    @Mock
    private io.gravitee.am.service.OrganizationUserService commonUserService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private PasswordHistoryService passwordHistoryService;

    @Mock
    private VerifyAttemptService verifyAttemptService;

    @Spy
    private UserValidatorImpl userValidator = new UserValidatorImpl(
            NAME_STRICT_PATTERN,
            NAME_LAX_PATTERN,
            USERNAME_PATTERN,
            new EmailValidatorImpl("")
    );

    @Test
    public void shouldDeleteUser_without_membership() {
        String organization = "DEFAULT";
        String userId = "user-id";

        User user = new User();
        user.setId(userId);
        user.setSource("source-idp");

        when(commonUserService.findById(any(), any(), any())).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.empty());
        when(commonUserService.delete(anyString())).thenReturn(Completable.complete());
        when(membershipService.findByMember(any(), any())).thenReturn(Flowable.empty());
        when(rateLimiterService.deleteByUser(any())).thenReturn(Completable.complete());
        when(passwordHistoryService.deleteByUser(any())).thenReturn(Completable.complete());
        when(verifyAttemptService.deleteByUser(any())).thenReturn(Completable.complete());

        organizationUserService.delete(ReferenceType.ORGANIZATION, organization, userId)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(commonUserService, times(1)).delete(any());
        verify(membershipService, never()).delete(anyString());
    }

    @Test
    public void shouldDeleteUser_with_memberships() {
        String organization = "DEFAULT";
        String userId = "user-id";

        User user = new User();
        user.setId(userId);
        user.setSource("source-idp");

        Membership m1 = mock(Membership.class);
        when(m1.getId()).thenReturn("m1");
        Membership m2 = mock(Membership.class);
        when(m2.getId()).thenReturn("m2");
        Membership m3 = mock(Membership.class);
        when(m3.getId()).thenReturn("m3");

        when(commonUserService.findById(any(), any(), any())).thenReturn(Single.just(user));
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.empty());
        when(commonUserService.delete(anyString())).thenReturn(Completable.complete());
        when(membershipService.findByMember(any(), any())).thenReturn(Flowable.just(m1, m2, m3));
        when(membershipService.delete(anyString())).thenReturn(Completable.complete());
        when(rateLimiterService.deleteByUser(any())).thenReturn(Completable.complete());
        when(passwordHistoryService.deleteByUser(any())).thenReturn(Completable.complete());
        when(verifyAttemptService.deleteByUser(any())).thenReturn(Completable.complete());

        organizationUserService.delete(ReferenceType.ORGANIZATION, organization, userId)
                .test()
                .assertComplete()
                .assertNoErrors();
        verify(commonUserService, times(1)).delete(any());
        verify(membershipService, times(3)).delete(anyString());
    }

    private NewUser newOrganizationUser() {
        NewUser newUser = new NewUser();
        newUser.setUsername("userid");
        newUser.setFirstName("userid");
        newUser.setLastName("userid");
        newUser.setEmail("userid");
        newUser.setPassword("Test123!");
        newUser.setSource("gravitee");
        return newUser;
    }

    @Test
    public void shouldCreateOrganizationUser() {
        NewUser newUser = newOrganizationUser();
        newUser.setSource("gravitee");
        newUser.setPassword("Test123!");
        newUser.setEmail("email@acme.fr");
        when(passwordService.isValid(any())).thenReturn(true);
        when(commonUserService.findByUsernameAndSource(any(), any(), anyString(), anyString())).thenReturn(Maybe.empty());
        final UserProvider provider = mock(UserProvider.class);
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.just(provider));
        when(provider.create(any())).thenReturn(Single.just(mock(io.gravitee.am.identityprovider.api.User.class)));

        doReturn(Completable.complete()).when(userValidator).validate(any());
        when(commonUserService.create(any())).thenReturn(Single.just(mock(User.class)));
        when(commonUserService.setRoles(any())).thenReturn(Completable.complete());

        TestObserver<User> testObserver = organizationUserService.createGraviteeUser(new Organization(), newUser, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();

        verify(provider).create(any());
        verify(commonUserService).create(argThat(user -> user.getPassword() != null));
        verify(commonUserService).setRoles(any());
    }

    @Test
    public void shouldNotCreateOrganizationUser_NotGraviteeSource() {
        NewUser newUser = newOrganizationUser();
        newUser.setSource("toto");
        newUser.setPassword("Test123!");
        newUser.setEmail("email@acme.fr");

        TestObserver<User> testObserver = organizationUserService.createGraviteeUser(new Organization(), newUser, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(UserInvalidException.class);

        verify(commonUserService, never()).create(argThat(user -> user.getPassword() == null));
        verify(commonUserService, never()).setRoles(any());
    }

    @Test
    public void shouldNotCreateOrganizationUser_UserAlreadyExist() {
        NewUser newUser = newOrganizationUser();

        Organization organization = new Organization();
        organization.setId("orgaid");

        when(commonUserService.findByUsernameAndSource(ReferenceType.ORGANIZATION, organization.getId(), newUser.getUsername(), newUser.getSource())).thenReturn(Maybe.just(new User()));

        TestObserver<User> testObserver = organizationUserService.createGraviteeUser(organization, newUser, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(UserAlreadyExistsException.class);
    }

    @Test
    public void shouldNotCreateOrganizationUser_UnknownProvider() {
        NewUser newUser = newOrganizationUser();

        Organization organization = new Organization();
        organization.setId("orgaid");

        when(commonUserService.findByUsernameAndSource(ReferenceType.ORGANIZATION, organization.getId(), newUser.getUsername(), newUser.getSource())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(newUser.getSource())).thenReturn(Maybe.empty());

        TestObserver<User> testObserver = organizationUserService.createGraviteeUser(organization, newUser, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(UserProviderNotFoundException.class);
    }

    @Test
    public void shouldNotCreateOrganizationUser_noPassword() {
        NewUser newUser = newOrganizationUser();
        newUser.setPassword(null);

        when(commonUserService.findByUsernameAndSource(any(), any(),  any(),  any())).thenReturn(Maybe.empty());
        when(identityProviderManager.getUserProvider(any())).thenReturn(Maybe.just(mock(UserProvider.class)));
        TestObserver<User> testObserver = organizationUserService.createGraviteeUser(new Organization(), newUser, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidPasswordException.class);
    }

    @Test
    public void shouldUpdateUser_byExternalId() {

        NewUser newUser = new NewUser();
        newUser.setExternalId("user#1");
        newUser.setSource("source");
        newUser.setUsername("Username");
        newUser.setFirstName("Firstname");
        newUser.setLastName("Lastname");
        newUser.setEmail("email@gravitee.io");

        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("info1", "value1");
        additionalInformation.put("info2", "value2");
        additionalInformation.put(StandardClaims.PICTURE, "https://gravitee.io/my-picture");
        newUser.setAdditionalInformation(additionalInformation);

        User user = new User();
        user.setId("user#1");
        when(commonUserService.findByExternalIdAndSource(ReferenceType.ORGANIZATION, "orga#1", newUser.getExternalId(), newUser.getSource())).thenReturn(Maybe.just(user));
        when(commonUserService.update(any(User.class))).thenAnswer(i -> Single.just(i.getArgument(0)));

        TestObserver<User> obs = organizationUserService.createOrUpdate(ReferenceType.ORGANIZATION, "orga#1", newUser).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(updatedUser -> {
            assertEquals(updatedUser.getId(), user.getId());
            assertEquals(updatedUser.getFirstName(), newUser.getFirstName());
            assertEquals(updatedUser.getLastName(), newUser.getLastName());
            assertEquals(updatedUser.getEmail(), newUser.getEmail());
            assertEquals(updatedUser.getAdditionalInformation(), newUser.getAdditionalInformation());
            assertEquals(updatedUser.getPicture(), newUser.getAdditionalInformation().get(StandardClaims.PICTURE));

            return true;
        });
    }


    @Test
    public void shouldUpdateUser_logoutDate() {
        User user = new User();
        user.setId("user#1");
        user.setExternalId("user#1");
        user.setSource("source");
        user.setUsername("Username");
        user.setFirstName("Firstname");
        user.setLastName("Lastname");
        user.setEmail("email@gravitee.io");
        user.setLastLogoutAt(null);

        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("info1", "value1");
        additionalInformation.put("info2", "value2");
        additionalInformation.put(StandardClaims.PICTURE, "https://gravitee.io/my-picture");
        user.setAdditionalInformation(additionalInformation);

        when(commonUserService.findById(ReferenceType.ORGANIZATION, "orga#1", user.getId())).thenReturn(Single.just(user));
        when(commonUserService.update(any(User.class))).thenAnswer(i -> Single.just(i.getArgument(0)));

        TestObserver<User> obs = organizationUserService.updateLogoutDate(ReferenceType.ORGANIZATION, "orga#1", user.getId()).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(updatedUser -> {
            assertEquals(updatedUser.getId(), user.getId());
            assertEquals(updatedUser.getFirstName(), user.getFirstName());
            assertEquals(updatedUser.getLastName(), user.getLastName());
            assertEquals(updatedUser.getEmail(), user.getEmail());
            assertEquals(updatedUser.getAdditionalInformation(), user.getAdditionalInformation());
            assertEquals(updatedUser.getPicture(), user.getAdditionalInformation().get(StandardClaims.PICTURE));
            assertTrue(updatedUser.getLastLogoutAt() != null && (System.currentTimeMillis() - updatedUser.getLastLogoutAt().getTime()) < 100);
            return true;
        });
    }

    @Test
    public void shouldUpdateUser_byUsername() {

        NewUser newUser = new NewUser();
        newUser.setExternalId("user#1");
        newUser.setSource("source");
        newUser.setUsername("Username");
        newUser.setFirstName("Firstname");
        newUser.setLastName("Lastname");
        newUser.setEmail("email@gravitee.io");

        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("info1", "value1");
        additionalInformation.put("info2", "value2");
        additionalInformation.put(StandardClaims.PICTURE, "https://gravitee.io/my-picture");
        newUser.setAdditionalInformation(additionalInformation);

        User user = new User();
        user.setId("user#1");
        when(commonUserService.findByExternalIdAndSource(ReferenceType.ORGANIZATION, "orga#1", newUser.getExternalId(), newUser.getSource())).thenReturn(Maybe.empty());
        when(commonUserService.findByUsernameAndSource(ReferenceType.ORGANIZATION, "orga#1", newUser.getUsername(), newUser.getSource())).thenReturn(Maybe.just(user));
        when(commonUserService.update(any(User.class))).thenAnswer(i -> Single.just(i.getArgument(0)));

        TestObserver<User> obs = organizationUserService.createOrUpdate(ReferenceType.ORGANIZATION, "orga#1", newUser).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(updatedUser -> {
            assertEquals(updatedUser.getId(), user.getId());
            assertEquals(updatedUser.getFirstName(), newUser.getFirstName());
            assertEquals(updatedUser.getLastName(), newUser.getLastName());
            assertEquals(updatedUser.getEmail(), newUser.getEmail());
            assertEquals(updatedUser.getAdditionalInformation(), newUser.getAdditionalInformation());
            assertEquals(updatedUser.getPicture(), newUser.getAdditionalInformation().get(StandardClaims.PICTURE));

            return true;
        });
    }

    @Test
    public void shouldCreateUser() {

        NewUser newUser = new NewUser();
        newUser.setExternalId("user#1");
        newUser.setSource("source");
        newUser.setUsername("Username");
        newUser.setFirstName("Firstname");
        newUser.setLastName("Lastname");
        newUser.setEmail("email@gravitee.io");

        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("info1", "value1");
        additionalInformation.put("info2", "value2");
        additionalInformation.put(StandardClaims.PICTURE, "https://gravitee.io/my-picture");
        newUser.setAdditionalInformation(additionalInformation);

        when(commonUserService.findByExternalIdAndSource(ReferenceType.ORGANIZATION, "orga#1", newUser.getExternalId(), newUser.getSource())).thenReturn(Maybe.empty());
        when(commonUserService.findByUsernameAndSource(ReferenceType.ORGANIZATION, "orga#1", newUser.getUsername(), newUser.getSource())).thenReturn(Maybe.empty());
        when(commonUserService.create(any(User.class))).thenAnswer(i -> Single.just(i.getArgument(0)));

        TestObserver<User> obs = organizationUserService.createOrUpdate(ReferenceType.ORGANIZATION, "orga#1", newUser).test();

        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertNoErrors();
        obs.assertValue(updatedUser -> {
            assertNotNull(updatedUser.getId());
            assertEquals(updatedUser.getFirstName(), newUser.getFirstName());
            assertEquals(updatedUser.getLastName(), newUser.getLastName());
            assertEquals(updatedUser.getEmail(), newUser.getEmail());
            assertEquals(updatedUser.getAdditionalInformation(), newUser.getAdditionalInformation());
            assertEquals(updatedUser.getPicture(), newUser.getAdditionalInformation().get(StandardClaims.PICTURE));

            return true;
        });
    }

    @Test
    public void shouldNotResetPassword_MissingPwd() {
        final TestObserver<Void> testObserver = organizationUserService.resetPassword("org#1", new User(), null, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidPasswordException.class);
    }

    @Test
    public void shouldNotResetPassword_invalidPwd() {
        when(passwordService.isValid(anyString())).thenReturn(false);
        final TestObserver<Void> testObserver = organizationUserService.resetPassword("org#1", new User(), "simple", null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidPasswordException.class);
    }

    @Test
    public void shouldResetPassword() {
        when(passwordService.isValid(anyString())).thenReturn(true);

        final User user = new User();
        user.setUsername("username");
        user.setSource("gravitee");

        when(commonUserService.update(any())).thenReturn(Single.just(user));

        final TestObserver<Void> testObserver = organizationUserService.resetPassword("org#1", user, "Test123!", null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNoErrors();

        verify(commonUserService).update(any());
    }

    @Test
    public void shouldNotResetPassword_InvalidSource() {
        when(passwordService.isValid(anyString())).thenReturn(true);

        final User user = new User();
        user.setUsername("username");
        user.setSource("invalid");

        final TestObserver<Void> testObserver = organizationUserService.resetPassword("org#1", user, "Test123!", null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidUserException.class);

        verify(commonUserService, never()).update(any());
    }

}
