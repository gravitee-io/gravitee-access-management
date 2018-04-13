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

import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.auth.user.Client;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.common.http.HttpStatusCode;
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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
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

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.POST, "/oauth/token").handler(tokenEndpointHandler);
        router.route().failureHandler(new ExceptionHandler());
    }

    @Test
    public void shouldNotInvokeTokenEndpoint_noClient() throws Exception {
        testRequest(
                HttpMethod.POST, "/oauth/token",
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
                HttpMethod.POST, "/oauth/token",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withValidClientCredentials_noGrantType() throws Exception {
        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client("my-client")));
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withInvalidClientCredentials() throws Exception {
        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client("other-client-id")));
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret&grant_type=client_credentials",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withValidClientCredentials() throws Exception {
        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client("my-client")));
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
        };

        when(tokenGranter.grant(any(TokenRequest.class))).thenReturn(Single.just(accessToken));

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret&grant_type=client_credentials",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withValidClientCredentials_noAccessToken() throws Exception {
        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new Client("my-client")));
                routingContext.next();
            }
        });

        when(tokenGranter.grant(any(TokenRequest.class))).thenReturn(Single.error(new Exception()));

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret&grant_type=client_credentials",
                HttpStatusCode.INTERNAL_SERVER_ERROR_500, "Internal Server Error");
    }
}
