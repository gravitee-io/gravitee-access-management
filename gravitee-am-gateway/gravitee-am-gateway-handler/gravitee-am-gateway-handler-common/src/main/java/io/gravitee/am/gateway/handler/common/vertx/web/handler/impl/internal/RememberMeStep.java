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

import io.gravitee.am.common.exception.authentication.AccountIllegalStateException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.common.utils.Tuple;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.rxjava3.ext.web.RoutingContext;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_ID_KEY;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.SESSION;
import static io.gravitee.am.gateway.handler.common.vertx.web.RoutingContextHelper.setUser;
import static java.util.Optional.ofNullable;

import lombok.CustomLog;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * @author Aurélien PACAUD (aurelien.pacaud at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class RememberMeStep extends AuthenticationFlowStep {


    private final JWTService jwtService;
    private final UserGatewayService userService;
    private final String cookieName;

    public RememberMeStep(Handler<RoutingContext> handler, JWTService jwtService, UserGatewayService userService, String cookieName) {
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

        // if client is unknown ignore the cookie otherwise we cannot verify it
        Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        if (client == null) {
            flow.doNext(routingContext);
            return;
        }

        // Extract current user form remember-me cookie
        extractUserFormRememberMeCookie(client, routingContext.request().getCookie(cookieName))
                .map(tuple -> {
                    final var user = tuple.getT1();
                    final var cookie = tuple.getT2();

                    var dates = List.of(
                            ofNullable(user.getLastPasswordReset()),
                            ofNullable(user.getLastUsernameReset()),
                            ofNullable(user.getLastLogoutAt())
                    );

                    // "iat" claim is stored in epoch seconds format, compare both dates in milliseconds
                    // and tolerate the truncated second as SSOSessionHandler does
                    final long cookieIssuedAt = cookie.getIat() * 1000L;
                    final boolean isIllegalStateAccount = dates.stream()
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .map(Date::getTime)
                            .anyMatch(time -> time - cookieIssuedAt > 1000L);

                    if (isIllegalStateAccount) {
                        throw new AccountIllegalStateException("The user account is in an illegal state. Please contact your administrator.");
                    }

                    return user;
                })
                .subscribe(
                        user -> {
                            setUser(routingContext, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user));
                            flow.exit(this);
                        },
                        throwable -> {
                            log.warn("An error has occurred when parsing RememberMe cookie", throwable);
                            routingContext.response().removeCookie(cookieName);
                            flow.doNext(routingContext);
                        }
                );
    }

    private Single<Tuple<User, JWT>> extractUserFormRememberMeCookie(final Client client, final Cookie rememberMeCookie) {

        final var rememberMeCookieValue = rememberMeCookie.getValue();

        return jwtService.decodeAndVerify(rememberMeCookieValue, client, SESSION)
                .flatMap(jwt -> {

                    final var userId = (String) jwt.get(USER_ID_KEY);

                    return userService.findById(userId)
                            .switchIfEmpty(Single.error(new UserNotFoundException(userId)))
                            .map(user -> Tuple.of(user, jwt));
                });
    }
}
