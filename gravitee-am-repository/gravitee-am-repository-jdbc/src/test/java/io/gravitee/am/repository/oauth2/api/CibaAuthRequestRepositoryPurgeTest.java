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

import io.gravitee.am.repository.jdbc.oauth2.oidc.JdbcCibaAuthReqRepository;
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CibaAuthRequestRepositoryPurgeTest extends AbstractOAuthTest {

    @Autowired
    private JdbcCibaAuthReqRepository cibaRepository;

    @Test
    public void shouldPurge() {
        Instant now = Instant.now();
        CibaAuthRequest object1 = new CibaAuthRequest();
        object1.setClientId("client");
        object1.setStatus("ONGOING");
        object1.setSubject("subject");
        object1.setUserCode("usercode");
        object1.setScopes(Set.of("openid"));
        object1.setCreatedAt(new Date(now.toEpochMilli()));
        object1.setLastAccessAt(new Date(now.toEpochMilli()));
        object1.setExpireAt(new Date(now.plus(1, ChronoUnit.MINUTES).toEpochMilli()));

        CibaAuthRequest object2 = new CibaAuthRequest();
        object2.setClientId("client");
        object2.setStatus("ONGOING");
        object2.setSubject("subject");
        object2.setUserCode("usercode");
        object2.setScopes(Set.of("openid"));
        object2.setCreatedAt(new Date(now.minus(2, ChronoUnit.MINUTES).toEpochMilli()));
        object2.setLastAccessAt(new Date(now.minus(2, ChronoUnit.MINUTES).toEpochMilli()));
        object2.setExpireAt(new Date(now.minus(1, ChronoUnit.MINUTES).toEpochMilli()));

        cibaRepository.create(object1).test().awaitTerminalEvent();
        cibaRepository.create(object2).test().awaitTerminalEvent();

        assertNotNull(cibaRepository.findById(object1.getId()).blockingGet());
        assertNull(cibaRepository.findById(object2.getId()).blockingGet());

        TestObserver<Void> testPurge = cibaRepository.purgeExpiredData().test();
        testPurge.awaitTerminalEvent();
        testPurge.assertNoErrors();

        assertNotNull(cibaRepository.findById(object1.getId()).blockingGet());
        assertNull(cibaRepository.findById(object2.getId()).blockingGet());

    }

}
