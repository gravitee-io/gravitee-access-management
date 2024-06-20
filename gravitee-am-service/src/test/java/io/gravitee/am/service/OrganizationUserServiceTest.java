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
package io.gravitee.am.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.AccountAccessTokenRepository;
import io.gravitee.am.repository.management.api.OrganizationUserRepository;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.exception.EmailFormatInvalidException;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.TooManyAccountTokenException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.impl.OrganizationUserServiceImpl;
import io.gravitee.am.service.model.NewAccountAccessToken;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.validators.email.EmailValidatorImpl;
import io.gravitee.am.service.validators.user.UserValidator;
import io.gravitee.am.service.validators.user.UserValidatorImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.platform.commons.util.StringUtils;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static io.gravitee.am.common.audit.EventType.USER_CREATED;
import static io.gravitee.am.service.validators.email.EmailValidatorImpl.EMAIL_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.NAME_LAX_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.NAME_STRICT_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.USERNAME_PATTERN;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class OrganizationUserServiceTest {

    @InjectMocks
    private OrganizationUserService userService = new OrganizationUserServiceImpl();

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private UserValidator userValidator = new UserValidatorImpl(
            NAME_STRICT_PATTERN,
            NAME_LAX_PATTERN,
            USERNAME_PATTERN,
            new EmailValidatorImpl(EMAIL_PATTERN, true)
    );

    @Mock
    private OrganizationUserRepository userRepository;

    @Mock
    private AccountAccessTokenRepository accessTokenRepository;

    @Mock
    private CredentialService credentialService;

    @Mock
    private AuditService auditService;

    private final static String ORG = "organization1";

    @Test
    public void shouldCreate() {
        NewUser newUser = Mockito.mock(NewUser.class);
        User user = new User();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        when(newUser.getUsername()).thenReturn("username");
        when(newUser.getSource()).thenReturn("source");
        when(userRepository.create(any(User.class))).thenReturn(Single.just(user));
        when(userRepository.findByUsernameAndSource(ReferenceType.ORGANIZATION, ORG, newUser.getUsername(), newUser.getSource())).thenReturn(Maybe.empty());

        TestObserver testObserver = userService.create(ReferenceType.ORGANIZATION, ORG, newUser).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userRepository, times(1)).create(any(User.class));
        verify(auditService, times(1)).report(argThat(
                audit -> USER_CREATED.equals(audit.build(new ObjectMapper()).getType())
        ));
    }

    @Test
    public void shouldNotCreateWhenUserInvalidException() {
        User user = new User();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        NewUser newUser = new NewUser();
        newUser.setEmail("invalid");

        when(userRepository.findByUsernameAndSource(any(), any(), any(), any())).thenReturn(Maybe.empty());

        TestObserver<User> testObserver = userService.create(ReferenceType.ORGANIZATION, ORG, newUser).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(UserInvalidException.class);

        verify(userRepository, never()).create(any(User.class));
    }

    @Test
    public void shouldNotCreate_invalidUserException() {
        User user = new User();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        NewUser newUser = new NewUser();
        newUser.setUsername("##&##");
        when(userRepository.findByUsernameAndSource(ReferenceType.ORGANIZATION, ORG, newUser.getUsername(), newUser.getSource())).thenReturn(Maybe.empty());
        when(userRepository.create(any(User.class))).thenReturn(Single.just(user));

        TestObserver<User> testObserver = userService.create(ReferenceType.ORGANIZATION, ORG, newUser).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidUserException.class);
    }

    @Test
    public void shouldCreate_technicalException() {
        NewUser newUser = Mockito.mock(NewUser.class);
        when(newUser.getUsername()).thenReturn("username");
        when(newUser.getSource()).thenReturn("source");
        when(userRepository.findByUsernameAndSource(ReferenceType.ORGANIZATION, ORG, newUser.getUsername(), newUser.getSource())).thenReturn(Maybe.empty());
        when(userRepository.create(any(User.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        userService.create(ReferenceType.ORGANIZATION, ORG, newUser).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate_alreadyExists() {
        NewUser newUser = Mockito.mock(NewUser.class);
        when(newUser.getUsername()).thenReturn("username");
        when(newUser.getSource()).thenReturn("source");
        when(userRepository.findByUsernameAndSource(ReferenceType.ORGANIZATION, ORG, newUser.getUsername(), newUser.getSource())).thenReturn(Maybe.just(new User()));

        TestObserver testObserver = new TestObserver();
        userService.create(ReferenceType.ORGANIZATION, ORG, newUser).subscribe(testObserver);

        testObserver.assertError(UserAlreadyExistsException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate() {
        UpdateUser updateUser = Mockito.mock(UpdateUser.class);
        User user = new User();
        user.setId("my-user");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        when(userRepository.findById(ReferenceType.ORGANIZATION, ORG, user.getId())).thenReturn(Maybe.just(user));
        when(userRepository.findByUsernameAndSource(eq(ReferenceType.ORGANIZATION), eq(ORG), any(), any())).thenReturn(Maybe.just(user));
        when(userRepository.update(any(User.class))).thenReturn(Single.just(user));

        var testObserver = userService.update(ReferenceType.ORGANIZATION, ORG, "my-user", updateUser).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userRepository, times(2)).findById(ReferenceType.ORGANIZATION, ORG, user.getId());
        verify(userRepository, times(1)).update(any(User.class));
    }

    @Test
    public void shouldNotUpdate_emailFormatInvalidException() {
        User user = new User();
        user.setId("my-user");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        UpdateUser updateUser = new UpdateUser();
        updateUser.setEmail("invalid");
        when(userRepository.findByUsernameAndSource(eq(ReferenceType.ORGANIZATION), eq(ORG), any(), any())).thenReturn(Maybe.just(user));
        when(userRepository.findById(ReferenceType.ORGANIZATION, ORG, user.getId())).thenReturn(Maybe.just(user));

        TestObserver<User> testObserver = userService.update(ReferenceType.ORGANIZATION, ORG, "my-user", updateUser).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(EmailFormatInvalidException.class);
    }

    @Test
    public void shouldNotUpdate_invalidUserException() {
        User user = new User();
        user.setId("my-user");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        UpdateUser updateUser = new UpdateUser();
        updateUser.setFirstName("$$^^^^¨¨¨)");
        when(userRepository.findByUsernameAndSource(eq(ReferenceType.ORGANIZATION), eq(ORG), any(), any())).thenReturn(Maybe.just(user));
        when(userRepository.findById(ReferenceType.ORGANIZATION, ORG, user.getId())).thenReturn(Maybe.just(user));

        TestObserver<User> testObserver = userService.update(ReferenceType.ORGANIZATION, ORG, "my-user", updateUser).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidUserException.class);
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateUser updateUser = Mockito.mock(UpdateUser.class);
        when(userRepository.findById(ReferenceType.ORGANIZATION, ORG, "my-user")).thenReturn(Maybe.just(new User()));

        TestObserver testObserver = new TestObserver();
        userService.update(ReferenceType.ORGANIZATION, ORG, "my-user", updateUser).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate_userNotFound() {
        UpdateUser updateUser = Mockito.mock(UpdateUser.class);
        when(userRepository.findById(ReferenceType.ORGANIZATION, ORG, "my-user")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        userService.update(ReferenceType.ORGANIZATION, ORG, "my-user", updateUser).subscribe(testObserver);

        testObserver.assertError(UserNotFoundException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        User user = new User();
        user.setId("my-user");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        when(userRepository.findById("my-user")).thenReturn(Maybe.just(user));
        when(userRepository.delete("my-user")).thenReturn(Completable.complete());
        when(credentialService.findByUserId(user.getReferenceType(), user.getReferenceId(), user.getId())).thenReturn(Flowable.empty());
        when(accessTokenRepository.deleteByUserId(any(), any(), any())).thenReturn(Completable.complete());

        TestObserver testObserver = userService.delete("my-user").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userRepository, times(1)).delete("my-user");
        verify(credentialService, never()).delete(anyString());
    }

    @Test
    public void shouldDelete_with_webauthn_credentials() {
        User user = new User();
        user.setId("my-user");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        Credential credential = new Credential();
        credential.setId("credential-id");

        when(userRepository.findById("my-user")).thenReturn(Maybe.just(user));
        when(userRepository.delete("my-user")).thenReturn(Completable.complete());
        when(credentialService.findByUserId(user.getReferenceType(), user.getReferenceId(), user.getId())).thenReturn(Flowable.just(credential));
        when(credentialService.delete(credential.getId(), false)).thenReturn(Completable.complete());
        when(accessTokenRepository.deleteByUserId(any(), any(), any())).thenReturn(Completable.complete());

        TestObserver testObserver = userService.delete("my-user").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userRepository, times(1)).delete("my-user");
        verify(credentialService, times(1)).delete("credential-id", false);
    }

    @Test
    public void shouldDelete_technicalException() {
        when(userRepository.findById("my-user")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        userService.delete("my-user").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_userNotFound() {
        when(userRepository.findById("my-user")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        userService.delete("my-user").subscribe(testObserver);

        testObserver.assertError(UserNotFoundException.class);
        testObserver.assertNotComplete();

        verify(userRepository, never()).delete("my-user");
    }

    @Test
    public void shouldGenerateAccountToken() {
        User user = new User();
        user.setId("my-user");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        var issuerUserId = "issuerUserId";
        var issuerUser = new User();
        issuerUser.setId(issuerUserId);
        when(userRepository.findById(issuerUserId)).thenReturn(Maybe.just(issuerUser));

        when(accessTokenRepository.create(any())).thenAnswer(invocation -> Single.just(invocation.getArguments()[0]));
        when(accessTokenRepository.findByUserId(any(), any(), any())).thenAnswer(invocation -> Flowable.empty());

        var newTokenRequest = new NewAccountAccessToken("test-token");
        userService.generateAccountAccessToken(user, newTokenRequest, issuerUserId)
                .test()
                .assertComplete()
                .assertNoErrors()
                .assertValue(token -> user.getId().equals(token.userId()))
                .assertValue(token -> user.getReferenceId().equals(token.referenceId()))
                .assertValue(token -> ReferenceType.ORGANIZATION == token.referenceType())
                .assertValue(token -> token.token() != null);

    }

    @Test
    public void shouldFindTokensByUser() {
        var userId = "userId";
        var accessToken1 = AccountAccessToken.builder().tokenId("1").token("12345").userId(userId).build();
        var accessToken2 = AccountAccessToken.builder().tokenId("2").token("678912").userId(userId).build();
        var organizationId = "organizationId";
        when(accessTokenRepository.findByUserId(ReferenceType.ORGANIZATION, organizationId, userId)).thenReturn(Flowable.just(accessToken2, accessToken1));

        userService.findUserAccessTokens(organizationId, userId).toList().test()
                .assertComplete()
                .assertNoErrors()
                .assertValue(tokens -> tokens.size() == 2)
                .assertValue(tokens -> tokens.stream().allMatch(i -> i.tokenId().equals("1") || i.tokenId().equals("2")))
                .assertValue(tokens -> tokens.stream().allMatch(i -> StringUtils.isBlank(i.issuerId())))
                .assertValue(tokens -> tokens.stream().allMatch(i -> i.token() == null));

        verify(accessTokenRepository, times(1)).findByUserId(any(), any(), any());
    }

    @Test
    public void shouldFindTokensByUserWithTokenIssuer() {
        var userId = "userId";
        var issuerUserId = "issuerUserId";
        var organizationId = "organizationId";
        var accessToken1 = AccountAccessToken.builder().tokenId("1").token("12345").userId(userId).issuerId(issuerUserId).build();
        var accessToken2 = AccountAccessToken.builder().tokenId("2").token("678912").userId(userId).issuerId(issuerUserId).build();
        var issuerUser = new User();
        issuerUser.setId(issuerUserId);
        when(accessTokenRepository.findByUserId(ReferenceType.ORGANIZATION, organizationId, userId)).thenReturn(Flowable.just(accessToken2, accessToken1));
        when(userRepository.findById(issuerUserId)).thenReturn(Maybe.just(issuerUser));

        userService.findUserAccessTokens(organizationId, userId).toList()
                .test()
                .assertComplete()
                .assertNoErrors()
                .assertValue(tokens -> tokens.size() == 2)
                .assertValue(tokens -> tokens.stream().allMatch(i -> i.tokenId().equals("1") || i.tokenId().equals("2")))
                .assertValue(tokens -> tokens.stream().allMatch(i -> i.issuerId().equals(issuerUserId)))
                .assertValue(tokens -> tokens.stream().allMatch(i -> i.token() == null));

        verify(accessTokenRepository, times(1)).findByUserId(any(), any(), any());
    }

    @Test
    public void shouldFindTokensByUserWithTokenIssuerNotFound() {
        var userId = "userId";
        var issuerUserId = "issuerUserId";
        var organizationId = "organizationId";
        var accessToken1 = AccountAccessToken.builder().tokenId("1").token("12345").userId(userId).issuerId(issuerUserId).build();
        var accessToken2 = AccountAccessToken.builder().tokenId("2").token("678912").userId(userId).issuerId(issuerUserId).build();
        when(accessTokenRepository.findByUserId(ReferenceType.ORGANIZATION, organizationId, userId)).thenReturn(Flowable.just(accessToken2, accessToken1));
        when(userRepository.findById(issuerUserId)).thenReturn(Maybe.empty());

        userService.findUserAccessTokens(organizationId, userId).toList()
                .test()
                .assertComplete()
                .assertNoErrors()
                .assertValue(tokens -> tokens.size() == 2)
                .assertValue(tokens -> tokens.stream().allMatch(i -> i.tokenId().equals("1") || i.tokenId().equals("2")))
                .assertValue(tokens -> tokens.stream().allMatch(i -> StringUtils.isBlank(i.issuerUsername())))
                .assertValue(tokens -> tokens.stream().allMatch(i -> i.token() == null));

        verify(accessTokenRepository, times(1)).findByUserId(any(), any(), any());
    }

    @Test
    public void shouldNotGenerateAccountToken_LimitReached() {
        User user = new User();
        user.setId("my-user");
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        when(accessTokenRepository.findByUserId(any(), any(), any()))
                .thenAnswer(invocation -> Flowable.fromStream(IntStream.range(0, 20)
                        .mapToObj(i -> AccountAccessToken.builder()
                                .tokenId("" + i)
                                .build())));

        var newTokenRequest = new NewAccountAccessToken("test-token");
        userService.generateAccountAccessToken(user, newTokenRequest, "issuer")
                .test()
                .assertError(TooManyAccountTokenException.class);
        verify(accessTokenRepository, never()).create(any());

    }

    @Test
    public void shouldRevokeToken() {
        var tokenToDelete = new AccountAccessToken("tokenId", ReferenceType.ORGANIZATION, "orgId", "userId", "testIssuer", "testIssuerId", "testToken", null, null, null);
        when(accessTokenRepository.findById("tokenId")).thenReturn(Maybe.just(tokenToDelete));
        when(accessTokenRepository.delete(anyString())).thenReturn(Completable.complete());
        userService.revokeToken("orgId", "userId", "tokenId")
                .test()
                .assertComplete();
    }

    @Test
    public void wrongOrg_shouldNotRevokeToken() {
        var tokenToDelete = new AccountAccessToken("tokenId", ReferenceType.ORGANIZATION, "orgId", "userId", "testIssuer", "testIssuerId", "testToken", null, null, null);
        when(accessTokenRepository.findById("tokenId")).thenReturn(Maybe.just(tokenToDelete));
        userService.revokeToken("wrongOrgId", "userId", "tokenId")
                .test()
                .assertError(NoSuchElementException.class);
    }

    @Test
    public void wrongUser_shouldNotRevokeToken() {
        var tokenToDelete = new AccountAccessToken("tokenId", ReferenceType.ORGANIZATION, "orgId", "userId", "testIssuer", "testIssuerId", "testToken", null, null, null);
        when(accessTokenRepository.findById("tokenId")).thenReturn(Maybe.just(tokenToDelete));
        userService.revokeToken("orgId", "wrongUserId", "tokenId")
                .test()
                .assertError(NoSuchElementException.class);
    }
}
