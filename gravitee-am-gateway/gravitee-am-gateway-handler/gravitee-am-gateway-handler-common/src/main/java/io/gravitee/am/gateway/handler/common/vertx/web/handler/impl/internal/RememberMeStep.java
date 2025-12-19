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

import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.http.Cookie;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.USER_ID_KEY;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.SESSION;

/**
 * @author Aurélien PACAUD (aurelien.pacaud at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RememberMeStep extends AuthenticationFlowStep {

    private static final Logger logger = LoggerFactory.getLogger(RememberMeStep.class);

    private final JWTService jwtService;
    private final UserService userService;
    private final String cookieName;

    public RememberMeStep(Handler<RoutingContext> handler, JWTService jwtService, UserService userService, String cookieName) {
        super(handler);
        this.jwtService = jwtService;
        this.userService = userService;
        this.cookieName = cookieName;
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {

        // If the user is present, a valid session exists
        if (routingContext.user() != null) {
            flow.doNext(routingContext);
            return;
        }

        // If there is no remember-me cookie
        if (routingContext.request().getCookie(cookieName) == null) {
            flow.doNext(routingContext);
            return;
        }

        // Extract current user form remember-me cookie
        extractUserFormRememberMeCookie(routingContext.request().getCookie(cookieName))
                .subscribe(
                        user -> {
                            routingContext.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User((user))));
                            flow.exit(this);
                        },
                        throwable -> {
                            logger.warn("An error has occurred when parsing RememberMe cookie", throwable);
                            routingContext.response().removeCookie(cookieName);
                            flow.doNext(routingContext);
                        }
                );
    }

    private Single<User> extractUserFormRememberMeCookie(final Cookie rememberMeCookie) {

        final var rememberMeCookieValue = rememberMeCookie.getValue();

        return jwtService.decode(rememberMeCookieValue, SESSION)
                .flatMap(jwt -> {

                    final var userId = (String) jwt.get(USER_ID_KEY);

                    return userService.findById(userId)
                            .switchIfEmpty(Single.error(new UserNotFoundException(userId)));
                });
    }
}
