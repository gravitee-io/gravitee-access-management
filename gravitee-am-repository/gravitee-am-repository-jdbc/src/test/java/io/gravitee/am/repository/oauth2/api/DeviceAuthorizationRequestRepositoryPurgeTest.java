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

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.repository.jdbc.oauth2.api.JdbcDeviceAuthorizationRequestRepository;
import io.gravitee.am.repository.jdbc.oauth2.api.model.JdbcDeviceAuthorizationRequest;
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.springframework.data.relational.core.query.Criteria.where;

/**
 * @author GraviteeSource Team
 */
public class DeviceAuthorizationRequestRepositoryPurgeTest extends AbstractOAuthTest {

    @Autowired
    private JdbcDeviceAuthorizationRequestRepository repository;

    @Test
    public void shouldPurgeExpiredRequestsOnly() {
        Instant now = Instant.now();

        DeviceAuthorizationRequest live = buildRequest(RandomString.generate(), RandomString.generate());
        live.setCreatedAt(new Date(now.toEpochMilli()));
        live.setLastAccessAt(new Date(now.toEpochMilli()));
        live.setExpireAt(new Date(now.plus(1, ChronoUnit.MINUTES).toEpochMilli()));

        DeviceAuthorizationRequest expired = buildRequest(RandomString.generate(), RandomString.generate());
        expired.setCreatedAt(new Date(now.minus(2, ChronoUnit.MINUTES).toEpochMilli()));
        expired.setLastAccessAt(new Date(now.minus(2, ChronoUnit.MINUTES).toEpochMilli()));
        expired.setExpireAt(new Date(now.minus(1, ChronoUnit.MINUTES).toEpochMilli()));

        repository.create(live).test().awaitDone(10, TimeUnit.SECONDS);
        repository.create(expired).test().awaitDone(10, TimeUnit.SECONDS);

        assertNotNull(repository.findById(live.getId()).blockingGet());
        assertNull(repository.findById(expired.getId()).blockingGet());
        assertEquals(1L, countRows(expired.getId()));

        TestObserver<Void> testPurge = repository.purgeExpiredData().test();
        testPurge.awaitDone(10, TimeUnit.SECONDS);
        testPurge.assertNoErrors();

        assertNotNull(repository.findById(live.getId()).blockingGet());
        assertEquals(1L, countRows(live.getId()));
        assertEquals(0L, countRows(expired.getId()));
    }

    private long countRows(String id) {
        return repository.getTemplate()
                .count(Query.query(where("id").is(id)), JdbcDeviceAuthorizationRequest.class)
                .block();
    }

    private DeviceAuthorizationRequest buildRequest(String id, String userCode) {
        DeviceAuthorizationRequest request = new DeviceAuthorizationRequest();
        request.setId(id);
        request.setUserCode(userCode);
        request.setClientId("client");
        request.setStatus("PENDING");
        request.setSubject("subject");
        request.setScopes(Set.of("openid"));
        return request;
    }
}
