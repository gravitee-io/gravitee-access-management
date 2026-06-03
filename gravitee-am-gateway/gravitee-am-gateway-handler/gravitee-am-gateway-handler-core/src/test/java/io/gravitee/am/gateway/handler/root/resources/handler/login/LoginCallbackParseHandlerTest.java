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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.common.exception.authentication.LoginCallbackFailedException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.DummySession;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.gravitee.am.certificate.api.Key;

import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.STATE;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginCallbackParseHandlerTest extends RxWebTestBase {

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private JWTService jwtService;

    @Mock
    private CertificateManager certificateManager;

    private LoginCallbackParseHandler handler;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        handler = new LoginCallbackParseHandler(clientSyncService, identityProviderManager, jwtService, certificateManager);
    }

    /**
     * Mocks the state JWT decoding so that restoreInitialQueryParams() succeeds
     * and puts a PARAM_CONTEXT_KEY containing the given clientId and providerId.
     */
    private void givenValidStateJwt(String clientId, String providerId) {
        var gatewayCertProvider = mock(io.gravitee.am.gateway.certificate.CertificateProvider.class);
        var apiCertProvider = mock(io.gravitee.am.certificate.api.CertificateProvider.class);

        JWT stateJwt = new JWT();
        stateJwt.put(ConstantKeys.CLAIM_QUERY_PARAM, "client_id=" + clientId + "&redirect_uri=http%3A%2F%2Fclient%2Fcallback");
        stateJwt.put(ConstantKeys.CLAIM_PROVIDER_ID, providerId);

        when(certificateManager.defaultCertificateProvider()).thenReturn(gatewayCertProvider);
        when(gatewayCertProvider.getProvider()).thenReturn(apiCertProvider);
        when(apiCertProvider.key()).thenReturn(Single.just(mock(Key.class)));
        when(jwtService.decodeAndVerify(anyString(), eq(gatewayCertProvider), eq(STATE)))
                .thenReturn(Single.just(stateJwt));
    }

    /**
     * AM-7078 — primary scenario.
     * External OIDC IdP returns an error via GET query string (auth-code / PKCE flow,
     * standard RFC 6749 §4.1.2.1 error response).
     * The handler must detect the error and fail with LoginCallbackFailedException
     * before reaching parseSocialProvider.
     */
    @Test
    public void should_failWithLoginCallbackFailedException_whenExternalIdpReturnsErrorInQueryParams() throws Exception {
        givenValidStateJwt("my-client", "my-provider");
        when(clientSyncService.findByClientId("my-client")).thenReturn(Maybe.just(new Client()));

        router.route().order(-1).handler(rc -> {
            rc.setSession(new DummySession());
            rc.next();
        });

        router.get("/login/callback")
                .handler(handler)
                .handler(rc -> rc.response().setStatusCode(200).end("should_not_reach"))
                .failureHandler(rc -> {
                    int status = rc.failure() instanceof LoginCallbackFailedException ? 400 : 500;
                    rc.response().setStatusCode(status).end(rc.failure().getClass().getSimpleName());
                });

        testRequest(
                HttpMethod.GET, "/login/callback?error=login_required&error_description=Authentication+required&state=state-token",
                null,
                null,
                400, "Bad Request", "LoginCallbackFailedException");

        verify(identityProviderManager, never()).get(anyString());
    }

    /**
     * Fragment / implicit-flow path (existing behaviour, regression guard).
     * LoginCallbackOpenIDConnectFlowHandler decoded the URL hash and put
     * error=... into the routing context before this handler runs.
     * The handler must still detect it and fail with LoginCallbackFailedException.
     */
    @Test
    public void should_failWithLoginCallbackFailedException_whenErrorAlreadyInContext() throws Exception {
        givenValidStateJwt("my-client", "my-provider");
        when(clientSyncService.findByClientId("my-client")).thenReturn(Maybe.just(new Client()));

        router.route().order(-1).handler(rc -> {
            rc.setSession(new DummySession());
            rc.put(ConstantKeys.ERROR_PARAM_KEY, "login_required");
            rc.next();
        });

        router.get("/login/callback")
                .handler(handler)
                .handler(rc -> rc.response().setStatusCode(200).end("should_not_reach"))
                .failureHandler(rc -> {
                    int status = rc.failure() instanceof LoginCallbackFailedException ? 400 : 500;
                    rc.response().setStatusCode(status).end(rc.failure().getClass().getSimpleName());
                });

        testRequest(
                HttpMethod.GET, "/login/callback?state=state-token",
                null,
                null,
                400, "Bad Request", "LoginCallbackFailedException");

        verify(identityProviderManager, never()).get(anyString());
    }

    /**
     * Happy path: no error from the external IdP.
     * The handler must load client and provider, then call next().
     */
    @Test
    public void should_continueToNextHandler_whenNoError() throws Exception {
        var authProvider = mock(AuthenticationProvider.class);
        givenValidStateJwt("my-client", "my-provider");
        when(clientSyncService.findByClientId("my-client")).thenReturn(Maybe.just(new Client()));
        when(identityProviderManager.get("my-provider")).thenReturn(Maybe.just(authProvider));

        router.route().order(-1).handler(rc -> {
            rc.setSession(new DummySession());
            rc.next();
        });

        router.get("/login/callback")
                .handler(handler)
                .handler(rc -> rc.response().setStatusCode(200).end("ok"));

        testRequest(
                HttpMethod.GET, "/login/callback?code=auth-code&state=state-token",
                null,
                null,
                200, "OK", "ok");
    }
}
