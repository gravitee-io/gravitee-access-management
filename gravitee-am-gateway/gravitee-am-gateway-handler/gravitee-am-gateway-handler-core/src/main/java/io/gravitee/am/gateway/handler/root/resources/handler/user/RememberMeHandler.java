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

import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_ID_KEY;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.SESSION;

/**
 * @author GraviteeSource Team
 */
public class RememberMeHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(RememberMeHandler.class);

    private final JWTService jwtService;
    private final UserService userService;
    private final String cookieName;

    public RememberMeHandler(JWTService jwtService, UserService userService, String cookieName) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.cookieName = cookieName;
    }

    @Override
    public void handle(RoutingContext routingContext) {

        // If the user is present, a valid session exists
        if (routingContext.user() != null) {
            if (routingContext.get(USER_CONTEXT_KEY) == null
                && routingContext.user().getDelegate() instanceof io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User vertxUser) {
                routingContext.put(USER_CONTEXT_KEY, vertxUser.getUser());
            }
            routingContext.next();
            return;
        }

        // If there is no remember-me cookie
        final Cookie rememberMeCookie = routingContext.request().getCookie(cookieName);
        if (rememberMeCookie == null) {
            routingContext.next();
            return;
        }

        // Extract current user form remember-me cookie
        extractUserFormRememberMeCookie(rememberMeCookie)
                .subscribe(
                        user -> {
                            routingContext.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
                            routingContext.put(USER_CONTEXT_KEY, user);
                            routingContext.next();
                        },
                        throwable -> {
                            logger.warn("An error has occurred when parsing RememberMe cookie", throwable);
                            routingContext.response().removeCookie(cookieName);
                            routingContext.next();
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
