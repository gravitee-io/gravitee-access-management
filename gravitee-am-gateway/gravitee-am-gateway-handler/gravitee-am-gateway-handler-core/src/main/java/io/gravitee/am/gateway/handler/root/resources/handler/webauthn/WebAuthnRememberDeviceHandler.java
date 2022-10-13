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
import io.gravitee.am.gateway.handler.common.webauthn.WebAuthnCookieService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.reactivex.core.http.Cookie;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.concurrent.TimeUnit;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnRememberDeviceHandler implements Handler<RoutingContext> {
    private final WebAuthnCookieService webAuthnCookieService;
    private final Domain domain;

    public WebAuthnRememberDeviceHandler(WebAuthnCookieService webAuthnCookieService, Domain domain) {
        this.webAuthnCookieService = webAuthnCookieService;
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // only apply this handler for HTML responses
        // see https://github.com/gravitee-io/issues/issues/7158
        if (MediaType.APPLICATION_JSON.equals(routingContext.request().getHeader(HttpHeaders.CONTENT_TYPE))) {
            routingContext.next();
            return;

        }

        // if webauthn remember device is disabled, continue
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        if (loginSettings == null
                || !loginSettings.isPasswordlessEnabled()
                || !loginSettings.isPasswordlessRememberDeviceEnabled()) {
            routingContext.next();
            return;
        }

        // if no user, continue
        final User user = routingContext.user();
        if (user == null) {
            routingContext.fail(401);
            return;
        }

        final io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) user.getDelegate()).getUser();;
        webAuthnCookieService.generateRememberDeviceCookieValue(endUser)
                .map(cookieValue -> {
                    Cookie cookie = Cookie.cookie(webAuthnCookieService.getRememberDeviceCookieName(), cookieValue);
                    // persist the cookie
                    cookie.setMaxAge(TimeUnit.MILLISECONDS.toSeconds(webAuthnCookieService.getRememberDeviceCookieTimeout()));
                    return cookie;
                })
                .subscribe(cookie -> {
                    routingContext.response().addCookie(cookie);
                    routingContext.next();
                });
    }
}
