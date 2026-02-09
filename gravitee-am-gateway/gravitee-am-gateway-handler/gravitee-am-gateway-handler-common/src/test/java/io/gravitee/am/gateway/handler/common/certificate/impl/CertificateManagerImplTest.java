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
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.CertificateEvent;
import io.gravitee.am.common.event.DomainCertificateSettingsEvent;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.exception.oauth2.TemporarilyUnavailableException;
import io.gravitee.am.gateway.certificate.CertificateProviderManager;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.CertificateSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.common.event.Event;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private DomainRepository domainRepository;

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
        when(domain.getId()).thenReturn(DOMAIN_ID);

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
        lenient().when(defaultProvider.getDomain()).thenReturn(DOMAIN_ID);

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

        var provider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        when(provider.getDomain()).thenReturn(DOMAIN_ID);

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
    public void findClientCertificate_shouldUseFallbackCertificateProviderIfNotFound() {
        // Arrange
        Client client = new Client();
        client.setCertificate("id");

        Mockito.when(certificateProviderManager.get("id")).thenReturn(null);

        String fallbackCertificateId = "fallback-cert-id";
        CertificateSettings certificateSettings = new CertificateSettings();
        certificateSettings.setFallbackCertificate(fallbackCertificateId);
        when(domain.getCertificateSettings()).thenReturn(certificateSettings);

        var fallbackProvider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        when(fallbackProvider.getDomain()).thenReturn(DOMAIN_ID);
        when(certificateProviderManager.get(fallbackCertificateId)).thenReturn(fallbackProvider);

        ReflectionTestUtils.invokeMethod(certificateManager, "initCertificateSettings");

        // Assert
        certificateManager.getClientCertificateProvider(client, true)
                .test()
                .assertValue(cp -> cp == fallbackProvider);

        certificateManager.getClientCertificateProvider(client, false)
                .test()
                .assertValue(cp -> cp == fallbackProvider);
    }

    @Test
    public void findClientCertificate_shouldFallbackToDefaultIfNoFallbackAndLegacyEnabled() {
        // Arrrange
        Client client = new Client();
        client.setCertificate("id");

        Mockito.when(certificateProviderManager.get("id")).thenReturn(null);

        // Assert
        certificateManager.getClientCertificateProvider(client, true)
                .test()
                .assertValue(cp -> cp == defaultProvider);
    }

    @Test
    public void findClientCertificate_shouldReturnErrorIfNoFallbackAndLegacyDisabled() {
        // Arrange
        Client client = new Client();
        client.setCertificate("id");

        Mockito.when(certificateProviderManager.get("id")).thenReturn(null);

        // Assert
        certificateManager.getClientCertificateProvider(client, false)
                .test()
                .assertError(th -> th instanceof TemporarilyUnavailableException);
    }

    @Test
    public void deployCertificate_shouldCallInitPluginSync() throws InterruptedException {
        // Arrange
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
        // Create a mock event for UNDEPLOY
        Event<CertificateEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(CertificateEvent.UNDEPLOY);
        when(event.content()).thenReturn(new Payload(CERTIFICATE_ID, ReferenceType.DOMAIN, DOMAIN_ID, io.gravitee.am.common.event.Action.DELETE));

        // Act
        certificateManager.onEvent(event);

        // Assert
        verify(domainReadinessService).pluginUnloaded(DOMAIN_ID, CERTIFICATE_ID);
    }

    @Test
    public void get_shouldReturnCertificateProviderWhenBelongsToDomain() {
        // Arrange
        var certificateProvider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        when(certificateProvider.getDomain()).thenReturn(DOMAIN_ID);
        when(certificateProviderManager.get(CERTIFICATE_ID)).thenReturn(certificateProvider);

        // Act
        TestObserver<io.gravitee.am.gateway.certificate.CertificateProvider> testObserver =
                certificateManager.get(CERTIFICATE_ID).test();

        // Assert
        testObserver
                .assertComplete()
                .assertValue(certificateProvider);
    }

    @Test
    public void get_shouldReturnEmptyWhenCertificateBelongsToOtherDomain() {
        // Arrange - security test for domain association
        String otherDomainId = "other-domain-id";

        var certificateProvider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        when(certificateProvider.getDomain()).thenReturn(otherDomainId);
        when(certificateProviderManager.get(CERTIFICATE_ID)).thenReturn(certificateProvider);

        // Act
        TestObserver<io.gravitee.am.gateway.certificate.CertificateProvider> testObserver =
                certificateManager.get(CERTIFICATE_ID).test();

        // Assert
        testObserver
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void getCertificate_shouldReturnProviderWhenBelongsToDomain() {
        // Arrange
        var certificateProvider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        var internalProvider = mock(io.gravitee.am.certificate.api.CertificateProvider.class);
        when(certificateProvider.getDomain()).thenReturn(DOMAIN_ID);
        when(certificateProvider.getProvider()).thenReturn(internalProvider);
        when(certificateProviderManager.get(CERTIFICATE_ID)).thenReturn(certificateProvider);

        // Act
        io.gravitee.am.certificate.api.CertificateProvider result =
                certificateManager.getCertificate(CERTIFICATE_ID);

        // Assert
        Mockito.verify(certificateProvider).getDomain();
        Mockito.verify(certificateProvider).getProvider();
        assert(result == internalProvider);
    }

    @Test
    public void getCertificate_shouldReturnNullWhenBelongsToOtherDomain() {
        // Arrange
        String otherDomainId = "other-domain-id";

        var certificateProvider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        when(certificateProvider.getDomain()).thenReturn(otherDomainId);
        when(certificateProviderManager.get(CERTIFICATE_ID)).thenReturn(certificateProvider);

        // Act
        io.gravitee.am.certificate.api.CertificateProvider result =
                certificateManager.getCertificate(CERTIFICATE_ID);

        // Assert
        Mockito.verify(certificateProvider).getDomain();
        Mockito.verify(certificateProvider, never()).getProvider();
        assert(result == null);
    }

    @Test
    public void get_shouldReturnCertificateFromOtherDomain_whenDomainIsMaster() {
        // Arrange - Master domain (test-domain-id) accessing certificate from different domain (other-domain-id)
        String otherDomainId = "other-domain-id";
        when(domain.isMaster()).thenReturn(true);

        var certificateProvider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        lenient().when(certificateProvider.getDomain()).thenReturn(otherDomainId);
        when(certificateProviderManager.get(CERTIFICATE_ID)).thenReturn(certificateProvider);

        // Act
        TestObserver<io.gravitee.am.gateway.certificate.CertificateProvider> testObserver =
                certificateManager.get(CERTIFICATE_ID).test();

        // Assert
        testObserver
                .assertComplete()
                .assertValue(certificateProvider);
    }

    @Test
    public void getCertificate_shouldReturnProviderFromOtherDomain_whenDomainIsMaster() {
        // Arrange - Master domain (test-domain-id) accessing certificate from different domain
        when(domain.isMaster()).thenReturn(true);

        var certificateProvider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        var internalProvider = mock(io.gravitee.am.certificate.api.CertificateProvider.class);

        when(certificateProvider.getProvider()).thenReturn(internalProvider);
        when(certificateProviderManager.get(CERTIFICATE_ID)).thenReturn(certificateProvider);

        // Act
        io.gravitee.am.certificate.api.CertificateProvider result =
                certificateManager.getCertificate(CERTIFICATE_ID);

        // Assert
        Mockito.verify(certificateProvider, never()).getDomain();
        Mockito.verify(certificateProvider).getProvider();
        assert(result == internalProvider);
    }

    @Test
    public void fallbackCertificateProvider_shouldReturnProviderWhenSet() {
        // Arrange
        String fallbackCertificateId = "fallback-cert-id";
        CertificateSettings certificateSettings = new CertificateSettings();
        certificateSettings.setFallbackCertificate(fallbackCertificateId);
        when(domain.getCertificateSettings()).thenReturn(certificateSettings);

        var fallbackProvider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        when(fallbackProvider.getDomain()).thenReturn(DOMAIN_ID);
        when(certificateProviderManager.get(fallbackCertificateId)).thenReturn(fallbackProvider);

        ReflectionTestUtils.invokeMethod(certificateManager, "initCertificateSettings");

        // Act
        TestObserver<io.gravitee.am.gateway.certificate.CertificateProvider> testObserver =
                certificateManager.fallbackCertificateProvider().test();

        // Assert
        testObserver
                .assertComplete()
                .assertValue(fallbackProvider);
    }

    @Test
    public void fallbackCertificateProvider_shouldReturnEmptyWhenNotSet() {
        // Arrange

        // Act
        TestObserver<io.gravitee.am.gateway.certificate.CertificateProvider> testObserver =
                certificateManager.fallbackCertificateProvider().test();

        // Assert
        testObserver
                .assertComplete()
                .assertNoValues();
    }

    @Test
    public void certificateSettings_shouldBeUpdatedOnEvent() {
        // Arrange
        certificateManager.fallbackCertificateProvider().test()
                .assertComplete()
                .assertNoValues();

        String newFallbackCertificateId = "new-fallback-cert-id";
        CertificateSettings newCertificateSettings = new CertificateSettings();
        newCertificateSettings.setFallbackCertificate(newFallbackCertificateId);

        Domain updatedDomain = mock(Domain.class);
        when(updatedDomain.getCertificateSettings()).thenReturn(newCertificateSettings);
        when(domainRepository.findById(DOMAIN_ID)).thenReturn(Maybe.just(updatedDomain));

        var fallbackProvider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        when(fallbackProvider.getDomain()).thenReturn(DOMAIN_ID);
        when(certificateProviderManager.get(newFallbackCertificateId)).thenReturn(fallbackProvider);

        // Act
        Payload payload = new Payload(DOMAIN_ID, ReferenceType.DOMAIN, DOMAIN_ID, Action.UPDATE);
        certificateManager.getCertificateSettingsListener().onEvent(new io.gravitee.common.event.impl.SimpleEvent<>(DomainCertificateSettingsEvent.UPDATE, payload));

        // Assert
        Awaitility.await()
                .atMost(20, TimeUnit.SECONDS)
                .until(() -> certificateManager.fallbackCertificateProvider().blockingGet() == fallbackProvider);
    }
}
