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

import io.gravitee.am.repository.jdbc.oauth2.oidc.JdbcRequestObjectRepository;
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.gravitee.am.repository.oidc.model.RequestObject;
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
public class RequestObjectRepositoryPurgeTest extends AbstractOAuthTest {
    @Autowired
    private JdbcRequestObjectRepository requestObjectRepository;

    @Test
    public void shouldPurge() {
        Instant now = Instant.now();
        RequestObject object1 = new RequestObject();
        object1.setDomain("domain");
        object1.setClient("client");
        object1.setExpireAt(new Date(now.plus(1, ChronoUnit.MINUTES).toEpochMilli()));

        RequestObject object2 = new RequestObject();
        object2.setDomain("domain");
        object2.setClient("client");
        object2.setExpireAt(new Date(now.minus(1, ChronoUnit.MINUTES).toEpochMilli()));

        requestObjectRepository.create(object1).test().awaitTerminalEvent();
        requestObjectRepository.create(object2).test().awaitTerminalEvent();

        assertNotNull(requestObjectRepository.findById(object1.getId()).blockingGet());
        assertNull(requestObjectRepository.findById(object2.getId()).blockingGet());

        TestObserver<Void> testPurge = requestObjectRepository.purgeExpiredData().test();
        testPurge.awaitTerminalEvent();
        testPurge.assertNoErrors();

        assertNotNull(requestObjectRepository.findById(object1.getId()).blockingGet());
        assertNull(requestObjectRepository.findById(object2.getId()).blockingGet());

    }

}
