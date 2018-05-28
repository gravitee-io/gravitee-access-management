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

import io.gravitee.am.gateway.handler.oauth2.approval.ApprovalService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.impl.DefaultAccessToken;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.oauth2.endpoint.authorization.AuthorizationEndpointFailureHandler;
import io.gravitee.am.gateway.handler.vertx.oauth2.endpoint.authorization.AuthorizationEndpointHandler;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.SessionHandler;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationEndpointHandlerTest  extends RxWebTestBase {

    @Mock
    private AuthorizationCodeService authorizationCodeService;

    @Mock
    private TokenGranter tokenGranter;

    @Mock
    private ClientService clientService;

    @Mock
    private ApprovalService approvalService;

    @Mock
    private Domain domain;

    @InjectMocks
    private AuthorizationEndpointHandler authorizationEndpointHandler =
            new AuthorizationEndpointHandler(authorizationCodeService, tokenGranter, clientService, approvalService, domain);

    @Override
    public void setUp() throws Exception {
        super.setUp();
        SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx));
        router.route("/oauth/authorize").handler(sessionHandler);
        router.route(HttpMethod.GET, "/oauth/authorize").handler(authorizationEndpointHandler);
        router.route().failureHandler(new AuthorizationEndpointFailureHandler(domain, clientService));
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_noUser_noRedirectUri() throws Exception {
        when(domain.getPath()).thenReturn("test");
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("/test/oauth/error", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_noUser_withRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=access_denied", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_invalidScope() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=invalid_scope&error_description=Empty+scope+%2528either+the+client+or+the+user+is+not+allowed+the+requested+scopes%2529", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_mismatchRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/authorize/callback"));

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/authorize/callback?error=redirect_uri_mismatch&error_description=The+redirect_uri+MUST+match+the+registered+callback+URL+for+this+application", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_approvalPage() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(false);

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(approvalService.checkApproval(any(), any(), any())).thenReturn(Single.just(authorizationRequest));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.startsWith("/test/oauth/confirm_access"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_responseTypeCode() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(OAuth2Constants.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationCode code = new AuthorizationCode();
        code.setCode("test-code");

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(approvalService.checkApproval(any(), any(), any())).thenReturn(Single.just(authorizationRequest));
        when(authorizationCodeService.create(any(), any())).thenReturn(Single.just(code));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?code=test-code", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_responseTypeToken() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(OAuth2Constants.TOKEN);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AccessToken accessToken = new DefaultAccessToken("token");

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(approvalService.checkApproval(any(), any(), any())).thenReturn(Single.just(authorizationRequest));
        when(tokenGranter.grant(any(), any())).thenReturn(Single.just(accessToken));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=token&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback#access_token=token&token_type=bearer&expires_in=0", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

}
