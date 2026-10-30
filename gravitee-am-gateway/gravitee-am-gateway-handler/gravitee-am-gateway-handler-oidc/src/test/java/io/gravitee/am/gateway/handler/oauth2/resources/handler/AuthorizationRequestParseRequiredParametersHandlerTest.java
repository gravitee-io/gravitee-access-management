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
package io.gravitee.am.gateway.handler.oauth2.resources.handler;

import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseRequiredParametersHandler;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Session;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationRequestParseRequiredParametersHandlerTest extends RxWebTestBase {

    @Mock
    private Session session;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(routingContext -> {
                    ((io.vertx.ext.web.impl.RoutingContextInternal) routingContext.getDelegate()).setSession(session);
                    OpenIDProviderMetadata metadata = new OpenIDProviderMetadata();
                    metadata.setResponseTypesSupported(List.of(ResponseType.CODE));
                    routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, metadata);
                    routingContext.next();
                })
                .handler(new AuthorizationRequestParseRequiredParametersHandler())
                .handler(rc -> rc.response().end())
                .failureHandler(rc -> rc.response().setStatusCode(400).end());
    }

    @Test
    public void shouldRejectRequestWithoutResponseType() throws Exception {
        testRequest(HttpMethod.GET, "/oauth/authorize?client_id=client-id",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldAcceptDeviceFlowWithoutResponseType() throws Exception {
        markDeviceFlow();

        testRequest(HttpMethod.GET, "/oauth/authorize?client_id=client-id",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldStillRequireClientIdForDeviceFlow() throws Exception {
        markDeviceFlow();

        testRequest(HttpMethod.GET, "/oauth/authorize",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldNotRelaxValidationWhenTheMarkerBelongsToAnotherClient() throws Exception {
        markDeviceFlow();

        testRequest(HttpMethod.GET, "/oauth/authorize?client_id=another-client",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    private void markDeviceFlow() {
        Mockito.when(session.get(ConstantKeys.DEVICE_FLOW_CLIENT_ID_KEY)).thenReturn("client-id");
        Mockito.lenient().when(session.get(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY)).thenReturn("device-code");
    }
}
