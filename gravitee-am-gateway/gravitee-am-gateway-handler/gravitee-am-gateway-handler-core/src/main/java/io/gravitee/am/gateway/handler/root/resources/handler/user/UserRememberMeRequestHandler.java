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
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.rxjava3.core.http.Cookie;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.REMEMBER_ME_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_ID_KEY;
import static io.vertx.rxjava3.ext.web.handler.SessionHandler.DEFAULT_SESSION_TIMEOUT;

/**
 * @author Aurélien PACAUD (aurelien.pacaud at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserRememberMeRequestHandler extends UserRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(UserRememberMeRequestHandler.class);
    private static final String REMEMBER_ME_ON = "on";

    private final JWTService jwtService;
    private final Domain domain;
    private final String cookieName;

    public UserRememberMeRequestHandler(JWTService jwtService, Domain domain, String cookieName) {
        this.jwtService = jwtService;
        this.domain = domain;
        this.cookieName = cookieName;
    }

    @Override
    public void handle(RoutingContext routingContext) {

        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        final var req = routingContext.request();

        // If the user checks "remember me" option in the form or if another handler added a parameter in the context
        boolean rememberMe = REMEMBER_ME_ON.equalsIgnoreCase(req.formAttributes().get(REMEMBER_ME_PARAM_KEY));

        if (routingContext.data().containsKey(REMEMBER_ME_PARAM_KEY)) {
            rememberMe |= (Boolean) routingContext.get(REMEMBER_ME_PARAM_KEY);
        }

        // If the user wants to be remembered, create a dedicated cookie.
        // Using domain or application remember-me duration to define the max-age of this cookie
        if (rememberMe && routingContext.user() != null) {

            final var jwt = new JWT();

            jwt.setIat(System.currentTimeMillis() / 1000);
            jwt.put(USER_ID_KEY, ((User) routingContext.user().getDelegate()).getUser().getId());

            jwtService.encode(jwt, client).map(jwtEncoded -> {
                        final var cookie = Cookie.cookie(cookieName, jwtEncoded);
                        cookie.setMaxAge(getRememberMeDuration(client));
                        return cookie;
                    })
                    .subscribe(
                            cookie -> {
                                routingContext.response().addCookie(cookie);
                                routingContext.next();
                            },
                            throwable -> {
                                logger.warn("Error during remember me cookie creation", throwable);
                                routingContext.fail(500);
                            }
                    );
        } else {
            routingContext.next();
        }
    }

    private long getRememberMeDuration(Client client) {

        var accountSettings = AccountSettings.getInstance(domain, client);

        if (accountSettings != null && accountSettings.isRememberMe()) {
            return accountSettings.getRememberMeDuration();
        }

        return DEFAULT_SESSION_TIMEOUT;// Should be never used
    }
}
