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

import io.gravitee.am.repository.jdbc.oauth2.api.JdbcRefreshTokenRepository;
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
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
public class RefreshTokenRepositoryPurgeTest extends AbstractOAuthTest {
    @Autowired
    private JdbcRefreshTokenRepository refreshTokenRepository;

    @Test
    public void shouldPurge() {
        Instant now = Instant.now();
        RefreshToken token1 = new RefreshToken();
        token1.setId("my-token");
        token1.setToken("my-token");
        token1.setClient("client-id");
        token1.setDomain("domain-id");
        token1.setSubject("user-id");
        token1.setExpireAt(new Date(now.plus(1, ChronoUnit.MINUTES).toEpochMilli()));
        RefreshToken token2 = new RefreshToken();
        token2.setId("my-token2");
        token2.setToken("my-token2");
        token2.setClient("client-id2");
        token2.setDomain("domain-id2");
        token2.setSubject("user-id2");
        token2.setExpireAt(new Date(now.minus(1, ChronoUnit.MINUTES).toEpochMilli()));

        TestObserver<Void> testObserver = refreshTokenRepository.create(token1).ignoreElement()
                .andThen(refreshTokenRepository.create(token2).ignoreElement())
                .test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();

        assertNotNull(refreshTokenRepository.findByToken("my-token").blockingGet());
        assertNull(refreshTokenRepository.findByToken("my-token2").blockingGet());

        TestObserver<Void> testPurge = refreshTokenRepository.purgeExpiredData().test();
        testPurge.awaitTerminalEvent();
        testPurge.assertNoErrors();

        assertNotNull(refreshTokenRepository.findByToken("my-token").blockingGet());
        assertNull(refreshTokenRepository.findByToken("my-token2").blockingGet());

    }

}
