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
import io.gravitee.am.common.exception.oauth2.TemporarilyUnavailableException;
import io.gravitee.am.gateway.certificate.CertificateProviderManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
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
}
