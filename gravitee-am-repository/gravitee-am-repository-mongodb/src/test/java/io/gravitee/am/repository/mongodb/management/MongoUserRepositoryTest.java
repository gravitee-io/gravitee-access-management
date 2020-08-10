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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.UserRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoUserRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Override
    public String collectionName() {
        return "users";
    }

    @Test
    public void testFindByDomain() throws TechnicalException {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        user.setDomain("testDomain");
        userRepository.create(user).blockingGet();

        // fetch users
        TestObserver<Set<User>> testObserver = userRepository.findByDomain("testDomain").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findById(userCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals("testsUsername"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        userRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        User user = new User();
        user.setUsername("testsUsername");
        user.setAdditionalInformation(Collections.singletonMap("email", "johndoe@test.com"));
        TestObserver<User> testObserver = userRepository.create(user).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()) && u.getAdditionalInformation().containsKey("email"));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        User userCreated = userRepository.create(user).blockingGet();

        // update user
        User updatedUser = new User();
        updatedUser.setId(userCreated.getId());
        updatedUser.setUsername("testUpdatedUsername");

        TestObserver<User> testObserver = userRepository.update(updatedUser).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(updatedUser.getUsername()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create user
        User user = new User();
        user.setUsername("testsUsername");
        User userCreated = userRepository.create(user).blockingGet();

        // fetch user
        TestObserver<User> testObserver = userRepository.findById(userCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> u.getUsername().equals(user.getUsername()));

        // delete user
        TestObserver testObserver1 = userRepository.delete(userCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch user
        userRepository.findById(userCreated.getId()).test().assertEmpty();
    }

    @Test
    public void testSearch_strict() {
        final String domain = "domain";
        // create user
        User user = new User();
        user.setDomain(domain);
        user.setUsername("testUsername");
        userRepository.create(user).blockingGet();

        User user2 = new User();
        user2.setDomain(domain);
        user2.setUsername("testUsername2");
        userRepository.create(user2).blockingGet();

        // fetch user
        TestObserver<Page<User>> testObserver = userRepository.search(domain, "testUsername", 0, 10).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.getData().size() == 1);
        testObserver.assertValue(users -> users.getData().iterator().next().getUsername().equals(user.getUsername()));

    }

    @Test
    public void testSearch_wildcard() {
        final String domain = "domain";
        // create user
        User user = new User();
        user.setDomain(domain);
        user.setUsername("testUsername");
        userRepository.create(user).blockingGet();

        User user2 = new User();
        user2.setDomain(domain);
        user2.setUsername("testUsername2");
        userRepository.create(user2).blockingGet();

        // fetch user
        TestObserver<Page<User>> testObserver = userRepository.search(domain, "testUsername*", 0, 10).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.getData().size() == 2);
    }

    @Test
    public void testSearch_paged() {
        final String domain = "domain";
        // create user
        User user1 = new User();
        user1.setDomain(domain);
        user1.setUsername("testUsername1");
        userRepository.create(user1).blockingGet();

        User user2 = new User();
        user2.setDomain(domain);
        user2.setUsername("testUsername2");
        userRepository.create(user2).blockingGet();

        User user3 = new User();
        user3.setDomain(domain);
        user3.setUsername("testUsername3");
        userRepository.create(user3).blockingGet();

        // fetch user (page 0)
        TestObserver<Page<User>> testObserverP0 = userRepository.search(domain, "testUsername*", 0, 2).test();
        testObserverP0.awaitTerminalEvent();

        testObserverP0.assertComplete();
        testObserverP0.assertNoErrors();
        testObserverP0.assertValue(users -> users.getData().size() == 2);
        testObserverP0.assertValue(users -> {
        	Iterator<User> it = users.getData().iterator();
        	return it.next().getUsername().equals(user1.getUsername()) && it.next().getUsername().equals(user2.getUsername());
        });

        // fetch user (page 1)
        TestObserver<Page<User>> testObserverP1 = userRepository.search(domain, "testUsername*", 1, 2).test();
        testObserverP1.awaitTerminalEvent();

        testObserverP1.assertComplete();
        testObserverP1.assertNoErrors();
        testObserverP1.assertValue(users -> users.getData().size() == 1);
        testObserverP1.assertValue(users -> users.getData().iterator().next().getUsername().equals(user3.getUsername()));
    }

    @Test
    public void testFindByDomainAndEmail() throws TechnicalException {
        final String domain = "domain";
        // create user
        User user = new User();
        user.setDomain(domain);
        user.setEmail("test@test.com");
        userRepository.create(user).blockingGet();

        User user2 = new User();
        user2.setDomain(domain);
        userRepository.create(user2).blockingGet();

        // fetch user
        TestObserver<List<User>> testObserver = userRepository.findByDomainAndEmail(domain, "test@test.com", false).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.size() == 1);
    }

    @Test
    public void testFindByDomainAndEmailWithStandardClaim() throws TechnicalException {
        final String domain = "domain";
        // create user
        User user = new User();
        user.setDomain(domain);
        user.setEmail("test@test.com");
        userRepository.create(user).blockingGet();

        User user2 = new User();
        user2.setDomain(domain);
        user2.setAdditionalInformation(Collections.singletonMap(StandardClaims.EMAIL, "test@test.com"));
        userRepository.create(user2).blockingGet();

        // fetch user
        TestObserver<List<User>> testObserver = userRepository.findByDomainAndEmail(domain, "test@test.com", false).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(users -> users.size() == 2);
    }

}
