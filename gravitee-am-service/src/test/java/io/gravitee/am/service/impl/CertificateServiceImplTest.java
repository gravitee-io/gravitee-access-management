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
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CertificatePluginService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.PluginConfigurationValidationService;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.TaskManager;
import io.gravitee.am.service.exception.CertificateWithApplicationsException;
import io.gravitee.am.service.exception.CertificateWithIdpException;
import io.gravitee.am.service.exception.CertificateWithProtectedResourceException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CertificateServiceImplTest {

    @Mock
    private CertificateRepository certificateRepository;

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

}
