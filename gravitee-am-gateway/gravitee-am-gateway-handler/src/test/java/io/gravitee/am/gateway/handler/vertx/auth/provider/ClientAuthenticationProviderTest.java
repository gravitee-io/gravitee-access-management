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
package io.gravitee.am.gateway.handler.vertx.auth.provider;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.BadClientCredentialsException;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.model.Client;
import io.reactivex.Maybe;
import io.vertx.core.json.JsonObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientAuthenticationProviderTest {

    private ClientAuthenticationProvider authProvider = new ClientAuthenticationProvider();

    @Mock
    private ClientService clientService;

    @Before
    public void init() {
        initMocks(this);
        authProvider.setClientService(clientService);
    }

    @Test
    public void shouldAuthenticateClient() throws Exception {
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-id");
        when(client.getClientSecret()).thenReturn("my-client-secret");

        when(clientService.findByClientId("my-client-id")).thenReturn(Maybe.just(client));
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-client-id");
        credentials.put("password", "my-client-secret");

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(credentials, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertNotNull(userAsyncResult.result());
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void shouldNotAuthenticateClient_badClientSecret() throws Exception {
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn("my-client-id");
        when(client.getClientSecret()).thenReturn("my-client-secret");

        when(clientService.findByClientId("my-client-id")).thenReturn(Maybe.just(client));
        JsonObject credentials = new JsonObject();
        credentials.put("username", "my-client-id");
        credentials.put("password", "my-other-client-secret");

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(credentials, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertTrue(userAsyncResult.failed());
            Assert.assertTrue(userAsyncResult.cause() instanceof BadClientCredentialsException);
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void shouldNotAuthenticateClient_unknownClient() throws Exception {
        when(clientService.findByClientId(anyString())).thenReturn(Maybe.empty());
        JsonObject credentials = new JsonObject();
        credentials.put(OAuth2Constants.CLIENT_ID, "other-client-id");
        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(credentials, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertTrue(userAsyncResult.failed());
            Assert.assertTrue(userAsyncResult.cause() instanceof BadClientCredentialsException);
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void shouldNotAuthenticateClient_exception() throws Exception {
        Exception exception = new Exception();
        when(clientService.findByClientId(anyString())).thenReturn(Maybe.error(exception));
        JsonObject credentials = new JsonObject();
        credentials.put(OAuth2Constants.CLIENT_ID, "other-client-id");
        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(credentials, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertTrue(userAsyncResult.failed());
            Assert.assertEquals(exception, userAsyncResult.cause());
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
