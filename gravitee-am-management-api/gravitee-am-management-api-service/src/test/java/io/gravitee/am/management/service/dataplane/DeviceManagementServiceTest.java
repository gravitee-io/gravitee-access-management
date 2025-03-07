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

package io.gravitee.am.management.service.dataplane;


import io.gravitee.am.dataplane.api.repository.DeviceRepository;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.service.dataplane.impl.DeviceManagementServiceImpl;
import io.gravitee.am.model.Device;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserId;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.AuditService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class DeviceManagementServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private DeviceManagementService deviceService = new DeviceManagementServiceImpl();

    private final static Domain DOMAIN = new Domain("domain1");
    private final static Domain DOMAIN2 = new Domain("domain2");
    private final static String CLIENT = "client1";
    private final static UserId USER = UserId.internal("user1");
    private final static String DEVICE_IDENTIFIER = "rememberDevice1";
    private final static String REMEMBER_DEVICE2 = "rememberDevice2";
    private final static String TYPE = "type";
    private final static String DEVICE1 = "device1";
    private final static String DEVICE2 = "device2";
    private Date expiresAt;
    private Date createdAt;
    private Device device1;
    private Device device2;

    @BeforeEach
    public void setUp() {
        createdAt = new Date();
        expiresAt = new Date(System.currentTimeMillis() + 7200);
        device1 = new Device().setId(UUID.randomUUID().toString()).setReferenceType(ReferenceType.DOMAIN)
                .setReferenceId(DOMAIN.getId())
                .setClient(CLIENT)
                .setUserId(USER)
                .setDeviceIdentifierId(DEVICE_IDENTIFIER)
                .setType(TYPE)
                .setDeviceId(DEVICE1)
                .setCreatedAt(createdAt)
                .setExpiresAt(expiresAt);

        device2 = new Device().setId(UUID.randomUUID().toString()).setReferenceType(ReferenceType.DOMAIN)
                .setReferenceId(DOMAIN.getId())
                .setClient(CLIENT)
                .setUserId(USER)
                .setDeviceIdentifierId(REMEMBER_DEVICE2)
                .setType(TYPE)
                .setDeviceId(DEVICE2)
                .setCreatedAt(createdAt)
                .setExpiresAt(expiresAt);

        when(dataPlaneRegistry.getDeviceRepository(any())).thenReturn(deviceRepository);
    }

    @Test
    public void mustFindByDomainAndApplicationAndUser() {
        doReturn(Flowable.fromIterable(List.of(device1, device2))).when(deviceRepository).findByDomainAndClientAndUser(DOMAIN.getId(), USER);

        final TestSubscriber<Device> testFull = deviceService.findByDomainAndUser(DOMAIN, USER).test();
        testFull.awaitDone(10, TimeUnit.SECONDS);
        testFull.assertNoErrors();
        testFull.assertValueCount(2);

        verify(deviceRepository, times(1)).findByDomainAndClientAndUser(DOMAIN.getId(), USER);
    }

    @Test
    public void mustFindNothing() {
        doReturn(Flowable.fromIterable(List.of())).when(deviceRepository).findByDomainAndClientAndUser(DOMAIN2.getId(), USER);

        final TestSubscriber<Device> testEmpty = deviceService.findByDomainAndUser(DOMAIN2, USER).test();
        testEmpty.awaitDone(10, TimeUnit.SECONDS);
        testEmpty.assertNoErrors();
        testEmpty.assertValueCount(0);

        verify(deviceRepository, times(1)).findByDomainAndClientAndUser(DOMAIN2.getId(), USER);
    }

    @Test
    public void mustDelete(){
        doReturn(Completable.complete()).when(deviceRepository).delete(any());
        doReturn(Maybe.just(device1)).when(deviceRepository).findById(any());

        TestObserver<Void> testObserver = deviceService.delete(DOMAIN, USER, DEVICE1, new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();

        verify(deviceRepository, times(1)).delete(DEVICE1);
        verify(auditService, times(1)).report(any());
    }

}
