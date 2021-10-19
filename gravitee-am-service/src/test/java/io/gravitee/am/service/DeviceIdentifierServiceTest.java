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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.DeviceIdentifierRepository;
import io.gravitee.am.service.exception.DeviceIdentifierNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.DeviceIdentifierServiceImpl;
import io.gravitee.am.service.model.NewDeviceIdentifier;
import io.gravitee.am.service.model.UpdateDeviceIdentifier;
import io.gravitee.am.service.reporter.builder.management.DeviceIdentifierAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DeviceIdentifierServiceTest {

    @InjectMocks
    private DeviceIdentifierService deviceIdentifierService = new DeviceIdentifierServiceImpl();

    @Mock
    private EventService eventService;

    @Mock
    private DeviceIdentifierRepository deviceIdentifierRepository;

    @Mock
    private AuditService auditService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(deviceIdentifierRepository.findById("device-identifier")).thenReturn(Maybe.just(new DeviceIdentifier()));
        TestObserver testObserver = deviceIdentifierService.findById("device-identifier").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingDeviceIdentifier() {
        when(deviceIdentifierRepository.findById("device-identifier")).thenReturn(Maybe.empty());
        TestObserver testObserver = deviceIdentifierService.findById("device-identifier").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(deviceIdentifierRepository.findById("device-identifier")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        deviceIdentifierService.findById("device-identifier").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(deviceIdentifierRepository.findByReference(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(new DeviceIdentifier()));
        TestSubscriber<DeviceIdentifier> testSubscriber = deviceIdentifierService.findByDomain(DOMAIN).test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(deviceIdentifierRepository.findByReference(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = deviceIdentifierService.findByDomain(DOMAIN).test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewDeviceIdentifier newDeviceIdentifier = Mockito.mock(NewDeviceIdentifier.class);
        when(deviceIdentifierRepository.create(any(DeviceIdentifier.class))).thenReturn(Single.just(new DeviceIdentifier()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = deviceIdentifierService.create(DOMAIN, newDeviceIdentifier).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(eventService).create(any());
        verify(auditService).report(any(DeviceIdentifierAuditBuilder.class));
        verify(deviceIdentifierRepository).create(any(DeviceIdentifier.class));
    }

    @Test
    public void shouldCreate_technicalException() {
        NewDeviceIdentifier newDeviceIdentifier = Mockito.mock(NewDeviceIdentifier.class);
        when(deviceIdentifierRepository.create(any())).thenReturn(Single.error(TechnicalException::new));

        TestObserver<DeviceIdentifier> testObserver = new TestObserver<>();
        deviceIdentifierService.create(DOMAIN, newDeviceIdentifier).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(eventService, never()).create(any());
        verify(auditService).report(any(DeviceIdentifierAuditBuilder.class));
    }

    @Test
    public void shouldUpdate() {
        UpdateDeviceIdentifier updateDeviceIdentifier = Mockito.mock(UpdateDeviceIdentifier.class);
        when(updateDeviceIdentifier.getName()).thenReturn("device-identifier");
        when(deviceIdentifierRepository.findById("device-identifier")).thenReturn(Maybe.just(new DeviceIdentifier()));
        when(deviceIdentifierRepository.update(any(DeviceIdentifier.class))).thenReturn(Single.just(new DeviceIdentifier()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = deviceIdentifierService.update(DOMAIN, "device-identifier", updateDeviceIdentifier).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(deviceIdentifierRepository).findById(anyString());
        verify(auditService).report(any(DeviceIdentifierAuditBuilder.class));
        verify(deviceIdentifierRepository).update(any(DeviceIdentifier.class));
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateDeviceIdentifier updateDeviceIdentifier = Mockito.mock(UpdateDeviceIdentifier.class);
        when(deviceIdentifierRepository.findById("device-identifier")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = deviceIdentifierService.update(DOMAIN, "device-identifier", updateDeviceIdentifier).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(deviceIdentifierRepository).findById(anyString());
        verify(deviceIdentifierRepository, never()).update(any(DeviceIdentifier.class));
        verify(auditService, never()).report(any(DeviceIdentifierAuditBuilder.class));
    }

    @Test
    public void shouldDelete_notDeviceIdentifier() {
        when(deviceIdentifierRepository.findById("device-identifier")).thenReturn(Maybe.empty());

        TestObserver testObserver = deviceIdentifierService.delete(DOMAIN, "device-identifier").test();

        testObserver.assertError(DeviceIdentifierNotFoundException.class);
        testObserver.assertNotComplete();

        verify(deviceIdentifierRepository, never()).delete(anyString());
        verify(auditService, never()).report(any(DeviceIdentifierAuditBuilder.class));
    }

    @Test
    public void shouldDelete_technicalException() {
        when(deviceIdentifierRepository.findById("device-identifier")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = deviceIdentifierService.delete(DOMAIN, "device-identifier").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        DeviceIdentifier detection = new DeviceIdentifier();
        detection.setId("detection-id");
        when(deviceIdentifierRepository.findById(detection.getId())).thenReturn(Maybe.just(detection));
        when(deviceIdentifierRepository.delete(detection.getId())).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = deviceIdentifierService.delete(DOMAIN, detection.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(deviceIdentifierRepository).delete(detection.getId());
        verify(auditService).report(any(DeviceIdentifierAuditBuilder.class));
    }
}
