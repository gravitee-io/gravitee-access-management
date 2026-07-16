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
package io.gravitee.am.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.common.plugin.ValidationResult;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.CertificateSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.service.model.UpdateCertificate;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CertificatePluginService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.PluginConfigurationValidationService;
import io.gravitee.am.service.PluginLicenseGate;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.TaskManager;
import io.gravitee.am.service.exception.CertificateIsFallbackException;
import io.gravitee.am.service.exception.CertificateWithApplicationsException;
import io.gravitee.am.service.exception.CertificateWithIdpException;
import io.gravitee.am.service.exception.CertificateWithProtectedResourceException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CertificateServiceImplTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private ProtectedResourceService protectedResourceService;

    @Mock
    private IdentityProviderService identityProviderService;

    @Mock
    private EventService eventService;

    @Mock
    private AuditService auditService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CertificatePluginService certificatePluginService;

    @Mock
    private CertificatePluginManager certificatePluginManager;

    @Mock
    private Environment environment;

    @Mock
    private TaskManager taskManager;

    @Mock
    private PluginConfigurationValidationService validationService;

    @Mock
    private PluginLicenseGate pluginLicenseGate;

    @InjectMocks
    private CertificateServiceImpl service;

    @Test
    void shouldDeleteCertificateWhenItIsNotAssociated() {
        // given
        String certId = "certId";
        Certificate cert = new Certificate();
        cert.setId(certId);
        cert.setDomain("domainId");
        Maybe<Certificate> certificate = Maybe.just(cert);
        Mockito.when(certificateRepository.findById(certId))
                .thenReturn(certificate);

        Mockito.when(applicationService.findByCertificate(certId))
                .thenReturn(Flowable.empty());

        Mockito.when(protectedResourceService.findByCertificate(certId))
                .thenReturn(Flowable.empty());

        Mockito.when(identityProviderService.findByDomain("domainId"))
                .thenReturn(Flowable.empty());

        Mockito.when(domainRepository.findById("domainId"))
                .thenReturn(Maybe.just(new Domain()));

        Mockito.when(certificateRepository.delete(certId))
                .thenReturn(Completable.complete());

        Mockito.when(eventService.create(any())).thenReturn(Single.just(new Event()));

        // when
        TestObserver<Void> observer = service.delete(certId, new DefaultUser()).test();

        // then
        observer.assertComplete();
    }

    @Test
    void onCertificateDeleteShouldThrowExIfAnyApplicationIsAssociated() {
        // given
        String certId = "certId";
        Certificate cert = new Certificate();
        cert.setId(certId);
        cert.setDomain("domainId");
        Maybe<Certificate> certificate = Maybe.just(cert);
        Mockito.when(certificateRepository.findById(certId))
                .thenReturn(certificate);

        Mockito.when(applicationService.findByCertificate(certId))
                .thenReturn(Flowable.just(new Application()));

        // when
        TestObserver<Void> observer = service.delete(certId, new DefaultUser()).test();

        // then
        observer.assertError(ex -> ex instanceof CertificateWithApplicationsException);
    }

    @Test
    void onCertificateDeleteShouldThrowExIfAnyProtectedResourceIsAssociated() {
        // given
        String certId = "certId";
        Certificate cert = new Certificate();
        cert.setId(certId);
        cert.setDomain("domainId");
        Maybe<Certificate> certificate = Maybe.just(cert);
        Mockito.when(certificateRepository.findById(certId))
                .thenReturn(certificate);

        Mockito.when(identityProviderService.findByDomain("domainId"))
                .thenReturn(Flowable.empty());

        Mockito.when(applicationService.findByCertificate(certId))
                .thenReturn(Flowable.empty());

        Mockito.when(protectedResourceService.findByCertificate(certId))
                .thenReturn(Flowable.just(new ProtectedResource()));

        // when
        TestObserver<Void> observer = service.delete(certId, new DefaultUser()).test();

        // then
        observer.assertError(ex -> ex instanceof CertificateWithProtectedResourceException);
    }

    @Test
    void onCertificateDeleteShouldThrowExIfAnyIdpIsAssociated() {
        // given
        String certId = "32d89a07-c7a9-48c4-989a-07c7a9b8c4ef";
        Certificate cert = new Certificate();
        cert.setId(certId);
        cert.setDomain("domainId");

        IdentityProvider idp = new IdentityProvider();
        idp.setConfiguration("{\"clientAuthenticationCertificate\":\"32d89a07-c7a9-48c4-989a-07c7a9b8c4ef\"}");

        Maybe<Certificate> certificate = Maybe.just(cert);
        Mockito.when(certificateRepository.findById(certId))
                .thenReturn(certificate);

        Mockito.when(applicationService.findByCertificate(certId))
                .thenReturn(Flowable.empty());

        Mockito.when(identityProviderService.findByDomain("domainId"))
                .thenReturn(Flowable.just(idp));

        // when
        TestObserver<Void> observer = service.delete(certId, new DefaultUser()).test();

        // then
        observer.assertError(ex -> ex instanceof CertificateWithIdpException);
    }

    @Test
    void onCertificateDeleteShouldThrowExIfCertificateIsFallback() {
        // given
        String certId = "fallback-cert-id";
        Certificate cert = new Certificate();
        cert.setId(certId);
        cert.setDomain("domainId");

        CertificateSettings certSettings = new CertificateSettings();
        certSettings.setFallbackCertificate(certId);

        Domain domain = new Domain();
        domain.setId("domainId");
        domain.setCertificateSettings(certSettings);

        Maybe<Certificate> certificate = Maybe.just(cert);
        Mockito.when(certificateRepository.findById(certId))
                .thenReturn(certificate);

        Mockito.when(applicationService.findByCertificate(certId))
                .thenReturn(Flowable.empty());

        Mockito.when(protectedResourceService.findByCertificate(certId))
                .thenReturn(Flowable.empty());

        Mockito.when(identityProviderService.findByDomain("domainId"))
                .thenReturn(Flowable.empty());

        Mockito.when(domainRepository.findById("domainId"))
                .thenReturn(Maybe.just(domain));

        // when
        TestObserver<Void> observer = service.delete(certId, new DefaultUser()).test();

        // then
        observer.assertError(ex -> ex instanceof CertificateIsFallbackException);
    }

    @Test
    void shouldDeleteCertificateWhenDifferentCertificateIsFallback() {
        // given
        String certId = "certId";
        Certificate cert = new Certificate();
        cert.setId(certId);
        cert.setDomain("domainId");

        CertificateSettings certSettings = new CertificateSettings();
        certSettings.setFallbackCertificate("other-cert-id");

        Domain domain = new Domain();
        domain.setId("domainId");
        domain.setCertificateSettings(certSettings);

        Maybe<Certificate> certificate = Maybe.just(cert);
        Mockito.when(certificateRepository.findById(certId))
                .thenReturn(certificate);

        Mockito.when(applicationService.findByCertificate(certId))
                .thenReturn(Flowable.empty());

        Mockito.when(protectedResourceService.findByCertificate(certId))
                .thenReturn(Flowable.empty());

        Mockito.when(identityProviderService.findByDomain("domainId"))
                .thenReturn(Flowable.empty());

        Mockito.when(domainRepository.findById("domainId"))
                .thenReturn(Maybe.just(domain));

        Mockito.when(certificateRepository.delete(certId))
                .thenReturn(Completable.complete());

        Mockito.when(eventService.create(any())).thenReturn(Single.just(new Event()));

        // when
        TestObserver<Void> observer = service.delete(certId, new DefaultUser()).test();

        // then
        observer.assertComplete();
    }

    @Test
    void update_normalizes_embedded_file_to_filename_in_stored_config() throws Exception {
        // Use a real ObjectMapper so the embedded-file normalization is exercised faithfully.
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ObjectMapper realMapper = new ObjectMapper();

        String certId = "cert-id";
        String type = "javakeystore-am-certificate";
        String fileName = "keystore.p12";
        String base64 = Base64.getEncoder().encodeToString("dummy-keystore-bytes".getBytes());

        Domain domain = new Domain();
        domain.setId("domainId");

        // The stored certificate already holds the normalized (filename-form) configuration.
        Certificate existing = new Certificate();
        existing.setId(certId);
        existing.setDomain("domainId");
        existing.setType(type);
        existing.setConfiguration("{\"content\":\"" + fileName + "\"}");

        String schema = "{\"properties\":{\"content\":{\"widget\":\"file\"}}}";

        Mockito.when(certificateRepository.findById(certId)).thenReturn(Maybe.just(existing));
        Mockito.when(pluginLicenseGate.check(any(), any(), any())).thenReturn(Completable.complete());
        Mockito.when(certificatePluginService.getSchema(type)).thenReturn(Maybe.just(schema));
        Mockito.when(certificatePluginManager.validate(any())).thenReturn(ValidationResult.SUCCEEDED);
        Mockito.when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        Mockito.when(certificateRepository.update(any()))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        // The update payload carries the full embedded file blob ({name, content:<base64>}).
        String embeddedFile = "{\"name\":\"" + fileName + "\",\"content\":\"" + base64 + "\"}";
        ObjectNode cfg = realMapper.createObjectNode();
        cfg.put("content", embeddedFile);
        UpdateCertificate update = new UpdateCertificate();
        update.setName("My cert");
        update.setConfiguration(realMapper.writeValueAsString(cfg));

        TestObserver<Certificate> observer =
                service.update(domain, certId, update, new DefaultUser()).test();
        observer.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        observer.assertComplete();

        ArgumentCaptor<Certificate> captor = ArgumentCaptor.forClass(Certificate.class);
        Mockito.verify(certificateRepository).update(captor.capture());
        String storedConfig = captor.getValue().getConfiguration();

        // The persisted configuration keeps only the file name; the raw base64 content must not leak into it.
        assertEquals(fileName, realMapper.readTree(storedConfig).get("content").asText());
        assertFalse(storedConfig.contains(base64),
                "stored configuration must not contain the raw base64 keystore content");
    }

    @Test
    void update_rejects_type_change() {
        String certId = "cert-id";
        Certificate existing = new Certificate();
        existing.setId(certId);
        existing.setDomain("domainId");
        existing.setType("javakeystore-am-certificate");
        Mockito.when(certificateRepository.findById(certId)).thenReturn(Maybe.just(existing));

        Domain domain = new Domain();
        domain.setId("domainId");
        UpdateCertificate update = new UpdateCertificate();
        update.setName("My cert");
        update.setType("pkcs12-am-certificate");
        update.setConfiguration("{}");

        TestObserver<Certificate> observer = service.update(domain, certId, update, new DefaultUser()).test();
        observer.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        observer.assertError(InvalidParameterException.class);
        Mockito.verify(certificateRepository, Mockito.never()).update(Mockito.any());
    }

    @Test
    void shouldForceDeleteCertificateWhenCertificateIsFallback() {
        String certId = "fallback-cert-id";
        Certificate cert = new Certificate();
        cert.setId(certId);
        cert.setDomain("domainId");

        CertificateSettings certSettings = new CertificateSettings();
        certSettings.setFallbackCertificate(certId);

        Domain domain = new Domain();
        domain.setId("domainId");
        domain.setCertificateSettings(certSettings);

        Mockito.when(certificateRepository.findById(certId))
                .thenReturn(Maybe.just(cert));
        Mockito.lenient().when(domainRepository.findById("domainId"))
                .thenReturn(Maybe.just(domain));
        Mockito.when(certificateRepository.delete(certId))
                .thenReturn(Completable.complete());
        Mockito.when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<Void> observer = service.delete(certId, new DefaultUser(), true).test();

        observer.assertComplete();
        observer.assertNoErrors();
        Mockito.verify(domainRepository, Mockito.never()).findById("domainId");
        Mockito.verify(certificateRepository).delete(certId);
    }

    @Test
    void shouldForceDeleteCertificateWhenApplicationAndIdpAreAssociated() {
        String certId = "32d89a07-c7a9-48c4-989a-07c7a9b8c4ef";
        Certificate cert = new Certificate();
        cert.setId(certId);
        cert.setDomain("domainId");
        IdentityProvider idp = new IdentityProvider();
        idp.setConfiguration("{\"clientAuthenticationCertificate\":\"32d89a07-c7a9-48c4-989a-07c7a9b8c4ef\"}");

        Mockito.when(certificateRepository.findById(certId))
                .thenReturn(Maybe.just(cert));
        Mockito.lenient().when(applicationService.findByCertificate(certId))
                .thenReturn(Flowable.just(new Application()));
        Mockito.lenient().when(identityProviderService.findByDomain("domainId"))
                .thenReturn(Flowable.just(idp));
        Mockito.when(certificateRepository.delete(certId))
                .thenReturn(Completable.complete());
        Mockito.when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver<Void> observer = service.delete(certId, new DefaultUser(), true).test();

        observer.assertComplete();
        observer.assertNoErrors();
        Mockito.verify(applicationService, Mockito.never()).findByCertificate(certId);
        Mockito.verify(identityProviderService, Mockito.never()).findByDomain("domainId");
        Mockito.verify(certificateRepository).delete(certId);
    }

}
