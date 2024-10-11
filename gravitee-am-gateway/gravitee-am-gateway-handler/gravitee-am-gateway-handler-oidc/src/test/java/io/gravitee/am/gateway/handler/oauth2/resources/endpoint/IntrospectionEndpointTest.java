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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint;

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.introspection.IntrospectionEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionResponse;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.RefreshToken;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
// TODO : test valid use cases
public class IntrospectionEndpointTest extends RxWebTestBase {

    @InjectMocks
    private IntrospectionEndpoint introspectionEndpointHandler = new IntrospectionEndpoint();

    @Mock
    private IntrospectionService introspectionService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.POST, "/oauth/introspect")
                .handler(introspectionEndpointHandler);
        router.route().failureHandler(new ExceptionHandler());
    }

    @Test
    public void shouldNotInvokeEndpoint_noClient() throws Exception {
        testRequest(
                HttpMethod.POST, "/oauth/introspect",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldReturnInvalidToken_noTokenProvided() throws Exception {
        io.gravitee.am.model.oidc.Client client = new io.gravitee.am.model.oidc.Client();
        client.setClientId("my-client-id");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                "/oauth/introspect",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldIntrospectAccessTokenByDefault() throws Exception {
        io.gravitee.am.model.oidc.Client client = new io.gravitee.am.model.oidc.Client();
        client.setClientId("my-client-id");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.next();
        });

        Mockito.when(introspectionService.introspect(Mockito.argThat(req -> req.getToken().equals("accesstoken"))))
                        .thenReturn(Single.just(new IntrospectionResponse()));

        testRequest(
                HttpMethod.POST,
                "/oauth/introspect?token=accesstoken",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldIntrospectRefreshTokenWhenHintIsProvided() throws Exception {
        io.gravitee.am.model.oidc.Client client = new io.gravitee.am.model.oidc.Client();
        client.setClientId("my-client-id");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.next();
        });

        Mockito.when(introspectionService.introspect(Mockito.argThat(req -> req.getToken().equals("refreshtoken"))))
                .thenReturn(Single.just(new IntrospectionResponse()));

        testRequest(
                HttpMethod.POST,
                "/oauth/introspect?token=refreshtoken&token_type_hint=refresh_token",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldOmitUnknownTokenHintType() throws Exception {
        io.gravitee.am.model.oidc.Client client = new io.gravitee.am.model.oidc.Client();
        client.setClientId("my-client-id");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.next();
        });

        Mockito.when(introspectionService.introspect(Mockito.argThat(req -> req.getToken().equals("accesstoken"))))
                .thenReturn(Single.just(new IntrospectionResponse()));

        testRequest(
                HttpMethod.POST,
                "/oauth/introspect?token=accesstoken&token_type_hint=unknown",
                HttpStatusCode.OK_200, "OK");
    }



}
