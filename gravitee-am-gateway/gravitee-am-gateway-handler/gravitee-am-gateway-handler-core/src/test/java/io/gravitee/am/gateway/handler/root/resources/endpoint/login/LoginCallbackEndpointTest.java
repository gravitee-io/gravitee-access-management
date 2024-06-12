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

package io.gravitee.am.gateway.handler.root.resources.endpoint.login;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.DummySession;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.CloseSessionMode;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.MultiMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static io.gravitee.am.common.utils.ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginCallbackEndpointTest extends RxWebTestBase {

    @Mock
    private JWTService jwtService;

    @Mock
    private CertificateManager certificateManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/login/callback")
                .handler(ctx -> {
                    ctx.setSession(new DummySession());
                    ctx.next();
                })
                .handler(new LoginCallbackEndpoint(jwtService, certificateManager));
    }

    @Test
    public void shouldNotRedirectTo_authorize_whenSocialIdp_not_preserve_sessions() throws Exception {
        var authenticationProvider = mock(SocialAuthenticationProvider.class);
        when(authenticationProvider.closeSessionAfterSignIn()).thenReturn(CloseSessionMode.REDIRECT);
        var signOutRequest = new Request();
        signOutRequest.setUri("http://idp/signout");
        when(authenticationProvider.signOutUrl(any())).thenReturn(Maybe.just(signOutRequest));

        when(certificateManager.defaultCertificateProvider()).thenReturn(mock(CertificateProvider.class));
        when(jwtService.encode(any(JWT.class), any(CertificateProvider.class))).thenReturn(Single.just("signedIn_state"));

        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, MultiMap.caseInsensitiveMultiMap());
            routingContext.put(ConstantKeys.PROVIDER_CONTEXT_KEY, authenticationProvider);
            routingContext.put(OIDC_PROVIDER_ID_TOKEN_KEY, "op_id_token_value");
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/login/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.startsWith("http://idp/signout"));
                    assertTrue(location.contains("state=signedIn_state"));
                    assertTrue(location.contains("id_token_hint=op_id_token_value"));
                    assertTrue(location.contains("post_logout_redirect_uri="));
                    assertTrue(location.contains("%2Flogin%2Fcallback"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectTo_authorize_whenSocialIdp_preserve_sessions() throws Exception {
        var authenticationProvider = mock(SocialAuthenticationProvider.class);
        when(authenticationProvider.closeSessionAfterSignIn()).thenReturn(CloseSessionMode.KEEP_ACTIVE);

        router.route().order(-1).handler(routingContext -> {
            Client client = new Client();
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PARAM_CONTEXT_KEY, MultiMap.caseInsensitiveMultiMap());
            routingContext.put(ConstantKeys.PROVIDER_CONTEXT_KEY, authenticationProvider);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/login/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("oauth/authorize"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
