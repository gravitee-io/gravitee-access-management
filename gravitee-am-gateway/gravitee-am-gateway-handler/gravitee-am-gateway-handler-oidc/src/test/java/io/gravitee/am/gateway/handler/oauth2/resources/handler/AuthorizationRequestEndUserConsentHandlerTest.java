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

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestEndUserConsentHandler;
import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.utils.OAuth2Constants;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.web.handler.SessionHandler;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationRequestEndUserConsentHandlerTest  extends RxWebTestBase {

    @Mock
    private UserConsentService userConsentService;

    @Mock
    private Domain domain;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // set domain
        when(domain.getPath()).thenReturn("test");

        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(new AuthorizationRequestEndUserConsentHandler(userConsentService, domain))
                .handler(rc -> rc.response().end())
                .failureHandler(rc -> rc.response().setStatusCode(403).end());
    }

    @Test
    public void shouldApproveRequest_clientAutoApproval() throws Exception {
        final String clientId = "client_id";
        final String autoApproveScope = "read";
        Client client = new Client();
        client.setClientId(clientId);
        client.setAutoApproveScopes(Collections.singletonList(autoApproveScope));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(clientId);
        authorizationRequest.setScopes(Collections.singleton(autoApproveScope));

        router.route().order(-2).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.session().put(OAuth2Constants.AUTHORIZATION_REQUEST, authorizationRequest);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/authorize/callback?param=param1",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldNotApproveRequest_noClientAutoApproval() throws Exception {
        final String clientId = "client_id";
        final String userId = "user_id";
        final String autoApproveScope = "read";
        Client client = new Client();
        client.setClientId(clientId);
        client.setAutoApproveScopes(null);

        User user = new User();
        user.setId(userId);

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(clientId);
        authorizationRequest.setScopes(Collections.singleton(autoApproveScope));

        when(userConsentService.checkConsent(any(), any())).thenReturn(Single.just(Collections.emptySet()));

        router.route().order(-2).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.put("client", client);
            routingContext.session().put(OAuth2Constants.AUTHORIZATION_REQUEST, authorizationRequest);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test/oauth/consent"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldApproveRequest_noClientAutoApproval_userApproval() throws Exception {
        final String clientId = "client_id";
        final String userId = "user_id";
        final String autoApproveScope = "read";
        Client client = new Client();
        client.setClientId(clientId);
        client.setAutoApproveScopes(null);

        User user = new User();
        user.setId(userId);

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(clientId);
        authorizationRequest.setScopes(Collections.singleton(autoApproveScope));

        when(userConsentService.checkConsent(any(), any())).thenReturn(Single.just(Collections.singleton(autoApproveScope)));

        router.route().order(-2).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put("client", client);
            routingContext.session().put(OAuth2Constants.AUTHORIZATION_REQUEST, authorizationRequest);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/authorize/callback?param=param1",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldNotApproveRequest_promptConsent() throws Exception {
        final String clientId = "client_id";
        final String userId = "user_id";
        final String autoApproveScope = "read";
        Client client = new Client();
        client.setClientId(clientId);
        client.setAutoApproveScopes(Collections.singletonList(autoApproveScope));

        User user = new User();
        user.setId(userId);

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(clientId);
        authorizationRequest.setScopes(Collections.singleton(autoApproveScope));
        authorizationRequest.setPrompts(Collections.singleton("consent"));

        router.route().order(-2).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.put("client", client);
            routingContext.session().put(OAuth2Constants.AUTHORIZATION_REQUEST, authorizationRequest);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&prompt=consent",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test/oauth/consent"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotApproveRequest_noClientAutoApproval_userDenial() throws Exception {
        final String clientId = "client_id";
        final String userId = "user_id";
        final String autoApproveScope = "read";
        Client client = new Client();
        client.setClientId(clientId);
        client.setAutoApproveScopes(null);

        User user = new User();
        user.setId(userId);

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(clientId);
        authorizationRequest.setScopes(Collections.singleton(autoApproveScope));
        authorizationRequest.setApproved(false);

        router.route().order(-2).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.put("client", client);
            routingContext.session().put("userConsentCompleted", true);
            routingContext.session().put(OAuth2Constants.AUTHORIZATION_REQUEST, authorizationRequest);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                HttpStatusCode.FORBIDDEN_403, "Forbidden", null);
    }

    @Test
    public void shouldApproveRequest_userApprovalChoice() throws Exception {
        final String clientId = "client_id";
        final String userId = "user_id";
        final String readScope = "read";
        final String writeScope = "write";
        Client client = new Client();
        client.setClientId(clientId);
        client.setAutoApproveScopes(Collections.singletonList(readScope));

        User user = new User();
        user.setId(userId);

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(clientId);
        authorizationRequest.setApproved(true);

        router.route().order(-2).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new io.vertx.reactivex.ext.auth.User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.put("client", client);
            routingContext.session().put("userConsentCompleted", true);
            routingContext.session().put(OAuth2Constants.AUTHORIZATION_REQUEST, authorizationRequest);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

}
