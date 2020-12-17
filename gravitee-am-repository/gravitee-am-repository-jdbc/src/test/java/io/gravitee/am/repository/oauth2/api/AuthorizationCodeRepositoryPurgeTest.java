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
package io.gravitee.am.repository.oauth2.api;

import io.gravitee.am.repository.jdbc.oauth2.api.JdbcAuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationCodeRepositoryPurgeTest extends AbstractOAuthTest {

    @Autowired
    private JdbcAuthorizationCodeRepository authorizationCodeRepository;

    @Test
    public void shouldRemoveCode() {
        Instant now = Instant.now();
        String code = "testCode";
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setId(code);
        authorizationCode.setCode(code);
        authorizationCode.setExpireAt(new Date(now.plus(1, ChronoUnit.MINUTES).toEpochMilli()));
        String codeExpired = "testCodeExpired";
        AuthorizationCode authorizationCodeExpired = new AuthorizationCode();
        authorizationCodeExpired.setId(codeExpired);
        authorizationCodeExpired.setCode(codeExpired);
        authorizationCodeExpired.setExpireAt(new Date(now.minus(1, ChronoUnit.MINUTES).toEpochMilli()));

        TestObserver<AuthorizationCode> testObserver = authorizationCodeRepository.create(authorizationCode).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver = authorizationCodeRepository.create(authorizationCodeExpired).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();

        assertNotNull(authorizationCodeRepository.findByCode(code).blockingGet());
        assertNull(authorizationCodeRepository.findByCode(codeExpired).blockingGet());

        TestObserver<Void> testPurge = authorizationCodeRepository.purgeExpiredData().test();
        testPurge.awaitTerminalEvent();
        testPurge.assertNoErrors();

        assertNotNull(authorizationCodeRepository.findByCode(code).blockingGet());
        assertNull(authorizationCodeRepository.findByCode(codeExpired).blockingGet());

    }

}
