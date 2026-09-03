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
import io.gravitee.am.repository.oauth2.AbstractOAuthTest;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class DeviceAuthorizationRequestRepositoryTest extends AbstractOAuthTest {

    private static final int RETENTION_SECONDS = 900;

    @Autowired
    private DeviceAuthorizationRequestRepository repository;

    @Test
    public void shouldNotFindByUnknownDeviceCode() {
        TestObserver<DeviceAuthorizationRequest> observer = repository.findById("unknown-device-code").test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldNotFindByUnknownUserCode() {
        TestObserver<DeviceAuthorizationRequest> observer = repository.findByUserCode("UNKNOWN").test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldFindByDeviceCode() {
        DeviceAuthorizationRequest request = buildRequest();
        repository.create(request).test().awaitDone(10, TimeUnit.SECONDS);

        TestObserver<DeviceAuthorizationRequest> observer = repository.findById(request.getId()).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(found -> found.getId().equals(request.getId())
                && found.getUserCode().equals(request.getUserCode())
                && found.getClientId().equals("client-id")
                && found.getSubject().equals("subject-id")
                && found.getStatus().equals("PENDING")
                && found.getScopes().contains("openid"));
    }

    @Test
    public void shouldFindByUserCode() {
        DeviceAuthorizationRequest request = buildRequest();
        repository.create(request).test().awaitDone(10, TimeUnit.SECONDS);

        TestObserver<DeviceAuthorizationRequest> observer = repository.findByUserCode(request.getUserCode()).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(found -> found.getId().equals(request.getId()));
    }

    @Test
    public void shouldRejectDuplicateUserCode() {
        DeviceAuthorizationRequest first = buildRequest();
        repository.create(first).test().awaitDone(10, TimeUnit.SECONDS).assertNoErrors();

        DeviceAuthorizationRequest duplicate = buildRequest();
        duplicate.setUserCode(first.getUserCode());

        TestObserver<DeviceAuthorizationRequest> observer = repository.create(duplicate).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(error -> error.getClass().getSimpleName().contains("Duplicate")
                || String.valueOf(error.getMessage()).toLowerCase().contains("duplicate key"));

        repository.findById(duplicate.getId()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertNoValues();
        repository.findByUserCode(first.getUserCode()).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(found -> found.getId().equals(first.getId()));
    }

    @Test
    public void shouldUpdate() {
        DeviceAuthorizationRequest request = buildRequest();
        repository.create(request).test().awaitDone(10, TimeUnit.SECONDS);

        request.setStatus("APPROVED");
        request.setSubject("approver-id");
        request.setIntervalIncrement(10);
        repository.update(request).test().awaitDone(10, TimeUnit.SECONDS).assertNoErrors();

        TestObserver<DeviceAuthorizationRequest> observer = repository.findById(request.getId()).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertValue(found -> found.getStatus().equals("APPROVED")
                && found.getSubject().equals("approver-id")
                && found.getIntervalIncrement() == 10);
    }

    @Test
    public void shouldUpdateStatus() {
        DeviceAuthorizationRequest request = buildRequest();
        repository.create(request).test().awaitDone(10, TimeUnit.SECONDS);

        repository.updateStatus(request.getId(), "DENIED").test().awaitDone(10, TimeUnit.SECONDS).assertNoErrors();

        TestObserver<DeviceAuthorizationRequest> observer = repository.findById(request.getId()).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertValue(found -> found.getStatus().equals("DENIED"));
    }

    @Test
    public void shouldDelete() {
        DeviceAuthorizationRequest request = buildRequest();

        TestObserver<DeviceAuthorizationRequest> observer = repository
                .create(request)
                .ignoreElement()
                .andThen(repository.delete(request.getId()))
                .andThen(repository.findById(request.getId()))
                .test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldFreeUserCodeAfterDelete() {
        DeviceAuthorizationRequest first = buildRequest();
        repository.create(first).test().awaitDone(10, TimeUnit.SECONDS);
        repository.delete(first.getId()).test().awaitDone(10, TimeUnit.SECONDS);

        DeviceAuthorizationRequest reused = buildRequest();
        reused.setUserCode(first.getUserCode());

        TestObserver<DeviceAuthorizationRequest> observer = repository.create(reused).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
    }

    @Test
    public void shouldStillFindRequestPastExpiryInsideRetentionWindow() {
        Instant now = Instant.now();
        Instant deviceCodeExpiry = now.minus(1, ChronoUnit.MINUTES);

        DeviceAuthorizationRequest request = buildRequest();
        request.setCreatedAt(new Date(deviceCodeExpiry.minusSeconds(600).toEpochMilli()));
        request.setLastAccessAt(new Date(now.minus(1, ChronoUnit.MINUTES).toEpochMilli()));
        request.setExpireAt(new Date(deviceCodeExpiry.plusSeconds(RETENTION_SECONDS).toEpochMilli()));

        repository.create(request).test().awaitDone(10, TimeUnit.SECONDS);

        assertTrue("fixture is not past the device code expiry", deviceCodeExpiry.isBefore(Instant.now()));

        TestObserver<DeviceAuthorizationRequest> observer = repository.findById(request.getId()).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertValueCount(1);
        observer.assertValue(found -> found.getId().equals(request.getId()));
    }

    @Test
    public void shouldNotFindRequestPastRetentionWindow() {
        Instant now = Instant.now();
        DeviceAuthorizationRequest request = buildRequest();
        request.setCreatedAt(new Date(now.minus(30, ChronoUnit.MINUTES).toEpochMilli()));
        request.setLastAccessAt(new Date(now.minus(30, ChronoUnit.MINUTES).toEpochMilli()));
        request.setExpireAt(new Date(now.minus(1, ChronoUnit.MINUTES).toEpochMilli()));

        repository.create(request).test().awaitDone(10, TimeUnit.SECONDS);

        TestObserver<DeviceAuthorizationRequest> observer = repository.findById(request.getId()).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldNotFindByUserCodePastRetentionWindow() {
        Instant now = Instant.now();
        DeviceAuthorizationRequest request = buildRequest();
        request.setExpireAt(new Date(now.minus(1, ChronoUnit.MINUTES).toEpochMilli()));

        repository.create(request).test().awaitDone(10, TimeUnit.SECONDS);

        TestObserver<DeviceAuthorizationRequest> observer = repository.findByUserCode(request.getUserCode()).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldPersistIntervalIncrement() {
        DeviceAuthorizationRequest request = buildRequest();
        request.setIntervalIncrement(15);

        repository.create(request).test().awaitDone(10, TimeUnit.SECONDS);

        DeviceAuthorizationRequest found = repository.findById(request.getId()).blockingGet();
        assertEquals(15, found.getIntervalIncrement());
    }

    private DeviceAuthorizationRequest buildRequest() {
        Instant now = Instant.now();
        DeviceAuthorizationRequest request = new DeviceAuthorizationRequest();
        request.setId(RandomString.generate());
        request.setUserCode(RandomString.generate());
        request.setStatus("PENDING");
        request.setClientId("client-id");
        request.setSubject("subject-id");
        request.setScopes(Set.of("openid", "profile"));
        request.setCreatedAt(new Date(now.toEpochMilli()));
        request.setLastAccessAt(new Date(now.toEpochMilli()));
        request.setExpireAt(new Date(now.plusSeconds(600 + RETENTION_SECONDS).toEpochMilli()));
        return request;
    }
}
