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
package io.gravitee.am.gateway.handler.users.resources.consents;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.users.service.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.service.exception.ScopeApprovalNotFoundException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private ClientSyncService clientService;

    @Mock
    private Domain domain;

    @InjectMocks
    private UserConsentEndpointHandler userConsentEndpointHandler = new UserConsentEndpointHandler(userService, clientService, domain);

    @Mock
    private OAuth2AuthProvider oAuth2AuthProvider;

    private OAuth2AuthHandler oAuth2AuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider);

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void shouldNotGetConsent_no_token() throws Exception {
        router.route("/users/:userId/consents/:consentId")
                .handler(oAuth2AuthHandler)
                .handler(userConsentEndpointHandler::get)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.GET, "/users/user-id/consents/consent-id",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");

    }

    // TODO : need to mock Async Handler of the oauth2AuthHandler
    @Test
    @Ignore
    public void shouldNotGetConsent_invalid_token() throws Exception {
        JWT jwt = new JWT();
        jwt.setAud("client-id");

        router.route("/users/:userId/consents/:consentId")
                .handler(oAuth2AuthHandler)
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
        JWT jwt = new JWT();
        jwt.setAud("client-id");

        when(userService.consent(anyString())).thenReturn(Maybe.error(new ScopeApprovalNotFoundException("consentId")));

        router.route("/users/:userId/consents/:consentId")
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
        JWT jwt = new JWT();
        jwt.setAud("client-id");

        when(userService.consent(anyString())).thenReturn(Maybe.just(new ScopeApproval()));

        router.route("/users/:userId/consents/:consentId")
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
        when(userService.findById(anyString())).thenReturn(Maybe.just(new User()));
        when(userService.revokeConsent(anyString(), anyString(), any())).thenReturn(Completable.complete());

        router.route("/users/:userId/consents/:consentId")
                .handler(rc -> {
                    JWT token = new JWT();
                    token.setSub("sub");
                    rc.put(ConstantKeys.TOKEN_CONTEXT_KEY, token);
                    rc.next();
                })
                .handler(userConsentEndpointHandler::revoke)
                .failureHandler(new ErrorHandler());

        testRequest(
                HttpMethod.DELETE, "/users/user-id/consents/consent-id",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer token"),
                204,
                "No Content", null);
    }
}
