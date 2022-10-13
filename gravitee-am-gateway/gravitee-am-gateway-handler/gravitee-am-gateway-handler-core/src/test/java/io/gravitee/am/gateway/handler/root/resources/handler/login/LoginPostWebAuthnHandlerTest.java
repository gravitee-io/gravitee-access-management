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

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.webauthn.WebAuthnCookieService;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.http.Cookie;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginPostWebAuthnHandlerTest extends RxWebTestBase {

    @Mock
    private WebAuthnCookieService webAuthnCookieService;

    private LoginPostWebAuthnHandler loginPostWebAuthnHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        loginPostWebAuthnHandler = new LoginPostWebAuthnHandler(webAuthnCookieService);

        router.route(HttpMethod.POST, "/login")
                .handler(loginPostWebAuthnHandler)
                .handler(rc -> rc.end())
                .failureHandler(rc -> rc.response().setStatusCode(rc.statusCode()).end());
    }

    @Test
    public void shouldRemoveCookie_nominal_case() throws Exception {
        router.route()
                .order(-1)
                .handler(rc -> {
                    // set cookie
                    Cookie cookie = Cookie.cookie("cookieName", "cookieValue");
                    cookie.setMaxAge(30);
                    rc.response().addCookie(Cookie.cookie("cookieName", "cookieValue"));
                    rc.next();
                });

        when(webAuthnCookieService.getRememberDeviceCookieName()).thenReturn("cookieName");

        testRequest(
                HttpMethod.POST,
                "/login",
                null,
                resp -> {
                    String cookie = resp.headers().get("set-cookie");
                    assertNotNull(cookie);
                    // cookie must be removed with Max-Age = 0;
                    assertTrue(cookie.startsWith("cookieName=; Max-Age=0;"));
                },
                200, "OK", null);
    }
}
