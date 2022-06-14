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
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.resources.handler.error.ErrorHandler;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LogoutCallbackEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private Domain domain;
    @Mock
    private ClientSyncService clientSyncService;
    @Mock
    private JWTService jwtService;
    @Mock
    private UserService userService;
    @Mock
    private AuthenticationFlowContextService authenticationFlowContextService;
    @Mock
    private CertificateManager certificateManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/logout/callback")
                .handler(new LogoutCallbackEndpoint(domain, clientSyncService, jwtService, userService, authenticationFlowContextService, certificateManager))
                .failureHandler(new ErrorHandler("/error"));
    }

    @Test
    public void shouldRedirect_using_state() throws Exception {
        JWT state = new JWT();
        state.put("q", "post_logout_redirect_uri=http://my-app&state=myappstate");
        state.put("p", "provider-id");
        state.put("c", "client-id");
        when(certificateManager.defaultCertificateProvider()).thenReturn(mock(CertificateProvider.class));
        when(jwtService.decodeAndVerify(any(String.class), any(CertificateProvider.class))).thenReturn(Single.just(state));
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(new Client()));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout/callback?state=myappstate",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("http://my-app?state=myappstate"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirect_using_state_invalidTargetUrl() throws Exception {
        Client client = new Client();
        client.setClientId("client-id");
        client.setPostLogoutRedirectUris(Arrays.asList("https://my-app"));
        client.setSingleSignOut(true);

        JWT state = new JWT();
        state.put("q", "post_logout_redirect_uri=http://my-invalid-app&state=myappstate");
        state.put("p", "provider-id");
        state.put("c", "client-id");
        when(certificateManager.defaultCertificateProvider()).thenReturn(mock(CertificateProvider.class));
        when(jwtService.decodeAndVerify(any(String.class), any(CertificateProvider.class))).thenReturn(Single.just(state));
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout/callback?state=myappstate",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/error?state=myappstate&client_id=client-id&error=invalid_request&error_description=The+post_logout_redirect_uri+MUST+match+the+registered+callback+URLs"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
