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

import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.common.utils.UUID;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceIdentifierRepositoryTest extends AbstractManagementTest {

    @Autowired
    private DeviceIdentifierRepository repository;

    @Test
    public void testFindByDomain() {
        DeviceIdentifier botDetection = buildDeviceIdentifier();
        botDetection.setReferenceId("testDomain");
        botDetection.setReferenceType(ReferenceType.DOMAIN);
        repository.create(botDetection).blockingGet();

        TestSubscriber<DeviceIdentifier> testSubscriber = repository.findByReference(ReferenceType.DOMAIN, "testDomain").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    private DeviceIdentifier buildDeviceIdentifier() {
        DeviceIdentifier deviceIdentifier = new DeviceIdentifier();
        String random = UUID.random().toString();
        deviceIdentifier.setName("name" + random);
        deviceIdentifier.setReferenceId("domain" + random);
        deviceIdentifier.setReferenceType(ReferenceType.DOMAIN);
        deviceIdentifier.setConfiguration("{\"config\": \"" + random + "\"}");
        deviceIdentifier.setType("type" + random);
        deviceIdentifier.setCreatedAt(new Date());
        deviceIdentifier.setUpdatedAt(new Date());
        return deviceIdentifier;
    }

    @Test
    public void testFindById() {
        DeviceIdentifier deviceIdentifier = buildDeviceIdentifier();
        DeviceIdentifier deviceIdentifierCreated = repository.create(deviceIdentifier).blockingGet();

        TestObserver<DeviceIdentifier> testObserver = repository.findById(deviceIdentifierCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getId().equals(deviceIdentifierCreated.getId()));
        testObserver.assertValue(bd -> bd.getName().equals(deviceIdentifierCreated.getName()));
        testObserver.assertValue(bd -> bd.getConfiguration().equals(deviceIdentifierCreated.getConfiguration()));
        testObserver.assertValue(bd -> bd.getReferenceId().equals(deviceIdentifierCreated.getReferenceId()));
        testObserver.assertValue(bd -> bd.getType().equals(deviceIdentifierCreated.getType()));
    }

    @Test
    public void testNotFoundById() {
        var observer = repository.findById("test").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void testCreate() {
        DeviceIdentifier bDetection = buildDeviceIdentifier();

        TestObserver<DeviceIdentifier> testObserver = repository.create(bDetection).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getId() != null);
        testObserver.assertValue(bd -> bd.getName().equals(bDetection.getName()));
        testObserver.assertValue(bd -> bd.getConfiguration().equals(bDetection.getConfiguration()));
        testObserver.assertValue(bd -> bd.getReferenceId().equals(bDetection.getReferenceId()));
        testObserver.assertValue(bd -> bd.getType().equals(bDetection.getType()));
    }

    @Test
    public void testUpdate() {
        DeviceIdentifier botDetection = buildDeviceIdentifier();
        DeviceIdentifier botDetectionCreated = repository.create(botDetection).blockingGet();

        DeviceIdentifier bDetection = buildDeviceIdentifier();
        bDetection.setId(botDetectionCreated.getId());
        bDetection.setName("testUpdatedName");

        TestObserver<DeviceIdentifier> testObserver = repository.update(bDetection).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getId().equals(botDetectionCreated.getId()));
        testObserver.assertValue(bd -> bd.getName().equals(bDetection.getName()));
        testObserver.assertValue(bd -> bd.getConfiguration().equals(bDetection.getConfiguration()));
        testObserver.assertValue(bd -> bd.getReferenceId().equals(bDetection.getReferenceId()));
        testObserver.assertValue(bd -> bd.getType().equals(bDetection.getType()));
    }

    @Test
    public void testDelete() {
        DeviceIdentifier botDetection = buildDeviceIdentifier();
        DeviceIdentifier deviceIdentifierCreated = repository.create(botDetection).blockingGet();

        TestObserver<DeviceIdentifier> testObserver = repository.findById(deviceIdentifierCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getName().equals(deviceIdentifierCreated.getName()));

        TestObserver testObserver1 = repository.delete(deviceIdentifierCreated.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        testObserver = repository.findById(deviceIdentifierCreated.getId()).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoValues();
        testObserver.assertNoErrors();
    }
}
