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
package io.gravitee.am.identityprovider.mongo.user;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.mongo.authentication.spring.MongoAuthenticationProviderConfiguration;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.util.Maps;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {
    MongoUserProviderTestConfiguration.class,
    MongoAuthenticationProviderConfiguration.class
}, loader = AnnotationConfigContextLoader.class)
public class MongoUserProviderTest {

    @Autowired
    private UserProvider userProvider;

    @Autowired
    private MongoIdentityProviderConfiguration configuration;

    @Before
    public void setup(){
        configuration.setUsernameCaseSensitive(false);
    }

    @Test
    public void shouldSelectUserByUsername() {
        TestObserver<User> testObserver = userProvider.findByUsername("bob").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "BoB".toLowerCase().equals(u.getUsername()));
    }

    @Test
    public void shouldSelectUserByUsername_caseInsensitive() {
        configuration.setUsernameCaseSensitive(true);

        TestObserver<User> testObserver = userProvider.findByUsername("UserWithCase").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "UserWithCase".equals(u.getUsername()));
    }

    @Test
    public void shouldNotSelectUserByUsername_caseInsensitive() {
        configuration.setUsernameCaseSensitive(true);

        TestObserver<User> testObserver = userProvider.findByUsername("BoB").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
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
    public void shouldSelectUserByEmail_AlternativeAttribute() {
        TestObserver<User> testObserver = userProvider.findByEmail("user02-alt@acme.com").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "user02".equals(u.getUsername()));
    }

    @Test
    public void shouldNotSelectUserByUsername_userNotFound() {
        configuration.setUsernameCaseSensitive(false);

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
    public void shouldCreateUser_insensitiveCase() {
        final String usernameWithCase = "UsernameWithCase";
        DefaultUser user = createUserBean(usernameWithCase);
        TestObserver<User> testObserver = userProvider.create(user).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> assertUserMatch(user, u));
        testObserver.assertValue(u -> u.getUsername().equals(usernameWithCase.toLowerCase()));
    }

    @Test
    public void shouldCreateUser_sensitiveCase() {
        configuration.setUsernameCaseSensitive(true);
        final String usernameWithCase = "UsernameWithCase";
        DefaultUser user = createUserBean(usernameWithCase);
        TestObserver<User> testObserver = userProvider.create(user).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> assertUserMatch(user, u));
        testObserver.assertValue(u -> u.getUsername().equals(usernameWithCase));
    }

    @Test
    public void shouldNotCreateUser_duplicateUsername() {
        DefaultUser user = createUserBean("duplicateUsername");
        userProvider.create(user).blockingGet();

        TestObserver<User> testObserver = userProvider.create(createUserBean("duplicateUsername")).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(UserAlreadyExistsException.class);
    }

    /**
     * Reproduces the check-then-act race: N concurrent creates for the same username can all pass the
     * {@code findByUsername} uniqueness check before any insert commits. Exactly one insert should win the
     * {@code u1_unique} index, and every other request must surface as {@link UserAlreadyExistsException}, not
     * a raw driver exception (e.g. a MongoWriteException for E11000 duplicate key).
     */
    @Test
    public void shouldReturnUserAlreadyExists_onConcurrentCreate() throws InterruptedException {
        final String username = "concurrent-" + java.util.UUID.randomUUID();
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
                        userProvider.create(createUserBean(username))
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
    public void shouldUpdateUser() {
        DefaultUser user = createUserBean();
        User createdUser = userProvider.create(user).blockingGet();

        assertUserMatch(user, createdUser);

        DefaultUser userToUpdate = (DefaultUser) createdUser;
        userToUpdate.setLastName("LUPDATE");
        userToUpdate.setFirstName("FUPDATE");
        userToUpdate.setEmail("update@acme.fr");
        userToUpdate.setCredentials("toto");
        userToUpdate.getAdditionalInformation().put("email", userToUpdate.getEmail());
        userToUpdate.getAdditionalInformation().put("given_name", userToUpdate.getFirstName());
        userToUpdate.getAdditionalInformation().put("family_name", userToUpdate.getLastName());

        final TestObserver<User> observer = userProvider.update(createdUser.getId(), userToUpdate).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(u -> assertUserMatch(userToUpdate, u));
        observer.assertValue(u -> u.getCredentials().equals(userToUpdate.getCredentials()));
    }

    /*
    * The existing user credential is null in case of pre-registration feature.
    * User set the password upon receiving email invitation.
    * This test is written specifically for github issue: 8321
    * */
    @Test
    public void shouldUpdateUser_Without_Credential() {
        DefaultUser user = createUserBean();
        user.setCredentials(null);
        User createdUser = userProvider.create(user).blockingGet();

        assertUserMatch(user, createdUser);

        DefaultUser userToUpdate = (DefaultUser) createdUser;
        userToUpdate.setLastName("LUPDATE");
        userToUpdate.setFirstName("FUPDATE");
        userToUpdate.setEmail("update@acme.fr");
        userToUpdate.setCredentials("toto");
        userToUpdate.getAdditionalInformation().put("email", userToUpdate.getEmail());
        userToUpdate.getAdditionalInformation().put("given_name", userToUpdate.getFirstName());
        userToUpdate.getAdditionalInformation().put("family_name", userToUpdate.getLastName());

        final TestObserver<User> observer = userProvider.update(createdUser.getId(), userToUpdate).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(u -> assertUserMatch(userToUpdate, u));
        observer.assertValue(u -> u.getCredentials().equals(userToUpdate.getCredentials()));
    }

    @Test
    public void shouldUpdatePassword_NothingExceptedPassword_isUpdated() {
        DefaultUser user = createUserBean();
        User createdUser = userProvider.create(user).blockingGet();

        assertUserMatch(user, createdUser);

        DefaultUser userToUpdate = (DefaultUser) createdUser;
        userToUpdate.setLastName("LUPDATE");
        userToUpdate.setFirstName("FUPDATE");
        userToUpdate.setEmail("update@acme.fr");
        userToUpdate.setCredentials("toto");
        userToUpdate.getAdditionalInformation().put("email", userToUpdate.getEmail());
        userToUpdate.getAdditionalInformation().put("given_name", userToUpdate.getFirstName());
        userToUpdate.getAdditionalInformation().put("family_name", userToUpdate.getLastName());

        final TestObserver<User> observer = userProvider.updatePassword(userToUpdate, "something").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(u -> assertUserMatch(user, u));
        observer.assertValue(u -> u.getCredentials().equals("something"));
    }

    private boolean assertUserMatch(DefaultUser expectedUser, User testableUser) {
        final String username = configuration.isUsernameCaseSensitive() ? expectedUser.getUsername()
            : expectedUser.getUsername().toLowerCase();
        assertEquals(username, testableUser.getUsername());
        assertEquals(expectedUser.getEmail(), testableUser.getEmail());
        assertEquals(expectedUser.getFirstName(), testableUser.getFirstName());
        assertEquals(expectedUser.getLastName(), testableUser.getLastName());
        assertNotNull(testableUser.getAdditionalInformation());
        assertTrue(testableUser.getAdditionalInformation().containsKey("key")
                && "value".equals(testableUser.getAdditionalInformation().get("key")));
        return true;
    }

    private DefaultUser createUserBean() {
       return createUserBean(RandomString.generate());
    }

    private DefaultUser createUserBean(String username) {
        DefaultUser user = new DefaultUser();
        user.setEmail(username+"@acme.com");
        user.setUsername(username);
        user.setFirstName("F-"+username);
        user.setLastName("L-"+username);
        user.setCredentials("T0pS3cret");
        user.setAdditionalInformation(new Maps.MapBuilder(new HashMap()).put("key", "value")
            .put("email", user.getEmail())
            .put("given_name", user.getFirstName())
            .put("family_name", user.getLastName()).build()
        );
        return user;
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
        user.setId("6");

        TestObserver testObserver = userProvider.updateUsername(user, "").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(IllegalArgumentException.class);
        testObserver.assertNoValues();
    }

    @Test
    public void must_not_updateUsername_user_not_found() {
        var user = new DefaultUser();
        user.setId("6");
        TestObserver testObserver = userProvider.updateUsername(user, "newusername").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(UserNotFoundException.class);
        testObserver.assertNoValues();
    }

    @Test
    public void must_updateUsername() {
        var user = new DefaultUser();
        user.setId(userProvider.findByUsername("changeme").blockingGet().getId());

        TestObserver testObserver = userProvider.updateUsername(user, "newusername").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();

        TestObserver<User> testObserver2 = userProvider.findByUsername("newusername").test();
        testObserver2.awaitDone(10, TimeUnit.SECONDS);
        testObserver2.assertComplete();
        testObserver2.assertNoErrors();
        testObserver2.assertValue(u -> "newusername".equals(u.getUsername()));
    }
}
