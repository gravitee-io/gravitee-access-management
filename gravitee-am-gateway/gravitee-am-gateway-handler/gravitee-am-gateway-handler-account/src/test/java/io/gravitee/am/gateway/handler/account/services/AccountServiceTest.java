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
package io.gravitee.am.gateway.handler.account.services;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.handler.account.services.impl.AccountServiceImpl;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.root.service.response.ResetPasswordResponse;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.SelfServiceAccountManagementSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.exception.CredentialNotFoundException;
import io.gravitee.am.service.exception.InvalidPasswordException;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
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
public class AccountServiceTest {

    @Mock
    private CredentialService credentialService;

    @Mock
    private UserValidator userValidator;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private AuditService auditService;

    @Mock
    private Domain domain;

    @Mock
    private UserService gatewayUserService;

    @Mock
    private PasswordService passwordService;

    @InjectMocks
    private AccountService accountService = new AccountServiceImpl();

    @Test
    public void shouldRemoveWebAuthnCredentials_nominalCase() {
        final String userId = "user-id";
        final String credentialId = "credential-id";
        final User principal = new DefaultUser();
        final Credential credential = mock(Credential.class);
        when(credential.getId()).thenReturn(credentialId);
        when(credential.getUserId()).thenReturn("user-id");

        when(credentialService.findById(credentialId)).thenReturn(Maybe.just(credential));
        when(credentialService.delete(credentialId)).thenReturn(Completable.complete());

        TestObserver testObserver = accountService.removeWebAuthnCredential(userId, credentialId, principal).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, times(1)).findById(credentialId);
        verify(credentialService, times(1)).delete(credentialId);
        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldRemoveWebAuthnCredentials_notFound() {
        final String userId = "user-id";
        final String credentialId = "credential-id";
        final User principal = new DefaultUser();

        when(credentialService.findById(credentialId)).thenReturn(Maybe.empty());

        TestObserver testObserver = accountService.removeWebAuthnCredential(userId, credentialId, principal).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, times(1)).findById(credentialId);
        verify(credentialService, never()).delete(credentialId);
        verify(auditService, never()).report(any());
    }

    @Test
    public void shouldRemoveWebAuthnCredentials_notTheSameUser() {
        final String userId = "user-id";
        final String credentialId = "credential-id";
        final User principal = new DefaultUser();
        final Credential credential = mock(Credential.class);
        when(credential.getUserId()).thenReturn("unknown-user-id");

        when(credentialService.findById(credentialId)).thenReturn(Maybe.just(credential));

        TestObserver testObserver = accountService.removeWebAuthnCredential(userId, credentialId, principal).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, times(1)).findById(credentialId);
        verify(credentialService, never()).delete(credentialId);
        verify(auditService, never()).report(any());
    }

    @Test
    public void shouldNotUpdateWebAuthnCredentials_notFound() {
        final String userId = "user-id";
        final String credentialId = "credential-id";
        final String deviceName = "device-name";
        final User principal = new DefaultUser();

        when(credentialService.findById(credentialId)).thenReturn(Maybe.empty());

        TestObserver testObserver = accountService.updateWebAuthnCredential(userId, credentialId, deviceName, principal).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertNotComplete();
        testObserver.assertError(CredentialNotFoundException.class);

        verify(credentialService, times(1)).findById(credentialId);
        verify(credentialService, never()).update(any());
        verify(auditService, never()).report(any());
    }

    @Test
    public void shouldNotUpdateWebAuthnCredentials_notTheSameUser() {
        final String userId = "user-id";
        final String credentialId = "credential-id";
        final String deviceName = "device-name";
        final User principal = new DefaultUser();

        Credential credential = new Credential();
        credential.setUserId("wrong-user-id");
        when(credentialService.findById(credentialId)).thenReturn(Maybe.just(credential));

        TestObserver testObserver = accountService.updateWebAuthnCredential(userId, credentialId, deviceName, principal).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, times(1)).findById(credentialId);
        // we do not update the credential
        verify(credentialService, never()).update(any());
        verify(auditService, never()).report(any());
    }

    @Test
    public void shouldUpdateWebAuthnCredentials_nominalCase() {
        final String userId = "user-id";
        final String credentialId = "credential-id";
        final String deviceName = "device-name";
        final User principal = new DefaultUser();

        Credential credential = new Credential();
        credential.setUserId("user-id");
        when(credentialService.findById(credentialId)).thenReturn(Maybe.just(credential));
        ArgumentCaptor<Credential> argumentCaptor = ArgumentCaptor.forClass(Credential.class);
        when(credentialService.update(argumentCaptor.capture())).thenReturn(Single.just(credential));

        TestObserver testObserver = accountService.updateWebAuthnCredential(userId, credentialId, deviceName, principal).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(credentialService, times(1)).findById(credentialId);
        verify(credentialService, times(1)).update(any());
        verify(auditService, times(1)).report(any());

        Credential updatedCredential = argumentCaptor.getValue();
        Assert.assertEquals(deviceName, updatedCredential.getDeviceName());
    }

    @Test
    public void shouldResetPassword_NoOldPassword() {
        final var client = mock(Client.class);
        final var user = mock(io.gravitee.am.model.User.class);

        when(gatewayUserService.resetPassword(any(), any(), any())).thenReturn(Single.just(new ResetPasswordResponse()));

        var observable = accountService.resetPassword(user, client, "newpassword", new DefaultUser(), Optional.empty()).test();

        observable.awaitDone(10, TimeUnit.SECONDS);
        observable.assertNoErrors();

        verify(gatewayUserService).resetPassword(any(), any(), any());
        verify(gatewayUserService, never()).checkPassword(any(), any(), any());
    }

    @Test
    public void shouldNotResetPassword_NoOldPassword() {
        final var client = mock(Client.class);
        final var user = mock(io.gravitee.am.model.User.class);

        final var settings = new SelfServiceAccountManagementSettings();
        final var resetPasswordSettings = new SelfServiceAccountManagementSettings.ResetPasswordSettings();
        resetPasswordSettings.setOldPasswordRequired(true);
        settings.setResetPassword(resetPasswordSettings);

        when(domain.getSelfServiceAccountManagementSettings()).thenReturn(settings);

        var observable = accountService.resetPassword(user, client, "newpassword", new DefaultUser(), Optional.empty()).test();

        observable.awaitDone(10, TimeUnit.SECONDS);
        observable.assertError(InvalidPasswordException.class);

        verify(gatewayUserService, never()).resetPassword(any(), any(), any());
        verify(gatewayUserService, never()).checkPassword(any(), any(), any());
    }

    @Test
    public void shouldResetPassword_OldPasswordProvided() {
        final var client = mock(Client.class);
        final var user = mock(io.gravitee.am.model.User.class);

        final var settings = new SelfServiceAccountManagementSettings();
        final var resetPasswordSettings = new SelfServiceAccountManagementSettings.ResetPasswordSettings();
        resetPasswordSettings.setOldPasswordRequired(true);
        settings.setResetPassword(resetPasswordSettings);

        when(domain.getSelfServiceAccountManagementSettings()).thenReturn(settings);
        when(gatewayUserService.resetPassword(any(), any(), any())).thenReturn(Single.just(new ResetPasswordResponse()));
        when(gatewayUserService.checkPassword(any(), any(), any())).thenReturn(Completable.complete());

        final var oldpassword = "oldpassword";
        final var newpassword = "newpassword";
        var observable = accountService.resetPassword(user, client, newpassword, new DefaultUser(), Optional.of(oldpassword)).test();

        observable.awaitDone(10, TimeUnit.SECONDS);
        observable.assertNoErrors();

        verify(gatewayUserService).resetPassword(any(), any(), any());
        verify(gatewayUserService).checkPassword(any(), argThat(pwd -> pwd.equals(oldpassword)), any());
    }

    @Test
    public void shouldNotResetPassword_InvalidOldPassword() {
        final var client = mock(Client.class);
        final var user = mock(io.gravitee.am.model.User.class);

        final var settings = new SelfServiceAccountManagementSettings();
        final var resetPasswordSettings = new SelfServiceAccountManagementSettings.ResetPasswordSettings();
        resetPasswordSettings.setOldPasswordRequired(true);
        settings.setResetPassword(resetPasswordSettings);

        when(domain.getSelfServiceAccountManagementSettings()).thenReturn(settings);
        when(gatewayUserService.checkPassword(any(), any(), any())).thenReturn(Completable.error(new BadCredentialsException()));

        final var oldpassword = "oldpassword";
        final var newpassword = "newpassword";
        var observable = accountService.resetPassword(user, client, newpassword, new DefaultUser(), Optional.of(oldpassword)).test();

        observable.awaitDone(10, TimeUnit.SECONDS);
        observable.assertError(BadCredentialsException.class);

        verify(gatewayUserService).checkPassword(any(), argThat(pwd -> pwd.equals(oldpassword)), any());
        verify(gatewayUserService, never()).resetPassword(any(), any(), any());
    }

    @Test
    public void shouldUpdateUser() {
        final String userId = "user-id";
        final io.gravitee.am.model.User userUpdate = new io.gravitee.am.model.User();
        userUpdate.setSource("source");
        userUpdate.setId(userId);
        userUpdate.setExternalId("ext-"+userId);
        userUpdate.setAddress(Map.of("street", "my street", "city", "my city"));
        userUpdate.setBirthdate("01/01/1970");
        userUpdate.setDisplayName("Display Name");
        userUpdate.setEmail("user-update@acme.com");
        userUpdate.setFirstName("Updated FN");
        userUpdate.setLastName("Updated LN");
        userUpdate.setNickName("Updated NN");
        userUpdate.setMiddleName("Updated MN");
        userUpdate.setLocale("fr");
        userUpdate.setZoneInfo("UTC");
        userUpdate.setPhoneNumber("123456789");
        userUpdate.setPicture("https://picturestore.org/my/picture");
        userUpdate.setProfile("https://picturestore.org/my/profile");
        userUpdate.setWebsite("https://crazyhost/user-id");

        final UserProvider userProvider = mock(UserProvider.class);

        when(userValidator.validate(userUpdate)).thenReturn(Completable.complete());
        when(identityProviderManager.getUserProvider(userUpdate.getSource())).thenReturn(Maybe.just(userProvider));
        when(userProvider.update(any(), any())).thenReturn(Single.just(new DefaultUser()));
        when(userRepository.update(any())).thenReturn(Single.just(userUpdate));

        TestObserver testObserver = accountService.update(userUpdate).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userProvider).update(any(), argThat(idpUser ->
                userUpdate.getFirstName().equals(idpUser.getFirstName()) &&
                        userUpdate.getLastName().equals(idpUser.getLastName()) &&
                        userUpdate.getNickName().equals(idpUser.getAdditionalInformation().get(StandardClaims.NICKNAME)) &&
                        userUpdate.getMiddleName().equals(idpUser.getAdditionalInformation().get(StandardClaims.MIDDLE_NAME)) &&
                        userUpdate.getBirthdate().equals(idpUser.getAdditionalInformation().get(StandardClaims.BIRTHDATE)) &&
                        userUpdate.getPicture().equals(idpUser.getAdditionalInformation().get(StandardClaims.PICTURE)) &&
                        userUpdate.getProfile().equals(idpUser.getAdditionalInformation().get(StandardClaims.PROFILE)) &&
                        userUpdate.getWebsite().equals(idpUser.getAdditionalInformation().get(StandardClaims.WEBSITE)) &&
                        userUpdate.getEmail().equals(idpUser.getEmail())&&
                        userUpdate.getPhoneNumber().equals(idpUser.getAdditionalInformation().get(StandardClaims.PHONE_NUMBER))
        ));
        verify(userRepository).update(any());
    }
}
