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
package io.gravitee.am.gateway.handler.oidc.resources.endpoint;

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicClientAccessEndpointTest extends RxWebTestBase {

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private DynamicClientRegistrationService dcrService;

    @InjectMocks
    DynamicClientAccessEndpoint endpoint = new DynamicClientAccessEndpoint(dcrService, clientSyncService);

    @Override
    public void setUp() throws Exception{
        super.setUp();

        router.route(HttpMethod.GET, "/register/:client_id").handler(endpoint::read);
        router.route(HttpMethod.PATCH, "/register/:client_id").handler(endpoint::patch);
        router.route(HttpMethod.PUT, "/register/:client_id").handler(endpoint::update);
        router.route(HttpMethod.DELETE, "/register/:client_id").handler(endpoint::delete);
        router.route(HttpMethod.POST, "/register/:client_id/renew_secret").handler(endpoint::renewClientSecret);
        router.route().failureHandler(new ExceptionHandler());

        Client client = new Client();
        client.setId("my-test-client_id");
        client.setClientId("my-test-client_id");

        when(clientSyncService.findByClientId("my-test-client_id")).thenReturn(Maybe.just(client));
        when(clientSyncService.findByClientId("unknown")).thenReturn(Maybe.empty());
    }

    @Test
    public void read() throws Exception{
        testRequest(
                HttpMethod.GET, "/register/my-test-client_id",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void read_notFound() throws Exception{
        testRequest(
                HttpMethod.GET, "/register/unknown",
                HttpStatusCode.NOT_FOUND_404, "Not Found");
    }

    @Test
    public void delete() throws Exception{
        when(dcrService.delete(any())).thenReturn(Single.just(new Client()));
        when(clientSyncService.removeDynamicClientRegistred(any())).thenReturn(new Client());

        testRequest(
                HttpMethod.DELETE, "/register/my-test-client_id",
                HttpStatusCode.NO_CONTENT_204, "No Content");
    }

    @Test
    public void delete_notFound() throws Exception{
        testRequest(
                HttpMethod.DELETE, "/register/unknown",
                HttpStatusCode.NOT_FOUND_404, "Not Found");
    }

    @Test
    public void renewClientSecret() throws Exception{
        when(dcrService.renewSecret(any(), any())).thenReturn(Single.just(new Client()));
        when(clientSyncService.addDynamicClientRegistred(any())).thenReturn(new Client());

        testRequest(
                HttpMethod.POST, "/register/my-test-client_id/renew_secret",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void renewClientSecret_notFound() throws Exception{
        testRequest(
                HttpMethod.POST, "/register/unknown/renew_secret",
                HttpStatusCode.NOT_FOUND_404, "Not Found");
    }

    @Test
    public void patch_noBody() throws Exception{
        String body = "{\"redirect_uris\": [\"https://redirecturi\"]}";
        testRequest(
                HttpMethod.PATCH, "/register/my-test-client_id",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void update_noBody() throws Exception{
        String body = "{\"redirect_uris\": [\"https://redirecturi\"]}";
        testRequest(
                HttpMethod.PUT, "/register/my-test-client_id",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    /* TODO: Implement valid case
    @Test
    public void patch() throws Exception{
        when(dcrService.validateClientRegistrationRequest(any())).thenReturn(Single.just(new DynamicClientRegistrationRequest()));
        when(clientService.update(any())).thenReturn(Single.just(new Client()));
        when(clientSyncService.addDynamicClientRegistred(any())).thenReturn(new Client());

        String body = "{\"redirect_uris\": [\"https://redirecturi\"]}";
        testRequest(
                HttpMethod.PATCH, "/register/my-test-client_id",
                req -> {
                    req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                    req.putHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(body.length()));
                    req.setChunked(false);
                    req.write(body);
                },
                HttpStatusCode.OK_200, "OK", null);
    }
    */
}
