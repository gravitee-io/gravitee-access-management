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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.provider;

import io.gravitee.am.model.oidc.Client;
import io.vertx.reactivex.core.http.HttpServerRequest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Client Authentication method : tls_client_auth
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientCertificateAuthProviderTest {

    private ClientCertificateAuthProvider authProvider = new ClientCertificateAuthProvider();

    @Test
    public void unauthorized_client_SSLPeerUnverifiedException() throws Exception {
        Client client = mock(Client.class);
        HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);
        SSLSession sslSession = mock(SSLSession.class);

        when(httpServerRequest.sslSession()).thenReturn(sslSession);
        when(sslSession.getPeerCertificates()).thenThrow(SSLPeerUnverifiedException.class);

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.handle(client, httpServerRequest, clientAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(clientAsyncResult);
            Assert.assertNotNull(clientAsyncResult.cause());
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void unauthorized_client_noMatchingDN() throws Exception {
        Client client = mock(Client.class);
        HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);
        SSLSession sslSession = mock(SSLSession.class);

        X509Certificate certificate = mock(X509Certificate.class);
        Principal subjectDN = mock(Principal.class);

        when(client.getTlsClientAuthSubjectDn()).thenReturn("CN=localhost, O=Invalid, C=US");
        when(subjectDN.getName()).thenReturn("CN=localhost, O=GraviteeSource, C=FR");
        when(certificate.getSubjectDN()).thenReturn(subjectDN);
        when(httpServerRequest.sslSession()).thenReturn(sslSession);
        when(sslSession.getPeerCertificates()).thenReturn(new Certificate[]{certificate});

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.handle(client, httpServerRequest, clientAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(clientAsyncResult);
            Assert.assertNotNull(clientAsyncResult.cause());
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void authorized_client() throws Exception {
        Client client = mock(Client.class);
        HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);
        SSLSession sslSession = mock(SSLSession.class);

        X509Certificate certificate = mock(X509Certificate.class);
        Principal subjectDN = mock(Principal.class);

        when(client.getTlsClientAuthSubjectDn()).thenReturn("CN=localhost, O=GraviteeSource, C=FR");
        when(subjectDN.getName()).thenReturn("CN=localhost, O=GraviteeSource, C=FR");
        when(certificate.getSubjectDN()).thenReturn(subjectDN);
        when(httpServerRequest.sslSession()).thenReturn(sslSession);
        when(sslSession.getPeerCertificates()).thenReturn(new Certificate[]{certificate});

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.handle(client, httpServerRequest, clientAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(clientAsyncResult);
            Assert.assertNotNull(clientAsyncResult.result());
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
