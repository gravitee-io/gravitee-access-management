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
package io.gravitee.am.gateway.handler.root.resources.handler.user;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginFailureHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.DEFAULT_REMEMBER_ME_COOKIE_NAME;
import static io.gravitee.am.common.utils.ConstantKeys.REMEMBER_ME_PARAM_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Aurélien PACAUD (aurelien.pacaud at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserRememberMeRequestHandlerTest extends RxWebTestBase {

    @Mock
    private JWTService jwtService;
    @Mock
    private Domain domain;
    @Mock
    private AuthenticationFlowContextService authenticationFlowContextService;

    private UserRememberMeRequestHandler handler;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        handler = new UserRememberMeRequestHandler(jwtService, domain, DEFAULT_REMEMBER_ME_COOKIE_NAME);

        router.route(HttpMethod.POST, "/login")
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(BodyHandler.create())
                .failureHandler(new LoginFailureHandler(authenticationFlowContextService, domain, identityProviderManager));
    }

    @Test
    public void userNotWantsToBeRemembered() throws Exception {

        router.route(HttpMethod.POST, "/login")
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
                    rc.next();
                })
                .handler(handler)
                .handler(rc -> rc.end());

        testRequest(
                HttpMethod.POST,
                "/login",
                null,
                rep -> {
                    assertNull(getCookieFromName(rep.cookies(), DEFAULT_REMEMBER_ME_COOKIE_NAME));
                    assertNull(rep.getHeader("Location"));
                },
                200, "OK", null);
    }

    @Test
    public void userWantsToBeRememberedConfigurationAtDomainLevel() throws Exception {
        // init domain
        final var accountSettings = new AccountSettings();
        accountSettings.setRememberMe(true);
        accountSettings.setRememberMeDuration(10);
        when(domain.getAccountSettings()).thenReturn(accountSettings);

        router.route(HttpMethod.POST, "/login")
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
                    io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
                    endUser.setId("user-id");
                    rc.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    rc.next();
                })
                .handler(handler)
                .handler(rc -> rc.end());

        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("encodedCookie"));

        testRequest(
                HttpMethod.POST,
                "/login",
                httpClientRequest -> {
                    MultiMap form = MultiMap.caseInsensitiveMultiMap();
                    form.set(REMEMBER_ME_PARAM_KEY, "on");
                    httpClientRequest.putHeader("content-type", "multipart/form-data");
                    httpClientRequest.send(form.toString());
                },
                rep -> {
                    final var cookie = getCookieFromName(rep.cookies(), DEFAULT_REMEMBER_ME_COOKIE_NAME);
                    assertNotNull(cookie);
                    assertTrue(cookie.contains("Max-Age=10"));
                    assertNull(rep.getHeader("Location"));
                },
                200, "OK", null);
    }

    @Test
    public void userWantsToBeRememberedConfigurationAtApplicationLevel() throws Exception {

        final var accountSettings = new AccountSettings();
        accountSettings.setInherited(false);
        accountSettings.setRememberMe(true);
        accountSettings.setRememberMeDuration(30);

        final var client = new Client();
        client.setAccountSettings(accountSettings);

        router.route(HttpMethod.POST, "/login")
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
                    endUser.setId("user-id");
                    rc.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    rc.next();
                })
                .handler(handler)
                .handler(rc -> rc.end());

        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("encodedCookie"));

        testRequest(
                HttpMethod.POST,
                "/login",
                httpClientRequest -> {
                    MultiMap form = MultiMap.caseInsensitiveMultiMap();
                    form.set(REMEMBER_ME_PARAM_KEY, "on");
                    httpClientRequest.putHeader("content-type", "multipart/form-data");
                    httpClientRequest.send(form.toString());
                },
                rep -> {
                    final var cookie = getCookieFromName(rep.cookies(), DEFAULT_REMEMBER_ME_COOKIE_NAME);
                    assertNotNull(cookie);
                    assertTrue(cookie.contains("Max-Age=30"));
                    assertNull(rep.getHeader("Location"));
                },
                200, "OK", null);
    }

    @Test
    public void userWantsToBeRememberedButNoUserInContext() throws Exception {

        router.route(HttpMethod.POST, "/login")
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
                    rc.next();
                })
                .handler(handler)
                .handler(rc -> rc.end());

        testRequest(
                HttpMethod.POST,
                "/login",
                httpClientRequest -> {
                    MultiMap form = MultiMap.caseInsensitiveMultiMap();
                    form.set(REMEMBER_ME_PARAM_KEY, "on");
                    httpClientRequest.putHeader("content-type", "multipart/form-data");
                    httpClientRequest.send(form.toString());
                },
                rep -> {
                    assertNull(getCookieFromName(rep.cookies(), DEFAULT_REMEMBER_ME_COOKIE_NAME));
                    assertNull(rep.getHeader("Location"));
                },
                200, "OK", null);
    }

    @Test
    public void userWantsToBeRememberedAndRememberMeFlagComesFromContext() throws Exception {
        // init domain
        final var accountSettings = new AccountSettings();
        accountSettings.setRememberMe(true);
        accountSettings.setRememberMeDuration(10);
        when(domain.getAccountSettings()).thenReturn(accountSettings);

        router.route(HttpMethod.POST, "/login")
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
                    io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
                    endUser.setId("user-id");
                    rc.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    rc.put(ConstantKeys.REMEMBER_ME_PARAM_KEY, true);
                    rc.next();
                })
                .handler(handler)
                .handler(rc -> rc.end());

        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("encodedCookie"));

        testRequest(
                HttpMethod.POST,
                "/login",
                null,
                rep -> {
                    final var cookie = getCookieFromName(rep.cookies(), DEFAULT_REMEMBER_ME_COOKIE_NAME);
                    assertNotNull(cookie);
                    assertTrue(cookie.contains("Max-Age=10"));
                    assertNull(rep.getHeader("Location"));
                },
                200, "OK", null);
    }

    @Test
    public void userNotWantsToBeRememberedAndRememberMeFlagComesFromContext() throws Exception {

        router.route(HttpMethod.POST, "/login")
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
                    io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
                    endUser.setId("user-id");
                    rc.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    rc.put(ConstantKeys.REMEMBER_ME_PARAM_KEY, false);
                    rc.next();
                })
                .handler(handler)
                .handler(rc -> rc.end());

        testRequest(
                HttpMethod.POST,
                "/login",
                null,
                rep -> {
                    assertNull(getCookieFromName(rep.cookies(), DEFAULT_REMEMBER_ME_COOKIE_NAME));
                    assertNull(rep.getHeader("Location"));
                },
                200, "OK", null);
    }

    private String getCookieFromName(List<String> cookies, String cookieName) {
        for (String cookie : cookies) {
            if (cookie.contains(cookieName)) {
                return cookie;
            }
        }

        return null;
    }
}