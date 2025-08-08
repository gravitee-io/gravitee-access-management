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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.common.event.CertificateEvent;
import io.gravitee.am.management.service.DomainNotifierService;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.plugins.certificate.core.CertificateProviderConfiguration;
import io.gravitee.am.service.CertificateService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventManager;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateManagerImplTest {

    @Mock
    private CertificatePluginManager certificatePluginManager;

    @Mock
    private CertificateService certificateService;

    @Mock
    private EventManager eventManager;

    @Mock
    private DomainNotifierService notifierService;

    @Mock
    private CertificateProvider certificateProvider;

    @InjectMocks
    private CertificateManagerImpl certificateManager;

    private Certificate certificate;
    private final String CERTIFICATE_ID = "cert-123";
    private final String DOMAIN_ID = "domain-123";

    @BeforeEach
    void setUp() {
        certificate = new Certificate();
        certificate.setId(CERTIFICATE_ID);
        certificate.setName("Test Certificate");
        certificate.setType("RSA");
        certificate.setDomain(DOMAIN_ID);
    }

    @Test
    void shouldStartAndInitializeCertificates() throws Exception {
        when(certificateService.findAll()).thenReturn(Flowable.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.empty());

        certificateManager.doStart();

        verify(eventManager).subscribeForEvents(certificateManager, CertificateEvent.class);
        verify(certificateService).findAll();
        verify(certificatePluginManager).create(any(CertificateProviderConfiguration.class));
    }

    @Test
    void shouldGetCertificateProviderWhenExists() {
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.empty());

        certificateManager.onEvent(createEvent(CertificateEvent.DEPLOY, CERTIFICATE_ID, DOMAIN_ID));

        Maybe<CertificateProvider> result = certificateManager.getCertificateProvider(CERTIFICATE_ID);

        assertTrue(result.blockingGet() != null);
        assertEquals(certificateProvider, result.blockingGet());
    }

    @Test
    void shouldReturnEmptyWhenCertificateProviderNotFoundAndCertificateNotExists() {
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.empty());

        Maybe<CertificateProvider> result = certificateManager.getCertificateProvider(CERTIFICATE_ID);

        assertTrue(result.isEmpty().blockingGet());
    }

    @Test
    void shouldHandleDeployEvent() {
        Event<CertificateEvent, Payload> event = createEvent(CertificateEvent.DEPLOY, CERTIFICATE_ID, null);
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.empty());

        certificateManager.onEvent(event);

        verify(certificateService).findById(CERTIFICATE_ID);
        verify(certificatePluginManager).create(any(CertificateProviderConfiguration.class));
    }

    @Test
    void shouldHandleUpdateEvent() {
        Event<CertificateEvent, Payload> event = createEvent(CertificateEvent.UPDATE, CERTIFICATE_ID, DOMAIN_ID);
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.empty());
        when(notifierService.deleteCertificateExpirationAcknowledgement(CERTIFICATE_ID))
                .thenReturn(Completable.complete());

        certificateManager.onEvent(event);

        verify(notifierService).unregisterCertificateExpiration(DOMAIN_ID, CERTIFICATE_ID);
        verify(notifierService).deleteCertificateExpirationAcknowledgement(CERTIFICATE_ID);
        verify(certificateService).findById(CERTIFICATE_ID);
    }

    @Test
    void shouldHandleUndeployEvent() {
        Event<CertificateEvent, Payload> event = createEvent(CertificateEvent.UNDEPLOY, CERTIFICATE_ID, DOMAIN_ID);
        when(notifierService.deleteCertificateExpirationAcknowledgement(CERTIFICATE_ID))
                .thenReturn(Completable.complete());

        certificateManager.onEvent(event);

        verify(notifierService).unregisterCertificateExpiration(DOMAIN_ID, CERTIFICATE_ID);
        verify(notifierService).deleteCertificateExpirationAcknowledgement(CERTIFICATE_ID);
    }

    @Test
    void shouldLoadCertificateAndRegisterExpirationNotification() {
        Date expirationDate = new Date();
        certificate.setExpiresAt(null);
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.of(expirationDate));
        when(certificateService.updateExpirationDate(CERTIFICATE_ID, expirationDate))
                .thenReturn(Completable.complete());

        certificateManager.onEvent(createEvent(CertificateEvent.DEPLOY, CERTIFICATE_ID, DOMAIN_ID));
        
        verify(certificatePluginManager).create(any(CertificateProviderConfiguration.class));
        verify(certificateService).updateExpirationDate(CERTIFICATE_ID, expirationDate);
        verify(notifierService).registerCertificateExpiration(certificate);
    }

    @Test
    void shouldNotUpdateExpirationDateWhenUnchanged() {
        Date expirationDate = new Date();
        certificate.setExpiresAt(expirationDate);
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.of(expirationDate));

        certificateManager.onEvent(createEvent(CertificateEvent.DEPLOY, CERTIFICATE_ID, DOMAIN_ID));

        verify(certificateService, never()).updateExpirationDate(any(), any());
        verify(notifierService).registerCertificateExpiration(certificate);
    }

    @Test
    void shouldHandleCertificateProviderCreationFailure() {
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(null);
        when(notifierService.deleteCertificateExpirationAcknowledgement(CERTIFICATE_ID))
                .thenReturn(Completable.complete());

        certificateManager.onEvent(createEvent(CertificateEvent.DEPLOY, CERTIFICATE_ID, DOMAIN_ID));

        verify(notifierService).unregisterCertificateExpiration(DOMAIN_ID, CERTIFICATE_ID);
        verify(notifierService).deleteCertificateExpirationAcknowledgement(CERTIFICATE_ID);
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenCertificateServiceFails() {
        when(certificateService.findById(CERTIFICATE_ID))
                .thenThrow(new RuntimeException("Database error"));

        assertThrows(IllegalStateException.class,
                () -> certificateManager.getCertificateProvider(CERTIFICATE_ID).blockingGet());
    }

    @Test
    void shouldLoadCertificateWithoutExpiryDateAndNotRegisterExpirationNotification() {
        certificate.setExpiresAt(null);
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.empty());

        certificateManager.onEvent(createEvent(CertificateEvent.DEPLOY, CERTIFICATE_ID, DOMAIN_ID));

        verify(certificatePluginManager).create(any(CertificateProviderConfiguration.class));
        verify(certificateService, never()).updateExpirationDate(any(), any());
        verify(notifierService, never()).registerCertificateExpiration(any());
        
        Maybe<CertificateProvider> result = certificateManager.getCertificateProvider(CERTIFICATE_ID);
        assertTrue(result.blockingGet() != null);
        assertEquals(certificateProvider, result.blockingGet());
    }

    private Event<CertificateEvent, Payload> createEvent(CertificateEvent eventType, String certificateId, String domainId) {
        Payload payload = new Payload(certificateId, ReferenceType.DOMAIN, domainId, null);

        return new Event<CertificateEvent, Payload>() {
            @Override
            public CertificateEvent type() {
                return eventType;
            }

            @Override
            public Payload content() {
                return payload;
            }
        };
    }
}