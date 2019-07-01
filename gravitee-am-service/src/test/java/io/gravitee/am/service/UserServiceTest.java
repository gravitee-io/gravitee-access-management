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

import io.gravitee.am.model.Group;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.GroupRepository;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.impl.UserServiceImpl;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    @InjectMocks
    private UserService userService = new UserServiceImpl();

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupRepository groupRepository;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(userRepository.findById("my-user")).thenReturn(Maybe.just(new User()));
        TestObserver testObserver = userService.findById("my-user").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingUser() {
        when(userRepository.findById("my-user")).thenReturn(Maybe.empty());
        TestObserver testObserver = userService.findById("my-user").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(userRepository.findById("my-user")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        userService.findById("my-user").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }


    @Test
    public void shouldFindByDomain() {
        when(userRepository.findByDomain(DOMAIN)).thenReturn(Single.just(Collections.singleton(new User())));
        TestObserver<Set<User>> testObserver = userService.findByDomain(DOMAIN).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.size() == 1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(userRepository.findByDomain(DOMAIN)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        userService.findByDomain(DOMAIN).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomainPagination() {
        Page pageUsers = new Page(Collections.singleton(new User()), 1 , 1);
        when(userRepository.findByDomain(DOMAIN, 1 , 1)).thenReturn(Single.just(pageUsers));
        TestObserver<Page<User>> testObserver = userService.findByDomain(DOMAIN, 1, 1).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.getData().size() == 1);
    }

    @Test
    public void shouldFindByDomainPagination_technicalException() {
        when(userRepository.findByDomain(DOMAIN, 1 , 1)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver<>();
        userService.findByDomain(DOMAIN, 1 , 1).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldLoadUserByUsernameAndDomain() {
        when(userRepository.findByUsernameAndDomain(DOMAIN, "my-user")).thenReturn(Maybe.just(new User()));
        TestObserver testObserver = userService.findByDomainAndUsername(DOMAIN, "my-user").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldLoadUserByUsernameAndDomain_notExistingUser() {
        when(userRepository.findByUsernameAndDomain(DOMAIN, "my-user")).thenReturn(Maybe.empty());
        TestObserver testObserver = userService.findByDomainAndUsername(DOMAIN, "my-user").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldLoadUserByUsernameAndDomain_technicalException() {
        when(userRepository.findByUsernameAndDomain(DOMAIN, "my-user")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        userService.findByDomainAndUsername(DOMAIN, "my-user").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewUser newUser = Mockito.mock(NewUser.class);
        when(newUser.getUsername()).thenReturn("username");
        when(newUser.getSource()).thenReturn("source");
        when(userRepository.create(any(User.class))).thenReturn(Single.just(new User()));
        when(userRepository.findByDomainAndUsernameAndSource(DOMAIN, newUser.getUsername(), newUser.getSource())).thenReturn(Maybe.empty());

        TestObserver testObserver = userService.create(DOMAIN, newUser).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userRepository, times(1)).create(any(User.class));
    }

    @Test
    public void shouldCreate_technicalException() {
        NewUser newUser = Mockito.mock(NewUser.class);
        when(newUser.getUsername()).thenReturn("username");
        when(newUser.getSource()).thenReturn("source");
        when(userRepository.findByDomainAndUsernameAndSource(DOMAIN, newUser.getUsername(), newUser.getSource())).thenReturn(Maybe.empty());
        when(userRepository.create(any(User.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        userService.create(DOMAIN, newUser).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate_alreadyExists() {
        NewUser newUser = Mockito.mock(NewUser.class);
        when(newUser.getUsername()).thenReturn("username");
        when(newUser.getSource()).thenReturn("source");
        when(userRepository.findByDomainAndUsernameAndSource(DOMAIN, newUser.getUsername(), newUser.getSource())).thenReturn(Maybe.just(new User()));

        TestObserver testObserver = new TestObserver();
        userService.create(DOMAIN, newUser).subscribe(testObserver);

        testObserver.assertError(UserAlreadyExistsException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate() {
        UpdateUser updateUser = Mockito.mock(UpdateUser.class);
        when(userRepository.findById("my-user")).thenReturn(Maybe.just(new User()));
        when(userRepository.update(any(User.class))).thenReturn(Single.just(new User()));

        TestObserver testObserver = userService.update(DOMAIN, "my-user", updateUser).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userRepository, times(1)).findById("my-user");
        verify(userRepository, times(1)).update(any(User.class));
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateUser updateUser = Mockito.mock(UpdateUser.class);
        when(userRepository.findById("my-user")).thenReturn(Maybe.just(new User()));
        when(userRepository.update(any(User.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        userService.update(DOMAIN, "my-user", updateUser).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate_userNotFound() {
        UpdateUser updateUser = Mockito.mock(UpdateUser.class);
        when(userRepository.findById("my-user")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        userService.update(DOMAIN, "my-user", updateUser).subscribe(testObserver);

        testObserver.assertError(UserNotFoundException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        when(userRepository.findById("my-user")).thenReturn(Maybe.just(new User()));
        when(userRepository.delete("my-user")).thenReturn(Completable.complete());

        TestObserver testObserver = userService.delete("my-user").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(userRepository, times(1)).delete("my-user");
    }

    @Test
    public void shouldDelete_technicalException() {
        when(userRepository.findById("my-user")).thenReturn(Maybe.just(new User()));
        when(userRepository.delete("my-user")).thenReturn(Completable.error(TechnicalException::new));

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
    public void shouldFindOrCreate_CreateEmptyUser() {
        String domain = "Domain";
        String username = "foo";
        String source = "SRC";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getUsername()).thenReturn(username);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);
        when(userRepository.findByDomainAndUsernameAndSource(domain, username, source)).thenReturn(Maybe.empty());
        when(userRepository.create(any())).thenReturn(Single.just(mock(User.class)));

        TestObserver testObserver = userService.findOrCreate(domain, user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userRepository, times(1)).create(any());
        verify(userRepository, never()).update(any());
    }

    @Test
    public void shouldFindOrCreate_UpdateKnownUser() {
        String domain = "Domain";
        String username = "foo";
        String source = "SRC";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getUsername()).thenReturn(username);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);
        when(userRepository.findByDomainAndUsernameAndSource(domain, username, source)).thenReturn(Maybe.just(mock(User.class)));
        when(userRepository.update(any())).thenReturn(Single.just(mock(User.class)));

        TestObserver testObserver = userService.findOrCreate(domain, user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userRepository, never()).create(any());
        verify(userRepository, times(1)).update(any());
    }

    @Test
    public void shouldFindOrCreate_UpdateKnownUserWithEmptyGroup() {
        String domain = "Domain";
        String username = "foo";
        String source = "SRC";
        io.gravitee.am.identityprovider.api.User user = mock(io.gravitee.am.identityprovider.api.User.class);
        when(user.getUsername()).thenReturn(username);
        HashMap<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("source", source);
        Map<String, List<String>> groupMapping = new HashMap<>();
        groupMapping.put("foo", Collections.singletonList("bar"));
        additionalInformation.put("_RESERVED_AM_GROUP_MAPPING_", groupMapping);
        when(user.getAdditionalInformation()).thenReturn(additionalInformation);
        User existingUser = mock(User.class);
        when(userRepository.findByDomainAndUsernameAndSource(domain, username, source)).thenReturn(Maybe.just(existingUser));
        when(userRepository.update(any())).thenReturn(Single.just(mock(User.class)));
        Group group = mock(Group.class);
        when(group.getMembers()).thenReturn(null);
        when(groupRepository.findByIdIn(any())).thenReturn(Single.just(Collections.singletonList(group)));

        TestObserver testObserver = userService.findOrCreate(domain, user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(userRepository, never()).create(any());
        verify(userRepository, times(1)).update(any());
    }
}
