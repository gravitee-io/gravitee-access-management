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

package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.client.ClientRequestParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnAccessHandler;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.GraviteeWebAuthnOptions;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.CredentialService;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.Authenticator;
import io.vertx.ext.auth.webauthn.RelyingParty;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.auth.webauthn.WebAuthn;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.SessionHandler;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnResponseEndpointTest extends RxWebTestBase implements AbstractWebAuthnTest{

    private static final String USER_ID = "123XXXRandom";
    private static final String USER_NAME = "an.user";
    private static final String CHALLENGE = "wEH9BVGggOK6h9tVOfXA5i9ZfhSreMyfnTNoQV0CpsVjjwp6AkcGpbnQBOdD-Y9bQ_WPBH2U0pVuQ_Eu1KWH5A";
    private static final String REQUEST_PATH = "/self-account-management-test/webauthn/response";

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private CredentialService credentialService;

    @Mock
    private Domain domain;

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private UserService userService;

    private WebAuthnResponseEndpoint webAuthnResponseEndpoint;
    private ClientRequestParseHandler clientRequestParseHandler;
    private WebAuthnAccessHandler webAuthnAccessHandler;
    private Client client;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        clientRequestParseHandler = new ClientRequestParseHandler(clientSyncService).setRequired(true);
        webAuthnAccessHandler = new WebAuthnAccessHandler(domain);
        client = new Client();
        client.setClientId(UUID.randomUUID().toString());
        when(clientSyncService.findByClientId(client.getClientId())).thenReturn(Maybe.just(client));

        final LoginSettings loginSettings = new LoginSettings();
        loginSettings.setPasswordlessEnabled(true);
        when(domain.getLoginSettings()).thenReturn(loginSettings);

        final User user = new User();
        user.setId(USER_ID);

        when(userAuthenticationManager.authenticate(any(Client.class), any(Authentication.class), anyBoolean())).thenReturn(Single.just(user));
        when(credentialService.update(any(), any(), any(), any())).thenReturn(Completable.complete());

        final VertxOptions options = getOptions();
        final Vertx vertx = Vertx.vertx(options);
        final Function<Authenticator, Future<Void>> updater = author -> Future.succeededFuture();

        final WebAuthn webAuthnImpl = WebAuthn.create(vertx, new GraviteeWebAuthnOptions().setRelyingParty(new RelyingParty().setName("Some Party Name")));
        webAuthnImpl.authenticatorUpdater(updater);

        final SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx));
        webAuthnResponseEndpoint = new WebAuthnResponseEndpoint(userAuthenticationManager, webAuthnImpl, credentialService, domain, userService);

        router.route()
                .order(-1)
                .handler(sessionHandler)
                .handler(ctx -> {
                    ctx.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_USER_ID, USER_ID);
                    ctx.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, USER_NAME);
                    ctx.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, CHALLENGE);
                    ctx.next();
                })
                .handler(BodyHandler.create())
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldNotRedirectToAuthoriseEndpoint_for_selfRegistration() throws Exception {
        final String token = createToken();
        final UserToken userToken = new UserToken(null, null, createJWT());

        when(userService.verifyToken(any())).thenReturn(Maybe.just(userToken));

        router.route(REQUEST_PATH)
                .handler(clientRequestParseHandler)
                .handler(webAuthnAccessHandler)
                .handler(webAuthnResponseEndpoint)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST,
                REQUEST_PATH + "?client_id=" + client.getClientId() + "&redirect_uri=" + REDIRECT_URI + "&registration_token=" + token,
                req -> {
                    final Buffer buffer = Buffer.buffer();
                    buffer.appendString(sampleCredential());
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                resp -> {
                    final String location = resp.headers().get("Location");
                    assertTrue("location should be the redirect uri", location.contains(REDIRECT_URI));
                    assertFalse("location should not point to authorize endpoint", location.contains("/oauth/authorize"));
                },
                200,
                "OK", null);
    }

    @Test
    public void shouldRedirectToAuthoriseEndpoint() throws Exception {
        router.route(REQUEST_PATH)
                .handler(clientRequestParseHandler)
                .handler(webAuthnAccessHandler)
                .handler(webAuthnResponseEndpoint)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST,
                REQUEST_PATH + "?client_id=" + client.getClientId() + "&redirect_uri=" + REDIRECT_URI,
                req -> {
                    final Buffer buffer = Buffer.buffer();
                    buffer.appendString(sampleCredential());
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                resp -> {
                    final String location = resp.headers().get("Location");
                    assertTrue("location should point to authorize endpoint", location.contains("/oauth/authorize"));
                    assertTrue("location should contain the redirect uri", location.contains("redirect.com"));
                },
                200,
                "OK", null);
    }

    private String sampleCredential() {
        JsonObject credential = new JsonObject()
                .put("rawId", "BXfaInD6HMmcx8bPfTRD0qT0voAVIYGXSK5LPu7LLGU17S0tVk9pSSyz1vuxw3SbPQXBraP9hWHR1m09D3JKs9Thgp_6hAaK_vKa_iW_rrZUf3U2ednl-COj")
                .put("id", "BXfaInD6HMmcx8bPfTRD0qT0voAVIYGXSK5LPu7LLGU17S0tVk9pSSyz1vuxw3SbPQXBraP9hWHR1m09D3JKs9Thgp_6hAaK_vKa_iW_rrZUf3U2ednl-COj")
                .put("type", "public-key")
                .put("response", new JsonObject()
                        .put("clientDataJSON", "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoid0VIOUJWR2dnT0s2aDl0Vk9mWEE1aTlaZmhTcmVNeWZuVE5vUVYwQ3BzVmpqd3A2QWtjR3BiblFCT2RELVk5YlFfV1BCSDJVMHBWdVFfRXUxS1dINUEiLCJvcmlnaW4iOiJodHRwOi8vbG9jYWxob3N0OjgwOTIiLCJjcm9zc09yaWdpbiI6ZmFsc2V9")
                        .put("attestationObject", "o2NmbXRmcGFja2VkZ2F0dFN0bXSiY2FsZyZjc2lnWEcwRQIhAKTpXQ_JG4KUgJT6L6zOEz8YUfaGPdj9-3CgL3m8KysFAiApPlMQNMEecungAIXhHJ4Frv-Wg6ZLVnY103y4UKB0cWhhdXRoRGF0YVjeSZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2NFYe5mta3OAAI1vMYKZIsLJfHwVQMAWgF32iJw-hzJnMfGz300Q9Kk9L6AFSGBl0iuSz7uyyxlNe0tLVZPaUkss9b7scN0mz0Fwa2j_YVh0dZtPQ9ySrPU4YKf-oQGiv7ymv4lv662VH91NnnZ5fgjo6UBAgMmIAEhWCAS-x7HsIXDRUm-mwG7mra6_qXKakGSC9I0lIFQal22kiJYILFheI0RGbEJ2Si_vUDPSjCPzAMTcOJlYbXAw-rIKhLu"));

        return credential.toString();
    }
}
