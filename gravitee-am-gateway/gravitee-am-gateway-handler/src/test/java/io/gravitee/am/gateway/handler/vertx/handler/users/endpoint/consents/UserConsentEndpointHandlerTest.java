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
package io.gravitee.am.gateway.handler.vertx.handler.users.endpoint.consents;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.jwt.JwtService;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.token.Token;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.handler.users.handler.AuthTokenParseHandler;
import io.gravitee.am.gateway.handler.vertx.handler.users.handler.ErrorHandler;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.service.exception.ScopeApprovalNotFoundException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserConsentEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    @Mock
    private ClientSyncService clientService;

    @Mock
    private TokenService tokenService;

    @Mock
    private Domain domain;

    @InjectMocks
    private UserConsentEndpointHandler userConsentEndpointHandler = new UserConsentEndpointHandler(userService, clientService, domain);

    @InjectMocks
    private AuthTokenParseHandler authTokenParseHandler = AuthTokenParseHandler.create(jwtService, tokenService, clientService, "consent_admin");

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void shouldNotGetConsent_no_token() throws Exception {
        router.route("/users/:userId/consents/:consentId")
                .handler(authTokenParseHandler)
                .handler(userConsentEndpointHandler::get)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.GET, "/users/user-id/consents/consent-id",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");

    }

    @Test
    public void shouldNotGetConsent_invalid_token() throws Exception {
        Token token = new AccessToken("uuid");
        token.setExpiresIn(10000);
        token.setScope("read");

        when(jwtService.decode(anyString())).thenReturn(Single.just(new JWT()));
        when(clientService.findByClientId(anyString())).thenReturn(Maybe.just(new Client()));
        when(tokenService.getAccessToken(anyString(), any())).thenReturn(Maybe.just(token));

        router.route("/users/:userId/consents/:consentId")
                .handler(authTokenParseHandler)
                .handler(userConsentEndpointHandler::get)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.GET, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                401,
                "Unauthorized", null);
    }

    @Test
    public void shouldNotGetConsent_notFound() throws Exception {
        Token token = new AccessToken("uuid");
        token.setExpiresIn(10000);
        token.setScope("consent_admin");

        when(jwtService.decode(anyString())).thenReturn(Single.just(new JWT()));
        when(clientService.findByClientId(anyString())).thenReturn(Maybe.just(new Client()));
        when(tokenService.getAccessToken(anyString(), any())).thenReturn(Maybe.just(token));
        when(userService.consent(anyString())).thenReturn(Maybe.error(new ScopeApprovalNotFoundException("consentId")));

        router.route("/users/:userId/consents/:consentId")
                .handler(authTokenParseHandler)
                .handler(userConsentEndpointHandler::get)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.GET, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                404,
                "Not Found", null);

    }

    @Test
    public void shouldGetConsent() throws Exception {
        Token token = new AccessToken("uuid");
        token.setExpiresIn(10000);
        token.setScope("consent_admin");

        when(jwtService.decode(anyString())).thenReturn(Single.just(new JWT()));
        when(clientService.findByClientId(anyString())).thenReturn(Maybe.just(new Client()));
        when(tokenService.getAccessToken(anyString(), any())).thenReturn(Maybe.just(token));
        when(userService.consent(anyString())).thenReturn(Maybe.just(new ScopeApproval()));

        router.route("/users/:userId/consents/:consentId")
                .handler(authTokenParseHandler)
                .handler(userConsentEndpointHandler::get)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.GET, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                200,
                "OK", null);

    }

    @Test
    public void shouldRevokeConsent() throws Exception {
        Token token = new AccessToken("uuid");
        token.setExpiresIn(10000);
        token.setScope("consent_admin");

        when(jwtService.decode(anyString())).thenReturn(Single.just(new JWT()));
        when(clientService.findByClientId(anyString())).thenReturn(Maybe.just(new Client()));
        when(tokenService.getAccessToken(anyString(), any())).thenReturn(Maybe.just(token));
        when(userService.revokeConsent(anyString(), anyString(), any())).thenReturn(Completable.complete());

        router.route("/users/:userId/consents/:consentId")
                .handler(authTokenParseHandler)
                .handler(userConsentEndpointHandler::revoke)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.DELETE, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                204,
                "No Content", null);
    }
}
