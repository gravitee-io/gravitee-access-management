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
package io.gravitee.am.gateway.handler.vertx.endpoint;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.auth.user.Client;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.token.TokenEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.token.TokenRequestParseHandler;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class TokenEndpointHandlerTest extends RxWebTestBase {

    @InjectMocks
    private TokenEndpointHandler tokenEndpointHandler = new TokenEndpointHandler();

    @Mock
    private TokenGranter tokenGranter;

    @Mock
    private ClientService clientService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.POST, "/oauth/token")
                .handler(new TokenRequestParseHandler())
                .handler(tokenEndpointHandler);
        router.route().failureHandler(new ExceptionHandler());
    }

    @Test
    public void shouldNotInvokeTokenEndpoint_emptyScope() throws Exception {
        testRequest(
                HttpMethod.POST, "/oauth/token?grant_type=client_credentials&scope=",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldNotInvokeTokenEndpoint_noClient() throws Exception {
        testRequest(
                HttpMethod.POST, "/oauth/token?grant_type=client_credentials",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldNotInvokeTokenEndpoint_invalidClient() throws Exception {
        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new AbstractUser() {
                    @Override
                    protected void doIsPermitted(String s, Handler<AsyncResult<Boolean>> handler) {

                    }

                    @Override
                    public JsonObject principal() {
                        return null;
                    }

                    @Override
                    public void setAuthProvider(AuthProvider authProvider) {

                    }
                }));
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.POST, "/oauth/token?grant_type=client_credentials",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withValidClientCredentials_noGrantType() throws Exception {
        io.gravitee.am.model.Client client = new io.gravitee.am.model.Client();
        client.setClientId("my-client-id");

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client(client)));
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withInvalidClientCredentials() throws Exception {
        io.gravitee.am.model.Client client = new io.gravitee.am.model.Client();
        client.setClientId("other-client-id");

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client(client)));
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret&grant_type=client_credentials",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withValidClientCredentials() throws Exception {
        io.gravitee.am.model.Client client = new io.gravitee.am.model.Client();
        client.setClientId("my-client");
        client.setScopes(Collections.singletonList("read"));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client(client)));
                routingContext.next();
            }
        });

        // Jackson is unable to generate a JSON from a mocked interface.
        AccessToken accessToken = new AccessToken() {
            @Override
            public String getValue() {
                return null;
            }

            @Override
            public String getTokenType() {
                return null;
            }

            @Override
            public int getExpiresIn() {
                return 0;
            }

            @Override
            public String getRefreshToken() {
                return null;
            }

            @Override
            public String getScope() {
                return null;
            }

            @Override
            public Map<String, Object> getAdditionalInformation() {
                return null;
            }
        };

        when(clientService.findByClientId(any())).thenReturn(Maybe.just(client));
        when(tokenGranter.grant(any(TokenRequest.class), any(io.gravitee.am.model.Client.class))).thenReturn(Single.just(accessToken));

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret&grant_type=client_credentials",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withValidClientCredentials_noAccessToken() throws Exception {
        io.gravitee.am.model.Client client = new io.gravitee.am.model.Client();
        client.setClientId("my-client");
        client.setScopes(Collections.singletonList("read"));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client(client)));
                routingContext.next();
            }
        });

        when(tokenGranter.grant(any(TokenRequest.class), any(io.gravitee.am.model.Client.class))).thenReturn(Single.error(new Exception()));

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret&grant_type=client_credentials",
                HttpStatusCode.INTERNAL_SERVER_ERROR_500, "Internal Server Error");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withValidClientCredentials_withoutGrantType() throws Exception {
        io.gravitee.am.model.Client client = new io.gravitee.am.model.Client();
        client.setClientId("my-client");
        client.setAuthorizedGrantTypes(null);

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client(client)));
                routingContext.next();
            }
        });

        when(tokenGranter.grant(any(TokenRequest.class), any(io.gravitee.am.model.Client.class))).thenReturn(Single.error(new Exception()));

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret&grant_type=client_credentials",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }
}
