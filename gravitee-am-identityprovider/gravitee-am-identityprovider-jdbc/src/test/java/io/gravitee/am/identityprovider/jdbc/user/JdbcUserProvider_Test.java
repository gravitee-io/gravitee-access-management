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
package io.gravitee.am.identityprovider.jdbc.user;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
public abstract class JdbcUserProvider_Test {

    @Autowired
    private UserProvider userProvider;

    @Test
    public void shouldSelectUserByUsername() {
        TestObserver<User> testObserver = userProvider.findByUsername("bob").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "bob".equals(u.getUsername()));
    }

    @Test
    public void shouldSelectUserByEmail() {
        TestObserver<User> testObserver = userProvider.findByEmail("user01@acme.com").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "user01".equals(u.getUsername()));
    }

    @Test
    public void shouldNotSelectUserByUsername_userNotFound() {
        TestObserver<User> testObserver = userProvider.findByUsername("unknown").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldNotSelectUserByEmail_userNotFound() {
        TestObserver<User> testObserver = userProvider.findByEmail("unknown@acme.com").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldCreate() {
        DefaultUser user = new DefaultUser("username1");
        TestObserver<User> testObserver = userProvider.create(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertValue(u -> u.getId() != null);
    }

    @Test
    public void shouldUpdate() {
        DefaultUser user = new DefaultUser("userToUpdate");
        user.setCredentials("password");
        user.setEmail("userToUpdate@acme.fr");
        User createdUser = userProvider.create(user).blockingGet();

        DefaultUser updateUser = new DefaultUser("userToUpdate");
        updateUser.setCredentials("password2");
        updateUser.setEmail("userToUpdate-email@acme.fr");
        userProvider.update(createdUser.getId(), updateUser).blockingGet();

        TestObserver<User> testObserver = userProvider.findByUsername("userToUpdate").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertValue(u -> u.getCredentials() == null);
        testObserver.assertValue(u -> "userToUpdate-email@acme.fr".equals(u.getEmail()));
    }

    @Test
    public void shouldUpdate_notPassword() {
        final String username = "userToUpdateNoPwd";
        DefaultUser user = new DefaultUser(username);
        user.setCredentials("password");
        user.setEmail(username + "@acme.fr");
        User createdUser = userProvider.create(user).blockingGet();

        DefaultUser updateUser = new DefaultUser(username);
        final String emailToUpdate = username + "-email@acme.fr";
        updateUser.setEmail(emailToUpdate);
        userProvider.update(createdUser.getId(), updateUser).blockingGet();

        TestObserver<User> testObserver = userProvider.findByUsername(username).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertValue(u -> u.getCredentials() == null);
        testObserver.assertValue(u -> emailToUpdate.equals(u.getEmail()));
    }

    @Test
    public void shouldNotUpdate_userNotFound() {
        DefaultUser user = new DefaultUser("userToUpdate");
        TestObserver testObserver = userProvider.update("unknown", user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(UserNotFoundException.class);
        testObserver.assertNoValues();
    }

    @Test
    public void shouldDelete() {
        DefaultUser user = new DefaultUser("userToDelete");
        User createdUser = userProvider.create(user).blockingGet();
        userProvider.delete(createdUser.getId()).blockingGet();

        TestObserver<User> testObserver = userProvider.findByUsername("userToDelete").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldNotDelete_userNotFound() {
        TestObserver testObserver = userProvider.delete("unknown").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(UserNotFoundException.class);
        testObserver.assertNoValues();
    }

    @Test
    public void must_not_updateUsername_null_username() {
        TestObserver testObserver = userProvider.updateUsername("5", null).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(IllegalArgumentException.class);
        testObserver.assertNoValues();
    }

    @Test
    public void must_not_updateUsername_empty_username() {
        TestObserver testObserver = userProvider.updateUsername("5", "").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(IllegalArgumentException.class);
        testObserver.assertNoValues();
    }

    @Test
    public void must_not_updateUsername_user_not_found() {
        TestObserver testObserver = userProvider.updateUsername("6", "newUsername").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(UserNotFoundException.class);
        testObserver.assertNoValues();
    }

    @Test
    public void must_updateUsername() {
        TestObserver testObserver = userProvider.updateUsername("5", "newUsername").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();

        TestObserver<User> testObserver2 = userProvider.findByUsername("newUsername").test();
        testObserver2.awaitTerminalEvent();
        testObserver2.assertComplete();
        testObserver2.assertNoErrors();
        testObserver2.assertValue(u -> "newUsername".equals(u.getUsername()));
    }
}
