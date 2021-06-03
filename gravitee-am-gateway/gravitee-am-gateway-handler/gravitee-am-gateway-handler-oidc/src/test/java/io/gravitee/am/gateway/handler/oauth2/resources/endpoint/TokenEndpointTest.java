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

import io.gravitee.am.common.exception.uma.UmaException;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.token.TokenEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.token.TokenRequestParseHandler;
import io.gravitee.am.gateway.handler.oauth2.service.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.authorization.Authorization;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class TokenEndpointTest extends RxWebTestBase {

    @InjectMocks
    private TokenEndpoint tokenEndpointHandler = new TokenEndpoint();

    @Mock
    private TokenGranter tokenGranter;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.POST, "/oauth/token")
                .handler(new TokenRequestParseHandler())
                .handler(tokenEndpointHandler);
        router.route().failureHandler(new ExceptionHandler());
    }

    @Test
    public void shouldInvokeTokenEndpoint_emptyScope() throws Exception {
        testRequest(
                HttpMethod.POST, "/oauth/token?grant_type=client_credentials&scope=",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
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
                    public JsonObject attributes() {
                        return null;
                    }

                    @Override
                    public io.vertx.ext.auth.User isAuthorized(Authorization authority, Handler<AsyncResult<Boolean>> resultHandler) {
                        return null;
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
    public void shouldNotInvokeAuthorizationEndpoint_duplicateParameters() throws Exception {
        testRequest(
                HttpMethod.POST, "/oauth/token?grant_type=client_credentials&grant_type=client_credentials",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withValidClientCredentials_noGrantType() throws Exception {
        io.gravitee.am.model.oidc.Client client = new io.gravitee.am.model.oidc.Client();
        client.setClientId("my-client-id");

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.put("client", new Client());
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withInvalidClientCredentials() throws Exception {
        Client client = new Client();
        client.setClientId("other-client-id");

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.put("client", client);
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret&grant_type=client_credentials",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withValidClientCredentials() throws Exception {
        Client client = new Client();
        client.setClientId("my-client");
        client.setScopes(Collections.singletonList("read"));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.put("client", client);
                routingContext.next();
            }
        });

        // Jackson is unable to generate a JSON from a mocked interface.
        Token accessToken = new AccessToken("my-token");

        when(tokenGranter.grant(any(TokenRequest.class), any(io.gravitee.am.model.oidc.Client.class))).thenReturn(Single.just(accessToken));

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret&grant_type=client_credentials",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withValidClientCredentials_noAccessToken() throws Exception {
        Client client = new Client();
        client.setClientId("my-client");
        client.setScopes(Collections.singletonList("read"));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.put("client", client);
                routingContext.next();
            }
        });

        when(tokenGranter.grant(any(TokenRequest.class), any(io.gravitee.am.model.oidc.Client.class))).thenReturn(Single.error(new Exception()));

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret&grant_type=client_credentials",
                HttpStatusCode.INTERNAL_SERVER_ERROR_500, "Internal Server Error");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withValidClientCredentials_withoutGrantType() throws Exception {
        Client client = new Client();
        client.setClientId("my-client");
        client.setAuthorizedGrantTypes(null);

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.put("client", client);
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret&grant_type=client_credentials",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void testInvokeTokenEndpoint_umaException() throws Exception{
        Client client = new Client();
        client.setClientId("my-client");
        client.setAuthorizedGrantTypes(Arrays.asList(GrantType.UMA));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.next();
        });

        when(tokenGranter.grant(any(TokenRequest.class), any(io.gravitee.am.model.oidc.Client.class))).thenReturn(Single.error(UmaException.requestDeniedBuilder().build()));

        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret&grant_type=urn:ietf:params:oauth:grant-type:uma-ticket",
                HttpStatusCode.FORBIDDEN_403, "Forbidden");
    }
}
