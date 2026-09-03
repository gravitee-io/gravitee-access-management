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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestStatus;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DeviceAuthorizationEndpointTest extends RxWebTestBase {

    @Mock
    private DeviceAuthorizationRequestService requestService;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        router.route(HttpMethod.POST, "/oauth/device_authorization")
                .handler(new DeviceAuthorizationEndpoint(requestService, new DeviceVerificationUriResolver()))
                .failureHandler(rc -> rc.response().setStatusCode(400).end());
    }

    @Test
    public void shouldReturnTheDeviceAuthorizationFields() throws Exception {
        withClient(GrantType.DEVICE_CODE);
        when(requestService.register(any(), any())).thenReturn(Single.just(storedRequest()));
        when(requestService.getDeviceCodeExpiryInSec(any())).thenReturn(600);
        when(requestService.getPollingIntervalInSec(any())).thenReturn(5);

        testRequest(HttpMethod.POST, "/oauth/device_authorization?scope=openid", null,
                resp -> resp.bodyHandler(body -> {
                    JsonObject json = new JsonObject(body.toString());
                    assertEquals("device-code", json.getString("device_code"));
                    assertEquals("BCDF-GHJK", json.getString("user_code"));
                    assertEquals(Integer.valueOf(600), json.getInteger("expires_in"));
                    assertEquals(Integer.valueOf(5), json.getInteger("interval"));
                    assertTrue(json.getString("verification_uri").endsWith("/oauth/device?client_id=client-id"));
                }), HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldEmbedTheDisplayedUserCodeInTheCompleteVerificationUri() throws Exception {
        withClient(GrantType.DEVICE_CODE);
        when(requestService.register(any(), any())).thenReturn(Single.just(storedRequest()));

        testRequest(HttpMethod.POST, "/oauth/device_authorization?scope=openid", null,
                resp -> resp.bodyHandler(body -> {
                    JsonObject json = new JsonObject(body.toString());
                    String complete = json.getString("verification_uri_complete");
                    assertTrue(complete.startsWith(json.getString("verification_uri")));
                    assertTrue(complete.contains("user_code=BCDF-GHJK"));
                }), HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldRejectClientWithoutDeviceCodeGrant() throws Exception {
        withClient(GrantType.AUTHORIZATION_CODE);

        testRequest(HttpMethod.POST, "/oauth/device_authorization", 400, "Bad Request");
    }

    private void withClient(String grantType) {
        Client client = new Client();
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(grantType));
        router.route().order(-1).handler(context -> {
            context.put(CLIENT_CONTEXT_KEY, client);
            context.put(CONTEXT_PATH, "");
            context.next();
        });
    }

    private DeviceAuthorizationRequest storedRequest() {
        DeviceAuthorizationRequest request = new DeviceAuthorizationRequest();
        request.setId("device-code");
        request.setUserCode("BCDFGHJK");
        request.setClientId("client-id");
        request.setStatus(DeviceAuthorizationRequestStatus.PENDING.name());
        request.setScopes(Set.of("openid"));
        request.setCreatedAt(new Date());
        request.setLastAccessAt(new Date());
        request.setExpireAt(new Date());
        return request;
    }
}
