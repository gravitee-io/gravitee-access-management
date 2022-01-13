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

    @Test
    public void testNotFindByDomainAndApplicationAndUser_expired() {
        Device device = buildDevice(new Date(System.currentTimeMillis() - 10000));
        Device createdDevice = repository.create(device).blockingGet();

       repository.findById(createdDevice.getUserId()).test().assertEmpty();
    }

    private Device buildDevice() {
        return buildDevice(new Date(System.currentTimeMillis() + 10000));
    }

    private Device buildDevice(Date expiresAt) {
        Device device = new Device();
        String random = UUID.random().toString();
        device.setReferenceType(ReferenceType.DOMAIN);
        device.setReferenceId("domain" + random);
        device.setClient("client" + random);
        device.setUserId("user" + random);
        device.setDeviceIdentifierId("device" + random);
        device.setType("type" + random);
        device.setDeviceId("deviceId" + random);
        device.setCreatedAt(new Date());
        device.setExpiresAt(expiresAt);
        return device;
    }

    @Test
    public void testFindById() throws TechnicalException {
        Device device = buildDevice();
        Device deviceCreated = repository.create(device).blockingGet();

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
    public void testNotFindById_expired() {
        Device device = buildDevice(new Date(System.currentTimeMillis() - 10000));
        Device deviceCreated = repository.create(device).blockingGet();

        TestObserver<Device> testObserver = repository.findById(deviceCreated.getId()).test();
        testObserver.assertEmpty();
    }

    @Test
    public void testFindByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId() {
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
        testObserver.assertValue(device1 -> device1.getId().equals(deviceCreated.getId()));
        testObserver.assertValue(device1 -> device1.getReferenceType().equals(deviceCreated.getReferenceType()));
        testObserver.assertValue(device1 -> device1.getReferenceId().equals(deviceCreated.getReferenceId()));
        testObserver.assertValue(device1 -> device1.getClient().equals(deviceCreated.getClient()));
        testObserver.assertValue(device1 -> device1.getDeviceIdentifierId().equals(deviceCreated.getDeviceIdentifierId()));
        testObserver.assertValue(device1 -> device1.getDeviceId().equals(deviceCreated.getDeviceId()));
        testObserver.assertValue(device1 -> device1.getType().equals(deviceCreated.getType()));
        testObserver.assertValue(device1 -> device1.getUserId().equals(deviceCreated.getUserId()));
    }

    @Test
    public void testNotFindByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId_unknown_client() {
        Device device = buildDevice();
        Device deviceCreated = repository.create(device).blockingGet();
        final TestObserver<Device> test = repository.findByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId(
                deviceCreated.getReferenceId(),
                "unknown_client",
                deviceCreated.getUserId(),
                deviceCreated.getDeviceIdentifierId(),
                deviceCreated.getDeviceId()).test().assertEmpty();
    }

    @Test
    public void testNotFindByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId_expired() {
        Device device = buildDevice(new Date(System.currentTimeMillis() - 10000));
        Device deviceCreated = repository.create(device).blockingGet();

        final TestObserver<Device> test = repository.findByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId(
                deviceCreated.getReferenceId(),
                deviceCreated.getClient(),
                deviceCreated.getUserId(),
                deviceCreated.getDeviceIdentifierId(),
                deviceCreated.getDeviceId()).test().assertEmpty();
    }

    @Test
    public void testNotFoundById_unknown_id() {
        repository.findById("unknown_id").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Device device = buildDevice();

        TestObserver<Device> testObserver = repository.create(device).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(device1 -> nonNull(device1.getId()));
        testObserver.assertValue(device1 -> device1.getReferenceType().equals(device.getReferenceType()));
        testObserver.assertValue(device1 -> device1.getReferenceId().equals(device.getReferenceId()));
        testObserver.assertValue(device1 -> device1.getClient().equals(device.getClient()));
        testObserver.assertValue(device1 -> device1.getType().equals(device.getType()));
        testObserver.assertValue(device1 -> device1.getDeviceIdentifierId().equals(device.getDeviceIdentifierId()));
        testObserver.assertValue(device1 -> device1.getDeviceId().equals(device.getDeviceId()));
        testObserver.assertValue(device1 -> device1.getUserId().equals(device.getUserId()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        Device createdDevice = buildDevice();
        Device deviceCreated = repository.create(createdDevice).blockingGet();

        Device device = buildDevice();
        device.setId(deviceCreated.getId());
        device.setExpiresAt(new Date());

        TestObserver<Device> testObserver = repository.update(device).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(device1 -> device1.getId().equals(device.getId()));
        testObserver.assertValue(device1 -> device1.getReferenceType().equals(device.getReferenceType()));
        testObserver.assertValue(device1 -> device1.getReferenceId().equals(device.getReferenceId()));
        testObserver.assertValue(device1 -> device1.getClient().equals(device.getClient()));
        testObserver.assertValue(device1 -> device1.getType().equals(device.getType()));
        testObserver.assertValue(device1 -> device1.getDeviceIdentifierId().equals(device.getDeviceIdentifierId()));
        testObserver.assertValue(device1 -> device1.getDeviceId().equals(device.getDeviceId()));
        testObserver.assertValue(device1 -> device1.getUserId().equals(device.getUserId()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        Device device = buildDevice();
        Device deviceCreated = repository.create(device).blockingGet();

        TestObserver<Device> testObserver = repository.findById(deviceCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(bd -> bd.getId().equals(deviceCreated.getId()));

        TestObserver testObserver1 = repository.delete(deviceCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        repository.findById(deviceCreated.getId()).test().assertEmpty();
    }
}
