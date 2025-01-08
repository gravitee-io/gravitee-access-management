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
package io.gravitee.am.dataplane.api.repository;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@TestPropertySource(properties = {"resilience.enabled = true"})
public class UserRepositoryInResilienceModeTest extends UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testUpsert_create_if_missing() {
        User user = new User();
        user.setId("123");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domainId");
        user.setUsername("testsUsername");
        user.setAdditionalInformation(Collections.singletonMap("email", "johndoe@test.com"));
        TestObserver<User> testObserver = userRepository.update(user).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> {

            userRepository.findById(u.getId())
                    .test()
                    .awaitDone(10, TimeUnit.SECONDS)
                    .assertComplete()
                    .assertNoErrors()
                    .assertValueCount(1);

            return true;
        });
    }

    @Test
    public void testUpsert_update_if_present() {
        User user = new User();
        user.setId("123");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domainId");
        user.setUsername("testsUsername");
        TestObserver<User> testObserver = userRepository
                .update(user)
                .test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        User userUpdate = new User(user);
        userUpdate.setUsername("username");

        testObserver = userRepository.update(userUpdate).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        testObserver.assertValue(u -> {
            userRepository.findById(u.getId())
                    .test()
                    .awaitDone(10, TimeUnit.SECONDS)
                    .assertComplete()
                    .assertNoErrors()
                    .assertValue(u1 -> u1.getUsername().equals("username"));

            return true;
        });
    }

}
