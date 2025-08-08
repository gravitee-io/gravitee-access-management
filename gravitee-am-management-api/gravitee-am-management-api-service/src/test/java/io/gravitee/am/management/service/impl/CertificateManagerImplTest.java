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
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
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
    void should_start_and_initialize_certificates() throws Exception {
        // Given
        when(certificateService.findAll()).thenReturn(Flowable.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.empty());

        // When
        certificateManager.doStart();

        // Then
        verify(eventManager).subscribeForEvents(certificateManager, CertificateEvent.class);
        verify(certificateService).findAll();
        verify(certificatePluginManager).create(any(CertificateProviderConfiguration.class));
    }

    @Test
    void should_get_certificate_provider_when_exists() {
        // Given
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.empty());

        // Load certificate first
        certificateManager.onEvent(createEvent(CertificateEvent.DEPLOY, CERTIFICATE_ID, DOMAIN_ID));

        // When
        Maybe<CertificateProvider> result = certificateManager.getCertificateProvider(CERTIFICATE_ID);

        // Then
        assertTrue(result.blockingGet() != null);
        assertEquals(certificateProvider, result.blockingGet());
    }

    @Test
    void should_return_empty_when_certificate_provider_not_found_and_certificate_not_exists() {
        // Given
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.empty());

        // When
        Maybe<CertificateProvider> result = certificateManager.getCertificateProvider(CERTIFICATE_ID);

        // Then
        assertTrue(result.isEmpty().blockingGet());
    }

    @Test
    void should_handle_deploy_event() {
        // Given
        Event<CertificateEvent, Payload> event = createEvent(CertificateEvent.DEPLOY, CERTIFICATE_ID, null);
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.empty());

        // When
        certificateManager.onEvent(event);

        // Then
        verify(certificateService).findById(CERTIFICATE_ID);
        verify(certificatePluginManager).create(any(CertificateProviderConfiguration.class));
    }

    @Test
    void should_handle_update_event() {
        // Given
        Event<CertificateEvent, Payload> event = createEvent(CertificateEvent.UPDATE, CERTIFICATE_ID, DOMAIN_ID);
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.empty());
        when(notifierService.deleteCertificateExpirationAcknowledgement(CERTIFICATE_ID))
                .thenReturn(Completable.complete());

        // When
        certificateManager.onEvent(event);

        // Then
        verify(notifierService).unregisterCertificateExpiration(DOMAIN_ID, CERTIFICATE_ID);
        verify(notifierService).deleteCertificateExpirationAcknowledgement(CERTIFICATE_ID);
        verify(certificateService).findById(CERTIFICATE_ID);
    }

    @Test
    void should_handle_undeploy_event() {
        // Given
        Event<CertificateEvent, Payload> event = createEvent(CertificateEvent.UNDEPLOY, CERTIFICATE_ID, DOMAIN_ID);
        when(notifierService.deleteCertificateExpirationAcknowledgement(CERTIFICATE_ID))
                .thenReturn(Completable.complete());

        // When
        certificateManager.onEvent(event);

        // Then
        verify(notifierService).unregisterCertificateExpiration(DOMAIN_ID, CERTIFICATE_ID);
        verify(notifierService).deleteCertificateExpirationAcknowledgement(CERTIFICATE_ID);
    }

    @Test
    void should_load_certificate_and_register_expiration_notification() {
        // Given
        Date expirationDate = new Date();
        certificate.setExpiresAt(null);
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.of(expirationDate));
        when(certificateService.updateExpirationDate(CERTIFICATE_ID, expirationDate))
                .thenReturn(Completable.complete());

        certificateManager.onEvent(createEvent(CertificateEvent.DEPLOY, CERTIFICATE_ID, DOMAIN_ID));

        // When
        //certificateManager.loadCertificate(certificate);

        // Then
        verify(certificatePluginManager).create(any(CertificateProviderConfiguration.class));
        verify(certificateService).updateExpirationDate(CERTIFICATE_ID, expirationDate);
        verify(notifierService).registerCertificateExpiration(certificate);
    }

    @Test
    void should_not_update_expiration_date_when_unchanged() {
        // Given
        Date expirationDate = new Date();
        certificate.setExpiresAt(expirationDate);
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.of(expirationDate));

        // When
        certificateManager.onEvent(createEvent(CertificateEvent.DEPLOY, CERTIFICATE_ID, DOMAIN_ID));

        // Then
        verify(certificateService, never()).updateExpirationDate(any(), any());
        verify(notifierService).registerCertificateExpiration(certificate);
    }

    @Test
    void should_handle_certificate_provider_creation_failure() {
        // Given
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(null);
        when(notifierService.deleteCertificateExpirationAcknowledgement(CERTIFICATE_ID))
                .thenReturn(Completable.complete());

        // When
        certificateManager.onEvent(createEvent(CertificateEvent.DEPLOY, CERTIFICATE_ID, DOMAIN_ID));

        // Then
        verify(notifierService).unregisterCertificateExpiration(DOMAIN_ID, CERTIFICATE_ID);
        verify(notifierService).deleteCertificateExpirationAcknowledgement(CERTIFICATE_ID);
    }

    @Test
    void should_throw_illegal_state_exception_when_certificate_service_fails() {
        // Given
        when(certificateService.findById(CERTIFICATE_ID))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThrows(IllegalStateException.class,
                () -> certificateManager.getCertificateProvider(CERTIFICATE_ID).blockingGet());
    }

    @Test
    void should_load_certificate_without_expiry_date_and_not_register_expiration_notification() {
        // Given
        certificate.setExpiresAt(null);
        when(certificateService.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(certificatePluginManager.create(any(CertificateProviderConfiguration.class)))
                .thenReturn(certificateProvider);
        when(certificateProvider.getExpirationDate()).thenReturn(Optional.empty());

        // When
        certificateManager.onEvent(createEvent(CertificateEvent.DEPLOY, CERTIFICATE_ID, DOMAIN_ID));

        // Then
        verify(certificatePluginManager).create(any(CertificateProviderConfiguration.class));
        verify(certificateService, never()).updateExpirationDate(any(), any());
        verify(notifierService, never()).registerCertificateExpiration(any());
        
        // Verify certificate provider is available
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