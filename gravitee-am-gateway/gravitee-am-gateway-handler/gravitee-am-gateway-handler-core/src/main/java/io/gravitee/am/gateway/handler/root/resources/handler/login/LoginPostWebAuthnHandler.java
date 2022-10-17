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

import io.gravitee.am.gateway.handler.common.webauthn.WebAuthnCookieService;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.Cookie;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * Post Login WebAuthn Remember Device Cookie handler
 * We remove the cookie if the end-user sign-in via password or social provider
 * It means that a user did not succeed to sign-in via passwordless (the device has been deleted or non-functional)
 * In this case we don't want to redirect the users directly to the webauthn login page
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginPostWebAuthnHandler implements Handler<RoutingContext> {

    private final WebAuthnCookieService webAuthnCookieService;

    public LoginPostWebAuthnHandler(WebAuthnCookieService webAuthnCookieService) {
        this.webAuthnCookieService = webAuthnCookieService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
       final Cookie cookie = routingContext.request().getCookie(webAuthnCookieService.getRememberDeviceCookieName());
       if (cookie != null) {
           routingContext
                   .response()
                   .addCookie(Cookie.cookie(webAuthnCookieService.getRememberDeviceCookieName(), "").setMaxAge(0));
       }
       routingContext.next();
    }
}
