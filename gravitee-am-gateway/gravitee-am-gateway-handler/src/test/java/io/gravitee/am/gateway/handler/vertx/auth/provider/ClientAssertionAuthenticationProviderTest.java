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

import io.gravitee.am.gateway.handler.oauth2.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.model.Client;
import io.reactivex.Maybe;
import io.vertx.core.json.JsonObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientAssertionAuthenticationProviderTest {

    private final static String CLIENT_ID = "my-client";
    private final static String CLIENT_SECRET = "my-secret";

    private ClientAssertionAuthenticationProvider authProvider = new ClientAssertionAuthenticationProvider();

    @Mock
    private ClientAssertionService clientAssertionService;

    @Before
    public void init() {
        initMocks(this);
        authProvider.setClientAssertionService(clientAssertionService);
    }

    @Test
    public void authorized_client() throws Exception {
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn(CLIENT_ID);
        when(client.getClientSecret()).thenReturn(CLIENT_SECRET);

        when(clientAssertionService.assertClient(any(),any(),any())).thenReturn(Maybe.just(client));
        JsonObject credentials = new JsonObject();
        credentials.put("client_assertion_type", "unknown");
        credentials.put("client_assertion", "dummy");

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(credentials, userAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(userAsyncResult);
            Assert.assertNotNull(userAsyncResult.result());
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }


    @Test
    public void unauthorized_invalidClient_assertion_type() throws Exception {
        when(clientAssertionService.assertClient(any(),any(),any())).thenReturn(Maybe.error(new InvalidClientException("Unknown or unsupported assertion_type")));

        JsonObject credentials = new JsonObject();
        credentials.put("client_assertion_type", "unknown");
        credentials.put("client_assertion", "dummy");

        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(credentials, clientAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(clientAsyncResult);
            Assert.assertTrue(clientAsyncResult.failed());
            Assert.assertTrue(clientAsyncResult.cause() instanceof InvalidClientException);
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void unauthorized_invalidClient_clientDoesNotMatch() throws Exception {

        Client client = Mockito.mock(Client.class);
        when(client.getClientId()).thenReturn(CLIENT_ID);
        when(clientAssertionService.assertClient(any(),any(),any())).thenReturn(Maybe.just(client));

        JsonObject credentials = new JsonObject();
        credentials.put("client_assertion_type", "unknown");
        credentials.put("client_assertion", "dummy");
        credentials.put("client_id", "notMatching");


        CountDownLatch latch = new CountDownLatch(1);
        authProvider.authenticate(credentials, clientAsyncResult -> {
            latch.countDown();
            Assert.assertNotNull(clientAsyncResult);
            Assert.assertTrue(clientAsyncResult.failed());
            Assert.assertTrue(clientAsyncResult.cause() instanceof InvalidClientException);
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
