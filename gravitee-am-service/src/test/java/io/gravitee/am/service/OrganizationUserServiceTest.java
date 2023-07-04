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
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.OrganizationUserRepository;
import io.gravitee.am.service.exception.EmailFormatInvalidException;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.impl.OrganizationUserServiceImpl;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.validators.email.EmailValidatorImpl;
import io.gravitee.am.service.validators.user.UserValidator;
import io.gravitee.am.service.validators.user.UserValidatorImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

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

    @Spy
    private UserValidator userValidator = new UserValidatorImpl(
            NAME_STRICT_PATTERN,
            NAME_LAX_PATTERN,
            USERNAME_PATTERN,
            new EmailValidatorImpl(EMAIL_PATTERN)
    );

    @Mock
    private OrganizationUserRepository userRepository;
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
            (ArgumentMatcher<AuditBuilder<Audit>>) audit -> USER_CREATED.equals(audit.build(new ObjectMapper()).getType())
        ));
    }

    @Test
    public void shouldNotCreate_emailFormatInvalidException() {
        User user = new User();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        NewUser newUser = new NewUser();
        newUser.setEmail("invalid");
        when(userRepository.findByUsernameAndSource(ReferenceType.ORGANIZATION, ORG, newUser.getUsername(), newUser.getSource())).thenReturn(Maybe.empty());
        when(userRepository.create(any(User.class))).thenReturn(Single.just(user));

        TestObserver<User> testObserver = userService.create(ReferenceType.ORGANIZATION, ORG, newUser).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(EmailFormatInvalidException.class);
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
        when(userRepository.findById(eq(ReferenceType.ORGANIZATION), eq(ORG), eq("my-user"))).thenReturn(Maybe.just(new User()));

        TestObserver testObserver = new TestObserver();
        userService.update(ReferenceType.ORGANIZATION, ORG, "my-user", updateUser).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate_userNotFound() {
        UpdateUser updateUser = Mockito.mock(UpdateUser.class);
        when(userRepository.findById(eq(ReferenceType.ORGANIZATION), eq(ORG), eq("my-user"))).thenReturn(Maybe.empty());

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
        when(credentialService.delete(credential.getId(),false)).thenReturn(Completable.complete());

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
}
