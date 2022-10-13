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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.webauthn.WebAuthnCookieService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.Cookie;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnLoginStep extends AuthenticationFlowStep {

    private final Domain domain;
    private final WebAuthnCookieService webAuthnCookieService;

    public WebAuthnLoginStep(Handler<RoutingContext> handler, Domain domain, WebAuthnCookieService webAuthnCookieService) {
        super(handler);
        this.domain = domain;
        this.webAuthnCookieService = webAuthnCookieService;
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        // if user is already authenticated, continue
        if (routingContext.user() != null) {
            flow.doNext(routingContext);
            return;
        }

        // get current application
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        // get login settings, if passwordless is disabled or passwordless remember is disabled, continue
        LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        if (loginSettings == null
                || !loginSettings.isPasswordlessEnabled()
                || !loginSettings.isPasswordlessRememberDeviceEnabled()) {
            flow.doNext(routingContext);
            return;
        }

        // check if passwordless device recognition is present
        Cookie cookie = routingContext.request().getCookie(webAuthnCookieService.getRememberDeviceCookieName());
        if (cookie == null) {
            // no cookie, continue
            flow.doNext(routingContext);
            return;
        }

        // check cookie value and continue
        webAuthnCookieService.verifyRememberDeviceCookieValue(cookie.getValue())
                .subscribe(
                        () -> {
                            /// go to the WebAuthn login page
                            flow.exit(this);
                        },
                        error -> {
                            // unable to decode the cookie, continue
                            flow.doNext(routingContext);
                        }
                );
    }
}
