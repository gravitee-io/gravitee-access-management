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

import io.gravitee.am.model.User;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.UserRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
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

}
