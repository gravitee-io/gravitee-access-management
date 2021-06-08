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

import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.OrganizationUserRepository;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.impl.OrganizationUserServiceImpl;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.validators.UserValidator;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class OrganizationUserServiceTest {

    @InjectMocks
    private OrganizationUserService userService = new OrganizationUserServiceImpl();

    @Spy
    private UserValidator userValidator = new UserValidator();

    @Mock
    private OrganizationUserRepository userRepository;

    @Mock
    private EventService eventService;

    @Mock
    private CredentialService credentialService;

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
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = userService.create(ReferenceType.ORGANIZATION, ORG, newUser).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userRepository, times(1)).create(any(User.class));
        verify(eventService, times(1)).create(any());
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
        testObserver.awaitTerminalEvent();

        testObserver.assertError(EmailFormatInvalidException.class);

        verifyZeroInteractions(eventService);
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
        testObserver.awaitTerminalEvent();

        testObserver.assertError(InvalidUserException.class);

        verifyZeroInteractions(eventService);
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
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        when(userRepository.findById(eq(ReferenceType.ORGANIZATION), eq(ORG), eq("my-user"))).thenReturn(Maybe.just(user));
        when(userRepository.findByUsernameAndSource(eq(ReferenceType.ORGANIZATION), eq(ORG), any(), any())).thenReturn(Maybe.just(user));
        when(userRepository.update(any(User.class))).thenReturn(Single.just(user));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = userService.update(ReferenceType.ORGANIZATION, ORG, "my-user", updateUser).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userRepository, times(1)).findById(eq(ReferenceType.ORGANIZATION), eq(ORG), eq("my-user"));
        verify(userRepository, times(1)).update(any(User.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldNotUpdate_emailFormatInvalidException() {
        User user = new User();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        UpdateUser updateUser = new UpdateUser();
        updateUser.setEmail("invalid");
        when(userRepository.findByUsernameAndSource(eq(ReferenceType.ORGANIZATION), eq(ORG), any(), any())).thenReturn(Maybe.just(user));
        when(userRepository.findById(eq(ReferenceType.ORGANIZATION), eq(ORG), eq("my-user"))).thenReturn(Maybe.just(user));

        TestObserver<User> testObserver = userService.update(ReferenceType.ORGANIZATION, ORG, "my-user", updateUser).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(EmailFormatInvalidException.class);

        verifyZeroInteractions(eventService);
    }

    @Test
    public void shouldNotUpdate_invalidUserException() {
        User user = new User();
        user.setReferenceType(ReferenceType.ORGANIZATION);
        user.setReferenceId(ORG);

        UpdateUser updateUser = new UpdateUser();
        updateUser.setFirstName("$$^^^^¨¨¨)");
        when(userRepository.findByUsernameAndSource(eq(ReferenceType.ORGANIZATION), eq(ORG), any(), any())).thenReturn(Maybe.just(user));
        when(userRepository.findById(eq(ReferenceType.ORGANIZATION), eq(ORG), eq("my-user"))).thenReturn(Maybe.just(user));

        TestObserver<User> testObserver = userService.update(ReferenceType.ORGANIZATION, ORG, "my-user", updateUser).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(InvalidUserException.class);

        verifyZeroInteractions(eventService);
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
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(credentialService.findByUserId(user.getReferenceType(), user.getReferenceId(), user.getId())).thenReturn(Flowable.empty());

        TestObserver testObserver = userService.delete("my-user").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userRepository, times(1)).delete("my-user");
        verify(eventService, times(1)).create(any());
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
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(credentialService.findByUserId(user.getReferenceType(), user.getReferenceId(), user.getId())).thenReturn(Flowable.just(credential));
        when(credentialService.delete(credential.getId())).thenReturn(Completable.complete());

        TestObserver testObserver = userService.delete("my-user").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userRepository, times(1)).delete("my-user");
        verify(eventService, times(1)).create(any());
        verify(credentialService, times(1)).delete("credential-id");
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
