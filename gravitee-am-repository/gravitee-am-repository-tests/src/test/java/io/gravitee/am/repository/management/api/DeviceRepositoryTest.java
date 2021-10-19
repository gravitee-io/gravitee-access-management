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

import io.gravitee.am.model.Device;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.common.utils.UUID;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

import static java.util.Objects.nonNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceRepositoryTest extends AbstractManagementTest {

    @Autowired
    private DeviceRepository repository;

    @Test
    public void testFindByDomainAndApplicationAndUser() {
        Device device = buildDevice();
        Device createdDevice = repository.create(device).blockingGet();

        TestSubscriber<Device> testSubscriber = repository.findByDomainAndClientAndUser(
                createdDevice.getReferenceId(),
                createdDevice.getUserId()
        ).test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    private Device buildDevice() {
        Device rememberDevice = new Device();
        String random = UUID.random().toString();
        rememberDevice.setReferenceType(ReferenceType.DOMAIN);
        rememberDevice.setReferenceId("domain" + random);
        rememberDevice.setClient("client" + random);
        rememberDevice.setUserId("user" + random);
        rememberDevice.setDeviceIdentifierId("rememberDevice" + random);
        rememberDevice.setType("type" + random);
        rememberDevice.setDeviceId("deviceId" + random);
        rememberDevice.setCreatedAt(new Date());
        rememberDevice.setExpiresAt(new Date());
        return rememberDevice;
    }

    @Test
    public void testFindById() throws TechnicalException {
        Device rememberDevice = buildDevice();
        Device deviceCreated = repository.create(rememberDevice).blockingGet();

        TestObserver<Device> testObserver = repository.findById(deviceCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getId().equals(deviceCreated.getId()));
        testObserver.assertValue(bd -> bd.getReferenceType().equals(deviceCreated.getReferenceType()));
        testObserver.assertValue(bd -> bd.getReferenceId().equals(deviceCreated.getReferenceId()));
        testObserver.assertValue(bd -> bd.getClient().equals(deviceCreated.getClient()));
        testObserver.assertValue(bd -> bd.getDeviceIdentifierId().equals(deviceCreated.getDeviceIdentifierId()));
        testObserver.assertValue(bd -> bd.getDeviceId().equals(deviceCreated.getDeviceId()));
        testObserver.assertValue(bd -> bd.getType().equals(deviceCreated.getType()));
        testObserver.assertValue(bd -> bd.getUserId().equals(deviceCreated.getUserId()));
    }

    @Test
    public void testFindByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId() throws TechnicalException {
        Device rememberDevice = buildDevice();
        Device deviceCreated = repository.create(rememberDevice).blockingGet();

        TestObserver<Device> testObserver = repository.findByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId(
                deviceCreated.getReferenceId(),
                deviceCreated.getClient(),
                deviceCreated.getUserId(),
                deviceCreated.getDeviceIdentifierId(),
                deviceCreated.getDeviceId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getId().equals(deviceCreated.getId()));
        testObserver.assertValue(bd -> bd.getReferenceType().equals(deviceCreated.getReferenceType()));
        testObserver.assertValue(bd -> bd.getReferenceId().equals(deviceCreated.getReferenceId()));
        testObserver.assertValue(bd -> bd.getClient().equals(deviceCreated.getClient()));
        testObserver.assertValue(bd -> bd.getDeviceIdentifierId().equals(deviceCreated.getDeviceIdentifierId()));
        testObserver.assertValue(bd -> bd.getDeviceId().equals(deviceCreated.getDeviceId()));
        testObserver.assertValue(bd -> bd.getType().equals(deviceCreated.getType()));
        testObserver.assertValue(bd -> bd.getUserId().equals(deviceCreated.getUserId()));
    }

    @Test
    public void testNotFindByDeviceId() throws TechnicalException {
        Device rememberDevice = buildDevice();
        Device deviceCreated = repository.create(rememberDevice).blockingGet();
        repository.findByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId(
                deviceCreated.getReferenceId(),
                "unknown_client",
                deviceCreated.getUserId(),
                deviceCreated.getDeviceIdentifierId(),
                deviceCreated.getDeviceId()).test().assertEmpty();
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        repository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Device device = buildDevice();

        TestObserver<Device> testObserver = repository.create(device).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> nonNull(bd.getId()));
        testObserver.assertValue(bd -> bd.getReferenceType().equals(device.getReferenceType()));
        testObserver.assertValue(bd -> bd.getReferenceId().equals(device.getReferenceId()));
        testObserver.assertValue(bd -> bd.getClient().equals(device.getClient()));
        testObserver.assertValue(bd -> bd.getType().equals(device.getType()));
        testObserver.assertValue(bd -> bd.getDeviceIdentifierId().equals(device.getDeviceIdentifierId()));
        testObserver.assertValue(bd -> bd.getDeviceId().equals(device.getDeviceId()));
        testObserver.assertValue(bd -> bd.getUserId().equals(device.getUserId()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        Device createdDevice = buildDevice();
        Device botDetectionCreated = repository.create(createdDevice).blockingGet();

        Device device = buildDevice();
        device.setId(botDetectionCreated.getId());
        device.setExpiresAt(new Date());

        TestObserver<Device> testObserver = repository.update(device).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getId().equals(device.getId()));
        testObserver.assertValue(bd -> bd.getReferenceType().equals(device.getReferenceType()));
        testObserver.assertValue(bd -> bd.getReferenceId().equals(device.getReferenceId()));
        testObserver.assertValue(bd -> bd.getClient().equals(device.getClient()));
        testObserver.assertValue(bd -> bd.getType().equals(device.getType()));
        testObserver.assertValue(bd -> bd.getDeviceIdentifierId().equals(device.getDeviceIdentifierId()));
        testObserver.assertValue(bd -> bd.getDeviceId().equals(device.getDeviceId()));
        testObserver.assertValue(bd -> bd.getUserId().equals(device.getUserId()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        Device botDetection = buildDevice();
        Device rememberDeviceCreated = repository.create(botDetection).blockingGet();

        TestObserver<Device> testObserver = repository.findById(rememberDeviceCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getId().equals(rememberDeviceCreated.getId()));

        TestObserver testObserver1 = repository.delete(rememberDeviceCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        repository.findById(rememberDeviceCreated.getId()).test().assertEmpty();
    }
}
