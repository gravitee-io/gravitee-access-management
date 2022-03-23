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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Maybe;
import io.vertx.core.Handler;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.webauthn.RelyingParty;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.auth.webauthn.WebAuthn;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.SessionHandler;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnRegisterEndpointTest extends RxWebTestBase implements AbstractWebAuthnTest {

    private static final String REQUEST_PATH = "/self-account-management-test/webauthn/register";

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private Domain domain;

    @Mock
    private ThymeleafTemplateEngine templateEngine;

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    UserService userService;

    private ClientRequestParseHandler clientRequestParseHandler;
    private WebAuthnRegisterEndpoint webAuthnRegisterEndpoint;
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
        final VertxOptions options = getOptions();
        final Vertx vertx = Vertx.vertx(options);

        final WebAuthn webAuthnImpl = WebAuthn.create(vertx, new GraviteeWebAuthnOptions().setRelyingParty(new RelyingParty().setName("Some Party Name")));
        final SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx));

        webAuthnRegisterEndpoint = new WebAuthnRegisterEndpoint(domain, userAuthenticationManager, webAuthnImpl, this.templateEngine, userService);

        router.route()
                .order(-1)
                .handler(sessionHandler)
                .handler(ctx -> {
                    ctx.setUser(io.vertx.reactivex.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                    ctx.next();
                })
                .handler(BodyHandler.create())
                .failureHandler(new ErrorHandler());

    }

    @Test
    public void shouldNotRedirectToRegisterEndpoint_for_selfRegistration() throws Exception {
        final String token = createToken();
        final UserToken userToken = new UserToken(null, null, createJWT());

        when(userService.verifyToken(any())).thenReturn(Maybe.just(userToken));

        router.route(REQUEST_PATH)
                .handler(clientRequestParseHandler)
                .handler(webAuthnAccessHandler)
                .handler(renderPageWithRedirectUri())
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.GET,
                REQUEST_PATH + "?client_id=" + client.getClientId() + "&redirect_uri=" + REDIRECT_URI + "&registration_token=" + token,
                req -> req.headers().set("content-type", "application/json"),
                200,
                "OK", null);
    }

    @Test
    public void shouldRedirectToRegisterEndpoint() throws Exception {
        router.route(REQUEST_PATH)
                .handler(clientRequestParseHandler)
                .handler(webAuthnAccessHandler)
                .handler(renderPageWithRegisterEndpointUri())
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.GET,
                REQUEST_PATH + "?client_id=" + client.getClientId() + "&redirect_uri=" + REDIRECT_URI,
                req -> req.headers().set("content-type", "application/json"),
                200,
                "OK", null);
    }

    private Handler<RoutingContext> renderPageWithRedirectUri() {
        return routingContext -> {
            doAnswer(answer -> {
                final String skipAction = routingContext.get(ConstantKeys.SKIP_ACTION_KEY);
                assertEquals("skip action should be the redirect uri", REDIRECT_URI, skipAction);
                assertFalse("skip action should not contain", skipAction.contains("/webauthn/register"));

                routingContext.next();
                return answer;
            }).when(templateEngine).render(Mockito.<Map<String, Object>>any(), any(), any());

            webAuthnRegisterEndpoint.handle(routingContext);
        };
    }

    private Handler<RoutingContext> renderPageWithRegisterEndpointUri() {
        return routingContext -> {
            doAnswer(answer -> {
                final String skipAction = routingContext.get(ConstantKeys.SKIP_ACTION_KEY);
                assertTrue("skip action should contain", skipAction.contains("/webauthn/register"));
                assertTrue("skip action contain the redirect uri", skipAction.contains("redirect_uri=http%3A%2F%2Fredirect.com%2Fapp"));

                routingContext.next();
                return answer;
            }).when(templateEngine).render(Mockito.<Map<String, Object>>any(), any(), any());

            webAuthnRegisterEndpoint.handle(routingContext);
        };
    }
}
