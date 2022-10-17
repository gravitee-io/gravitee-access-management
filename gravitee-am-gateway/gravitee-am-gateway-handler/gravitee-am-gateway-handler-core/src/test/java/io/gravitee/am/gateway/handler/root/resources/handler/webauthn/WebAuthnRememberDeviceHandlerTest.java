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
package io.gravitee.am.gateway.handler.root.resources.handler.webauthn;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.webauthn.WebAuthnCookieService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.reactivex.core.buffer.Buffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnRememberDeviceHandlerTest extends RxWebTestBase {

    @Mock
    private WebAuthnCookieService webAuthnCookieService;

    @Mock
    private Domain domain;

    private WebAuthnRememberDeviceHandler webAuthnRememberDeviceHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        webAuthnRememberDeviceHandler = new WebAuthnRememberDeviceHandler(webAuthnCookieService, domain);

        router.route(HttpMethod.POST, "/webauthn/login")
                .handler(webAuthnRememberDeviceHandler)
                .handler(rc -> rc.end())
                .failureHandler(rc -> rc.response().setStatusCode(rc.statusCode()).end());
    }

    @Test
    public void shouldAddCookie_nominal_case() throws Exception {
        router.route()
                .order(-1)
                .handler(rc -> {
                    // set user
                    User endUser = new User();
                    endUser.setId("user-id");
                    rc.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));

                    // set application
                    Client client = new Client();
                    LoginSettings loginSettings = new LoginSettings();
                    loginSettings.setInherited(false);
                    loginSettings.setPasswordlessEnabled(true);
                    loginSettings.setPasswordlessRememberDeviceEnabled(true);
                    client.setLoginSettings(loginSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                });

        when(webAuthnCookieService.getRememberDeviceCookieName()).thenReturn("cookieName");
        when(webAuthnCookieService.getRememberDeviceCookieTimeout()).thenReturn(30000l);
        when(webAuthnCookieService.generateRememberDeviceCookieValue(any())).thenReturn(Single.just("cookieValue"));

        testRequest(
                HttpMethod.POST,
                "/webauthn/login",
                null,
                resp -> {
                    String cookie = resp.headers().get("set-cookie");
                    assertNotNull(cookie);
                    assertTrue(cookie.startsWith("cookieName=cookieValue; Max-Age=30;"));
                },
                200, "OK", null);

    }

    @Test
    public void shouldNotAddCookie_option_disabled() throws Exception {
        router.route()
                .order(-1)
                .handler(rc -> {
                    // set application
                    Client client = new Client();
                    LoginSettings loginSettings = new LoginSettings();
                    loginSettings.setInherited(false);
                    loginSettings.setPasswordlessEnabled(false);
                    loginSettings.setPasswordlessRememberDeviceEnabled(false);
                    client.setLoginSettings(loginSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                });

        testRequest(
                HttpMethod.POST,
                "/webauthn/login",
                null,
                resp -> {
                    String cookie = resp.headers().get("set-cookie");
                    assertNull(cookie);
                },
                200, "OK", null);

    }

    @Test
    public void shouldNotAddCookie_option_disabled_2() throws Exception {
        router.route()
                .order(-1)
                .handler(rc -> {
                    // set application
                    Client client = new Client();
                    LoginSettings loginSettings = new LoginSettings();
                    loginSettings.setInherited(false);
                    loginSettings.setPasswordlessEnabled(true);
                    loginSettings.setPasswordlessRememberDeviceEnabled(false);
                    client.setLoginSettings(loginSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                });

        testRequest(
                HttpMethod.POST,
                "/webauthn/login",
                null,
                resp -> {
                    String cookie = resp.headers().get("set-cookie");
                    assertNull(cookie);
                },
                200, "OK", null);

    }

    @Test
    public void shouldNotAddCookie_json_request() throws Exception {
        testRequest(
                HttpMethod.POST,
                "/webauthn/login",
                req -> {
                    req.headers().set("content-type", "application/json");
                    req.setChunked(true);
                    req.write(Buffer.newInstance(Json.encodeToBuffer("{}")));
                },
                resp -> {
                    String cookie = resp.headers().get("set-cookie");
                    assertNull(cookie);
                },
                200, "OK", null);
    }

    @Test
    public void shouldNotAddCookie_no_user() throws Exception {
        router.route()
                .order(-1)
                .handler(rc -> {
                    // set application
                    Client client = new Client();
                    LoginSettings loginSettings = new LoginSettings();
                    loginSettings.setInherited(false);
                    loginSettings.setPasswordlessEnabled(true);
                    loginSettings.setPasswordlessRememberDeviceEnabled(true);
                    client.setLoginSettings(loginSettings);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                });

        testRequest(
                HttpMethod.POST,
                "/webauthn/login",
                null,
                resp -> {
                    String cookie = resp.headers().get("set-cookie");
                    assertNull(cookie);
                },
                401, "Unauthorized", null);
    }
}
