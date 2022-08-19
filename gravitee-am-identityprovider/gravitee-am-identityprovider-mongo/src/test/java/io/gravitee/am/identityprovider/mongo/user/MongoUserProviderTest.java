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
import io.gravitee.am.identityprovider.mongo.authentication.spring.MongoAuthenticationProviderConfiguration;
import io.gravitee.common.util.Maps;
import io.reactivex.observers.TestObserver;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.HashMap;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { MongoUserProviderTestConfiguration.class, MongoAuthenticationProviderConfiguration.class }, loader = AnnotationConfigContextLoader.class)
public class MongoUserProviderTest {

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
    public void shouldCreateUser() {
        DefaultUser user = createUserBean();
        TestObserver<User> testObserver = userProvider.create(user).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> assertUserMatch(user, u));
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
        observer.awaitTerminalEvent();
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
        observer.awaitTerminalEvent();
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
        observer.awaitTerminalEvent();
        observer.assertNoErrors();
        observer.assertValue(u -> assertUserMatch(user, u));
        observer.assertValue(u -> u.getCredentials().equals("something"));
    }

    private boolean assertUserMatch(DefaultUser expectedUser, User testableUser) {
        Assert.assertTrue(expectedUser.getUsername().equals(testableUser.getUsername()));
        Assert.assertTrue(expectedUser.getEmail().equals(testableUser.getEmail()));
        Assert.assertTrue(expectedUser.getFirstName().equals(testableUser.getFirstName()));
        Assert.assertTrue(expectedUser.getLastName().equals(testableUser.getLastName()));
        Assert.assertTrue(testableUser.getAdditionalInformation() != null);
        Assert.assertTrue(testableUser.getAdditionalInformation().containsKey("key")
                && "value".equals(testableUser.getAdditionalInformation().get("key")));
        return true;
    }

    private DefaultUser createUserBean() {
        final String username = RandomString.generate();
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
}
