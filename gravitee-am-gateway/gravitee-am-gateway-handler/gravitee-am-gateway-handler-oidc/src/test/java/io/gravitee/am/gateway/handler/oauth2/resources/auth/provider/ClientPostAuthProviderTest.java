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

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.authentication.crypto.password.SHAPasswordEncoder;
import io.gravitee.am.service.impl.SecretService;
import io.gravitee.am.service.spring.application.SecretHashAlgorithm;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.List.of;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientPostAuthProviderTest {

    private ClientPostAuthProvider authProvider = new ClientPostAuthProvider(new SecretService());

    @Before
    public void init() {
        initMocks(this);
    }

    @Test
    public void shouldAuthenticateClient() throws Exception {
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-id");
        when(client.getClientSecret()).thenReturn("my-client-secret");

        HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);
        when(httpServerRequest.getParam(Parameters.CLIENT_ID)).thenReturn("my-client-id");
        when(httpServerRequest.getParam(Parameters.CLIENT_SECRET)).thenReturn("my-client-secret");

        RoutingContext context = mock(RoutingContext.class);
        when(context.request()).thenReturn(httpServerRequest);

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.handle(client, context, clientAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(clientAsyncResult);
            Assert.assertNotNull(clientAsyncResult.result());
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void shouldNotAuthenticateClient_badClientSecret() throws Exception {
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-id");
        when(client.getClientSecret()).thenReturn("my-client-secret");

        HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);
        when(httpServerRequest.getParam(Parameters.CLIENT_ID)).thenReturn("my-client-id");
        when(httpServerRequest.getParam(Parameters.CLIENT_SECRET)).thenReturn("my-other-client-secret");

        RoutingContext context = mock(RoutingContext.class);
        when(context.request()).thenReturn(httpServerRequest);

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.handle(client, context, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertTrue(userAsyncResult.failed());
            Assert.assertTrue(userAsyncResult.cause() instanceof InvalidClientException);
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }


    @Test
    public void shouldAuthenticateClient_hashedValue() throws Exception {
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-id");

        var sha512Encoder = new SHAPasswordEncoder(512);
        var hashedPassword = sha512Encoder.encode("my-client-secret");

        var clientSecret = new ClientSecret();
        clientSecret.setId(UUID.randomUUID().toString());
        clientSecret.setName(clientSecret.getId());
        clientSecret.setSecret(hashedPassword);
        clientSecret.setSettingsId(UUID.randomUUID().toString());
        clientSecret.setCreatedAt(new Date());

        var settings = new ApplicationSecretSettings(clientSecret.getSettingsId(), SecretHashAlgorithm.SHA_512.name(), Map.of());

        when(client.getSecretSettings()).thenReturn(of(settings));
        when(client.getClientSecrets()).thenReturn(of(clientSecret));

        HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);
        when(httpServerRequest.getParam(Parameters.CLIENT_ID)).thenReturn("my-client-id");
        when(httpServerRequest.getParam(Parameters.CLIENT_SECRET)).thenReturn("my-client-secret");

        RoutingContext context = mock(RoutingContext.class);
        when(context.request()).thenReturn(httpServerRequest);

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.handle(client, context, clientAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(clientAsyncResult);
            Assert.assertNotNull(clientAsyncResult.result());
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void shouldNotAuthenticateClient_hashedSecret_badClientSecret() throws Exception {
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-id");

        var sha512Encoder = new SHAPasswordEncoder(512);
        var hashedPassword = sha512Encoder.encode("my-client-secret");

        var clientSecret = new ClientSecret();
        clientSecret.setId(UUID.randomUUID().toString());
        clientSecret.setName(clientSecret.getId());
        clientSecret.setSecret(hashedPassword);
        clientSecret.setSettingsId(UUID.randomUUID().toString());
        clientSecret.setCreatedAt(new Date());

        var settings = new ApplicationSecretSettings(clientSecret.getSettingsId(), SecretHashAlgorithm.SHA_512.name(), Map.of());

        when(client.getSecretSettings()).thenReturn(of(settings));
        when(client.getClientSecrets()).thenReturn(of(clientSecret));

        HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);
        when(httpServerRequest.getParam(Parameters.CLIENT_ID)).thenReturn("my-client-id");
        when(httpServerRequest.getParam(Parameters.CLIENT_SECRET)).thenReturn("my-other-client-secret");

        RoutingContext context = mock(RoutingContext.class);
        when(context.request()).thenReturn(httpServerRequest);

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.handle(client, context, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertTrue(userAsyncResult.failed());
            Assert.assertTrue(userAsyncResult.cause() instanceof InvalidClientException);
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
