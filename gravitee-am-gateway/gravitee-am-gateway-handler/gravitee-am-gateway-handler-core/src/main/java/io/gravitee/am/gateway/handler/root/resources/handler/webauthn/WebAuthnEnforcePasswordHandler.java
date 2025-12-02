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
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.http.Cookie;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnEnforcePasswordHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebAuthnEnforcePasswordHandler.class);

    private final Domain domain;
    private final WebAuthnCookieService webAuthnCookieService;

    public WebAuthnEnforcePasswordHandler(Domain domain,
                                          WebAuthnCookieService webAuthnCookieService) {
        this.domain = domain;
        this.webAuthnCookieService = webAuthnCookieService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final Cookie cookie = routingContext.request().getCookie(webAuthnCookieService.getRememberDeviceCookieName());
        final LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        // feature disabled, continue
        if (loginSettings == null || !loginSettings.isPasswordlessEnabled() || !loginSettings.isEnforcePasswordPolicyEnabled()) {
            routingContext.next();
            return;
        }
        // no remember device cookie, continue
        if (cookie == null) {
            routingContext.next();
            return;
        }
        webAuthnCookieService.extractUserFromRememberDeviceCookieValue(cookie.getValue())
                .subscribe(
                        user -> {
                            final Date lastLoginWithCredentials = user.getLastLoginWithCredentials();
                            if (lastLoginWithCredentials != null) {
                                final Integer maxAge = loginSettings.getPasswordlessEnforcePasswordMaxAge();
                                final Instant expirationDate = lastLoginWithCredentials.toInstant().plusSeconds(maxAge);
                                if (expirationDate.isBefore(Instant.now())) {
                                    // enhance context
                                    routingContext.put(ConstantKeys.PASSWORDLESS_ENFORCE_PASSWORD, true);
                                }
                            }
                            routingContext.next();
                        },
                        error -> {
                            LOGGER.error("Unable to extract the user information from the remember device cookie", error);
                            routingContext.next();
                        });
    }
}
