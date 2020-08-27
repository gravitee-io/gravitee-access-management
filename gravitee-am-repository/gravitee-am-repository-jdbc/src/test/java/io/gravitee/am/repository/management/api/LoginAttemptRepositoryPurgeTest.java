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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.LoginAttempt;
import io.gravitee.am.repository.jdbc.management.api.JdbcLoginAttemptRepository;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginAttemptRepositoryPurgeTest extends AbstractManagementTest {

    @Autowired
    protected JdbcLoginAttemptRepository repository;

    @Test
    public void shouldPurgeExpiredData() {
        Instant now = Instant.now();
        LoginAttempt attemptExpired = buildLoginAttempt(now.minus(10, ChronoUnit.MINUTES));
        LoginAttempt attemptExpired2 = buildLoginAttempt(now.minus(10, ChronoUnit.MILLIS));

        LoginAttempt attemptNotExpired = buildLoginAttempt(now.plus(1, ChronoUnit.MINUTES));

        TestObserver<LoginAttempt> testObserver = repository.create(attemptExpired).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver = repository.create(attemptExpired2).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver = repository.create(attemptNotExpired).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();

        assertNull(repository.findById(attemptExpired.getId()).blockingGet());
        assertNull(repository.findById(attemptExpired2.getId()).blockingGet());
        assertNotNull(repository.findById(attemptNotExpired.getId()).blockingGet());

        TestObserver<Void> test = repository.purgeExpiredData().test();
        test.awaitTerminalEvent();
        test.assertNoErrors();

        assertNotNull(repository.findById(attemptNotExpired.getId()).blockingGet());
    }

    private LoginAttempt buildLoginAttempt(Instant expiredAt) {
        LoginAttempt attempt = new LoginAttempt();
        String random = UUID.randomUUID().toString();
        attempt.setAttempts(1);
        attempt.setClient("client"+random);
        attempt.setDomain("domain"+random);
        attempt.setIdentityProvider("idp"+random);
        attempt.setUsername("user"+random);
        Date createdAt = new Date();
        attempt.setCreatedAt(createdAt);
        attempt.setUpdatedAt(createdAt);
        attempt.setExpireAt(new Date(expiredAt.toEpochMilli()));
        return attempt;
    }
}
