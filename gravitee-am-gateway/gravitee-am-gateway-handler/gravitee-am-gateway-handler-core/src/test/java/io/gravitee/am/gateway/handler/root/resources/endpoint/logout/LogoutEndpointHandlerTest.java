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
package io.gravitee.am.gateway.handler.root.resources.endpoint.logout;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.resources.handler.error.ErrorHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.TokenService;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LogoutEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private Domain domain;
    @Mock
    private TokenService tokenService;
    @Mock
    private AuditService auditService;
    @Mock
    private ClientSyncService clientSyncService;
    @Mock
    private JWTService jwtService;
    @Mock
    private AuthenticationFlowContextService authenticationFlowContextService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/logout")
                .handler(new LogoutEndpoint(domain, tokenService, auditService, clientSyncService, jwtService, authenticationFlowContextService))
                .failureHandler(new ErrorHandler("/error"));
    }

    @Test
    public void shouldInvokeLogoutEndpoint_noTargetUrl() throws Exception {
        testRequest(
                HttpMethod.GET, "/logout",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("/"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_noClient() throws Exception {
        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://test"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_client_noRestriction() throws Exception {
        Client client = mock(Client.class);
        when(client.getPostLogoutRedirectUris()).thenReturn(null);
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://test"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_client_restriction() throws Exception {
        Client client = mock(Client.class);
        when(client.getPostLogoutRedirectUris()).thenReturn(Arrays.asList("https://test"));
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://test"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_client_restriction_2() throws Exception {
        Client client = mock(Client.class);
        when(client.getPostLogoutRedirectUris()).thenReturn(Arrays.asList("https://test", "https://dev"));
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://test"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_client_restriction_mismatch() throws Exception {
        Client client = new Client();
        client.setClientId("client-id");
        client.setPostLogoutRedirectUris(Arrays.asList("https://dev"));
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/error?client_id=client-id&error=invalid_request&error_description=The+post_logout_redirect_uri+MUST+match+the+registered+callback+URLs"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_client_id_token_restriction_mismatch() throws Exception {
        JWT jwt = new JWT();
        jwt.setAud("client-id");

        Client client = new Client();
        client.setClientId("client-id");
        client.setPostLogoutRedirectUris(Arrays.asList("https://dev"));
        when(jwtService.decode("idToken")).thenReturn(Single.just(jwt));
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify("idToken", client)).thenReturn(Single.just(jwt));

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?post_logout_redirect_uri=https%3A%2F%2Ftest&id_token_hint=idToken",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/error?client_id=client-id&error=invalid_request&error_description=The+post_logout_redirect_uri+MUST+match+the+registered+callback+URLs"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_noClient_restriction_mismatch() throws Exception {
        OIDCSettings oidcSettings = mock(OIDCSettings.class);
        when(oidcSettings.getPostLogoutRedirectUris()).thenReturn(Arrays.asList("https://dev"));
        when(domain.getOidc()).thenReturn(oidcSettings);

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/error?error=invalid_request&error_description=The+post_logout_redirect_uri+MUST+match+the+registered+callback+URLs"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    // see https://github.com/gravitee-io/issues/issues/5163
    @Test
    public void shouldInvokeLogoutEndpoint_postRedirectUri_lax_id_token_hint() throws Exception {
        OIDCSettings oidcSettings = mock(OIDCSettings.class);
        when(oidcSettings.getPostLogoutRedirectUris()).thenReturn(Arrays.asList("https://dev"));
        when(domain.getOidc()).thenReturn(oidcSettings);

        testRequest(
                HttpMethod.GET, "/logout?post_logout_redirect_uri=https%3A%2F%2Fdev",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://dev"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
