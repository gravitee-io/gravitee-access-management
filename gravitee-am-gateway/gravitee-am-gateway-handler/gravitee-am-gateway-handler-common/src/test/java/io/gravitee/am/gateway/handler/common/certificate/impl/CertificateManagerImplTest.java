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
package io.gravitee.am.gateway.handler.common.certificate.impl;

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.common.event.CertificateEvent;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.exception.oauth2.TemporarilyUnavailableException;
import io.gravitee.am.gateway.certificate.CertificateProviderManager;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.common.event.Event;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CertificateManagerImplTest {

    @Spy
    @InjectMocks
    private CertificateManagerImpl certificateManager = new CertificateManagerImpl();

    @Mock
    private CertificateProviderManager certificateProviderManager;

    @Mock
    private Domain domain;

    @Mock
    private DomainReadinessService domainReadinessService;

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderCertificateReloader identityProviderReloader;

    private static final String DOMAIN_ID = "test-domain-id";
    private static final String CERTIFICATE_ID = "test-certificate-id";

    io.gravitee.am.gateway.certificate.CertificateProvider defaultProvider;

    @Before
    public void setUp() throws Exception {
        CertificateProvider rs256CertificateProvider = mock(CertificateProvider.class);
        CertificateProvider rs512CertificateProvider = mock(CertificateProvider.class);
        when(rs256CertificateProvider.signatureAlgorithm()).thenReturn("RS256");
        when(rs512CertificateProvider.signatureAlgorithm()).thenReturn("RS512");

        io.gravitee.am.gateway.certificate.CertificateProvider rs256CertProvider =
                mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        io.gravitee.am.gateway.certificate.CertificateProvider rs512CertProvider =
                mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        when(rs256CertProvider.getProvider()).thenReturn(rs256CertificateProvider);
        when(rs512CertProvider.getProvider()).thenReturn(rs512CertificateProvider);
        doReturn(Arrays.asList(rs256CertProvider,rs512CertProvider)).when(certificateManager).providers();

        defaultProvider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        certificateManager.defaultCertificateProvider = defaultProvider;
    }

    @Test
    public void findByAlgorithm_nullAlgorithm() {
        TestObserver testObserver = certificateManager.findByAlgorithm(null).test();
        testObserver
                .assertComplete()
                .assertNoValues();

    }

    @Test
    public void findByAlgorithm_emptyAlgorithm() {
        TestObserver testObserver = certificateManager.findByAlgorithm("").test();
        testObserver.assertComplete();
        testObserver
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void findByAlgorithm_unknownAlgorithm() {
        TestObserver testObserver = certificateManager.findByAlgorithm("unknown").test();
        testObserver.assertComplete();
        testObserver
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void findByAlgorithm_foundAlgorithm() {
        TestObserver testObserver = certificateManager.findByAlgorithm("RS512").test();
        testObserver.assertComplete();
        testObserver
                .assertComplete()
                .assertValue(o -> "RS512".equals(
                        ((io.gravitee.am.gateway.certificate.CertificateProvider)o)
                                .getProvider()
                                .signatureAlgorithm()
                ));
    }

    @Test
    public void findClientCertificate_shouldReturnIfFound(){
        Client client = new Client();
        client.setCertificate("id");
        var provider = new io.gravitee.am.gateway.certificate.CertificateProvider(null);

        Mockito.when(certificateProviderManager.get("id")).thenReturn(provider);

        certificateManager.getClientCertificateProvider(client, true)
                .test()
                .assertValue(cp -> cp == provider);

        certificateManager.getClientCertificateProvider(client, false)
                .test()
                .assertValue(cp -> cp == provider);
    }

    @Test
    public void findClientCertificate_shouldReturnHmacIfCertificateIsEmpty(){
        Client client = new Client();
        client.setCertificate(null);

        certificateManager.getClientCertificateProvider(client, true)
                .test()
                .assertValue(cp -> cp == defaultProvider);

        certificateManager.getClientCertificateProvider(client, false)
                .test()
                .assertValue(cp -> cp == defaultProvider);
    }

    @Test
    public void findClientCertificate_shouldFallbackIfNotFound(){
        Client client = new Client();
        client.setCertificate("id");

        Mockito.when(certificateProviderManager.get("id")).thenReturn(null);

        certificateManager.getClientCertificateProvider(client, true)
                .test()
                .assertValue(cp -> cp == defaultProvider);

    }

    @Test
    public void findClientCertificate_shouldReturnErrorIfFallbackIsDisabled(){
        Client client = new Client();
        client.setCertificate("id");

        Mockito.when(certificateProviderManager.get("id")).thenReturn(null);

        certificateManager.getClientCertificateProvider(client, false)
                .test()
                .assertError(th -> th instanceof TemporarilyUnavailableException);

    }

    @Test
    public void deployCertificate_shouldCallInitPluginSync() throws InterruptedException {
        // Arrange
        when(domain.getId()).thenReturn(DOMAIN_ID);

        Certificate certificate = new Certificate();
        certificate.setId(CERTIFICATE_ID);
        certificate.setName("Test Certificate");
        certificate.setDomain(DOMAIN_ID);

        when(certificateRepository.findById(CERTIFICATE_ID)).thenReturn(Maybe.just(certificate));
        when(identityProviderReloader.reloadIdentityProvidersWithCertificate(CERTIFICATE_ID))
                .thenReturn(io.reactivex.rxjava3.core.Completable.complete());

        // Create a mock event for DEPLOY
        Event<CertificateEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(CertificateEvent.DEPLOY);
        when(event.content()).thenReturn(new Payload(CERTIFICATE_ID, ReferenceType.DOMAIN, DOMAIN_ID, io.gravitee.am.common.event.Action.CREATE));

        // Act
        certificateManager.onEvent(event);

        // Wait for async processing
        TimeUnit.MILLISECONDS.sleep(200);

        // Assert - initPluginSync should be called before the certificate is loaded
        verify(domainReadinessService, timeout(1000)).initPluginSync(DOMAIN_ID, CERTIFICATE_ID, Type.CERTIFICATE.name());
        verify(domainReadinessService, timeout(1000)).pluginLoaded(DOMAIN_ID, CERTIFICATE_ID);
    }

    @Test
    public void deployCertificate_shouldCallPluginFailedOnError() throws InterruptedException {
        // Arrange
        when(domain.getId()).thenReturn(DOMAIN_ID);

        when(certificateRepository.findById(CERTIFICATE_ID))
                .thenReturn(Maybe.error(new RuntimeException("Database error")));

        // Create a mock event for DEPLOY
        Event<CertificateEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(CertificateEvent.DEPLOY);
        when(event.content()).thenReturn(new Payload(CERTIFICATE_ID, ReferenceType.DOMAIN, DOMAIN_ID, io.gravitee.am.common.event.Action.CREATE));

        // Act
        certificateManager.onEvent(event);

        // Wait for async processing
        TimeUnit.MILLISECONDS.sleep(200);

        // Assert - initPluginSync should be called, followed by pluginFailed
        verify(domainReadinessService, timeout(1000)).initPluginSync(DOMAIN_ID, CERTIFICATE_ID, Type.CERTIFICATE.name());
        verify(domainReadinessService, timeout(1000)).pluginFailed(eq(DOMAIN_ID), eq(CERTIFICATE_ID), anyString());
    }

    @Test
    public void undeployCertificate_shouldCallPluginUnloaded() {
        // Arrange
        when(domain.getId()).thenReturn(DOMAIN_ID);

        // Create a mock event for UNDEPLOY
        Event<CertificateEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(CertificateEvent.UNDEPLOY);
        when(event.content()).thenReturn(new Payload(CERTIFICATE_ID, ReferenceType.DOMAIN, DOMAIN_ID, io.gravitee.am.common.event.Action.DELETE));

        // Act
        certificateManager.onEvent(event);

        // Assert
        verify(domainReadinessService).pluginUnloaded(DOMAIN_ID, CERTIFICATE_ID);
    }
}
