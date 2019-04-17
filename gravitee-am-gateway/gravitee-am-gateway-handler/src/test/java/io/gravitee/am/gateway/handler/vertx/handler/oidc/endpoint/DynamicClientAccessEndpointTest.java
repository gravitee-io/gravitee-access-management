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
package io.gravitee.am.gateway.handler.vertx.handler.oidc.endpoint;

import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oidc.clientregistration.DynamicClientRegistrationService;
import io.gravitee.am.gateway.handler.oidc.request.DynamicClientRegistrationRequest;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.am.model.Client;
import io.gravitee.am.service.ClientService;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicClientAccessEndpointTest extends RxWebTestBase {

    @Mock
    private ClientService clientService;

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private DynamicClientRegistrationService dcrService;

    @InjectMocks
    DynamicClientAccessEndpoint endpoint = new DynamicClientAccessEndpoint(dcrService,clientService,clientSyncService);

    @Override
    public void setUp() throws Exception{
        super.setUp();

        router.route(HttpMethod.GET, "/register/:client_id").handler(endpoint::read);
        router.route(HttpMethod.PATCH, "/register/:client_id").handler(endpoint::patch);
        router.route(HttpMethod.PUT, "/register/:client_id").handler(endpoint::update);
        router.route(HttpMethod.DELETE, "/register/:client_id").handler(endpoint::delete);
        router.route().failureHandler(new ExceptionHandler());

        Client client = new Client();
        client.setId("my-test-client_id");
        client.setClientId("my-test-client_id");

        when(clientSyncService.findByDomainAndClientId(null,"my-test-client_id")).thenReturn(Maybe.just(client));
    }

    @Test
    public void unknownClient() throws Exception{
        when(clientSyncService.findByDomainAndClientId(null,"unknown-client_id")).thenReturn(Maybe.empty());

        testRequest(
                HttpMethod.GET, "/register/unknown-client_id",
                HttpStatusCode.NOT_FOUND_404, "Not Found");
    }

    @Test
    public void read() throws Exception{
        testRequest(
                HttpMethod.GET, "/register/my-test-client_id",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void delete() throws Exception{
        when(clientService.delete("my-test-client_id")).thenReturn(Completable.complete());
        when(clientSyncService.removeDynamicClientRegistred(any())).thenReturn(new Client());

        testRequest(
                HttpMethod.DELETE, "/register/my-test-client_id",
                HttpStatusCode.NO_CONTENT_204, "No Content");
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

    /*TODO: Implement valid case
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
