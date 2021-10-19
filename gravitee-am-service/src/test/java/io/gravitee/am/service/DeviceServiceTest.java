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
package io.gravitee.am.service;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Device;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.DeviceRepository;
import io.gravitee.am.service.exception.DeviceNotFoundException;
import io.gravitee.am.service.impl.DeviceServiceImpl;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DeviceServiceTest {

    @InjectMocks
    private DeviceService deviceService = new DeviceServiceImpl();

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private AuditService auditService;

    private final static String DOMAIN = "domain1";
    private final static String DOMAIN2 = "domain2";
    private final static String CLIENT = "client1";
    private final static String USER = "user1";
    private final static String DEVICE_IDENTIFIER = "rememberDevice1";
    private final static String REMEMBER_DEVICE2 = "rememberDevice2";
    private final static String TYPE = "type";
    private final static String DEVICE1 = "device1";
    private final static String DEVICE2 = "device2";
    private Date expiresAt;
    private Date createdAt;
    private Device device1;
    private Device device2;

    @Before
    public void setUp() {
        doNothing().when(auditService).report(any());
        createdAt = new Date();
        expiresAt = new Date(System.currentTimeMillis() + 7200);
        device1 = new Device().setId(UUID.randomUUID().toString()).setReferenceType(ReferenceType.DOMAIN)
                .setReferenceId(DOMAIN)
                .setClient(CLIENT)
                .setUserId(USER)
                .setDeviceIdentifierId(DEVICE_IDENTIFIER)
                .setType(TYPE)
                .setDeviceId(DEVICE1)
                .setCreatedAt(createdAt)
                .setExpiresAt(expiresAt);

        device2 = new Device().setId(UUID.randomUUID().toString()).setReferenceType(ReferenceType.DOMAIN)
                .setReferenceId(DOMAIN)
                .setClient(CLIENT)
                .setUserId(USER)
                .setDeviceIdentifierId(REMEMBER_DEVICE2)
                .setType(TYPE)
                .setDeviceId(DEVICE2)
                .setCreatedAt(createdAt)
                .setExpiresAt(expiresAt);
    }

    @Test
    public void mustFindByDomainAndApplicationAndUser() {
        doReturn(Flowable.fromIterable(List.of(device1, device2))).when(deviceRepository).findByDomainAndClientAndUser(DOMAIN, USER);

        final TestSubscriber<Device> testFull = deviceService.findByDomainAndUser(DOMAIN, USER).test();
        testFull.awaitTerminalEvent();
        testFull.assertNoErrors();
        testFull.assertValueCount(2);

        verify(deviceRepository, times(1)).findByDomainAndClientAndUser(DOMAIN, USER);
    }

    @Test
    public void mustFindNothing() {
        doReturn(Flowable.fromIterable(List.of())).when(deviceRepository).findByDomainAndClientAndUser(DOMAIN2, USER);

        final TestSubscriber<Device> testEmpty = deviceService.findByDomainAndUser(DOMAIN2, USER).test();
        testEmpty.awaitTerminalEvent();
        testEmpty.assertNoErrors();
        testEmpty.assertValueCount(0);

        verify(deviceRepository, times(1)).findByDomainAndClientAndUser(DOMAIN2, USER);
    }

    @Test
    public void mustCreate_Device() {
        doReturn(Single.just(device1)).when(deviceRepository).create(any());

        final TestObserver<Device> testObserver = deviceService.create(DOMAIN, CLIENT, USER, DEVICE_IDENTIFIER, TYPE, 7200L, DEVICE1).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue(device1::equals);
    }

    @Test
    public void mustReturn_SingleBoolean() {
        doReturn(Maybe.empty()).when(deviceRepository).findByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId(DOMAIN, CLIENT, USER, DEVICE_IDENTIFIER, DEVICE1);
        doReturn(Maybe.just(device2)).when(deviceRepository).findByDomainAndClientAndUserAndDeviceIdentifierAndDeviceId(DOMAIN2, CLIENT, USER, DEVICE_IDENTIFIER, DEVICE1);

        final TestObserver<Boolean> testObserverEmpty = deviceService.deviceExists(DOMAIN, CLIENT, USER, DEVICE_IDENTIFIER, DEVICE1).test();
        testObserverEmpty.awaitTerminalEvent();
        testObserverEmpty.assertNoErrors();
        testObserverEmpty.assertValue(TRUE::equals);

        final TestObserver<Boolean> testObserver = deviceService.deviceExists(DOMAIN2, CLIENT, USER, DEVICE_IDENTIFIER, DEVICE1).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        testObserver.assertValue(FALSE::equals);
    }

    @Test
    public void mustNotDelete_deviceDoesNotExist() {
        doReturn(Maybe.empty()).when(deviceRepository).findById(device1.getId());

        deviceService.delete(DOMAIN, USER, device1.getId(), new DefaultUser())
                .test()
                .assertFailure(DeviceNotFoundException.class);
    }

    @Test
    public void mustNotDelete_deviceDoesNotBelongToContext() {
        doReturn(Maybe.just(device1)).when(deviceRepository).findById(device1.getId());

        deviceService.delete(DOMAIN2, USER, device1.getId(), new DefaultUser())
                .test()
                .assertFailure(DeviceNotFoundException.class);
    }

    @Test
    public void mustDeleteDevice() {
        doReturn(Maybe.just(device1)).when(deviceRepository).findById(device1.getId());
        doReturn(Completable.complete()).when(deviceRepository).delete(device1.getId());

        deviceService.delete(DOMAIN, USER, device1.getId(), new DefaultUser())
                .test().assertNoErrors();
    }
}
