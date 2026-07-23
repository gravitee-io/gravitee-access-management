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

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.jdbc.configuration.JdbcAuthenticationProviderConfigurationTest;
import io.gravitee.am.identityprovider.jdbc.user.spring.JdbcUserProviderConfiguration;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { JdbcAuthenticationProviderConfigurationTest.class, JdbcUserProviderConfiguration.class }, loader = AnnotationConfigContextLoader.class)
public class JdbcUserProvider_Test {

    @Autowired
    private UserProvider userProvider;

    @Test
    public void shouldSelectUserByUsername() {
        TestObserver<User> testObserver = userProvider.findByUsername("bob").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "bob".equals(u.getUsername()));
    }

    @Test
    public void shouldSelectUserByUsernameWithSpaces() {
        TestObserver<User> testObserver = userProvider.findByUsername("b o b").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "b o b".equals(u.getUsername()));
    }

    @Test
    public void shouldSelectUserByEmail() {
        TestObserver<User> testObserver = userProvider.findByEmail("user01@acme.com").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "user01".equals(u.getUsername()));
    }

    @Test
    public void shouldNotSelectUserByUsername_userNotFound() {
        TestObserver<User> testObserver = userProvider.findByUsername("unknown").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldNotSelectUserByEmail_userNotFound() {
        TestObserver<User> testObserver = userProvider.findByEmail("unknown@acme.com").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldCreate() {
        DefaultUser user = new DefaultUser("username1");
        TestObserver<User> testObserver = userProvider.create(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertValue(u -> u.getId() != null);
    }

    @Test
    public void shouldNotCreate_duplicateUsername() {
        DefaultUser user = new DefaultUser("duplicateUsername");
        userProvider.create(user).blockingGet();

        TestObserver<User> testObserver = userProvider.create(new DefaultUser("duplicateUsername")).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(UserAlreadyExistsException.class);
    }

    /**
     * Reproduces the check-then-act race: N concurrent creates for the same username can all pass the
     * {@code SELECT} uniqueness check before any {@code INSERT} commits. Exactly one insert should win the
     * unique index, and every other request must surface as {@link UserAlreadyExistsException}, not a raw
     * driver exception (e.g. R2dbcDataIntegrityViolationException / SQLSTATE 23505 on Postgres).
     */
    @Test
    public void shouldReturnUserAlreadyExists_onConcurrentCreate() throws InterruptedException {
        final String username = "concurrent-" + RandomString.generate();
        final int concurrency = 10;

        final CountDownLatch ready = new CountDownLatch(concurrency);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(concurrency);
        final List<User> successes = Collections.synchronizedList(new ArrayList<>());
        final List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            for (int i = 0; i < concurrency; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        userProvider.create(new DefaultUser(username))
                                .doFinally(done::countDown)
                                .subscribe(successes::add, failures::add);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertTrue("all concurrent creates should complete", done.await(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals("exactly one concurrent create should win the unique index", 1, successes.size());
        assertEquals(concurrency - 1, failures.size());
        assertTrue("every losing create must be reported as UserAlreadyExistsException, not a raw DB error",
                failures.stream().allMatch(UserAlreadyExistsException.class::isInstance));
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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertValue(u -> u.getCredentials() == null);
        testObserver.assertValue(u -> emailToUpdate.equals(u.getEmail()));
    }

    @Test
    public void shouldNotUpdate_userNotFound() {
        DefaultUser user = new DefaultUser("userToUpdate");
        TestObserver testObserver = userProvider.update("unknown", user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(UserNotFoundException.class);
        testObserver.assertNoValues();
    }

    @Test
    public void shouldDelete() {
        DefaultUser user = new DefaultUser("userToDelete");
        User createdUser = userProvider.create(user).blockingGet();
        userProvider.delete(createdUser.getId()).blockingAwait();

        TestObserver<User> testObserver = userProvider.findByUsername("userToDelete").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoValues();
    }

    @Test
    public void shouldNotDelete_userNotFound() {
        TestObserver testObserver = userProvider.delete("unknown").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(UserNotFoundException.class);
        testObserver.assertNoValues();
    }

    @Test
    public void must_not_updateUsername_null_username() {
        var user = new DefaultUser();
        user.setId("5");
        TestObserver testObserver = userProvider.updateUsername(user, null).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(IllegalArgumentException.class);
        testObserver.assertNoValues();
    }

    @Test
    public void must_not_updateUsername_empty_username() {
        var user = new DefaultUser();
        user.setId("5");
        TestObserver testObserver = userProvider.updateUsername(user, "").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(IllegalArgumentException.class);
        testObserver.assertNoValues();
    }

    @Test
    public void must_not_updateUsername_user_not_found() {
        var user = new DefaultUser();
        user.setId("7");
        TestObserver testObserver = userProvider.updateUsername(user, "newUsername").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(UserNotFoundException.class);
        testObserver.assertNoValues();
    }

    @Test
    public void must_updateUsername() {
        var user = new DefaultUser();
        user.setId("5");

        TestObserver testObserver = userProvider.updateUsername(user, "newUsername").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();

        TestObserver<User> testObserver2 = userProvider.findByUsername("newUsername").test();
        testObserver2.awaitDone(10, TimeUnit.SECONDS);
        testObserver2.assertComplete();
        testObserver2.assertNoErrors();
        testObserver2.assertValue(u -> "newUsername".equals(u.getUsername()));
    }
}
