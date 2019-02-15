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
package io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.user;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidTokenException;
import io.gravitee.am.gateway.handler.user.UserService;
import io.gravitee.am.gateway.handler.user.model.UserToken;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserTokenRequestParseHandler extends UserRequestHandler {

    private static final String TOKEN_PARAM  = "token";
    private static final String ERROR_PARAM = "error";
    private static final String SUCCESS_PARAM = "success";
    private static final String WARNING_PARAM = "warning";

    private UserService userService;

    public UserTokenRequestParseHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext context) {
        String token = context.request().getParam(TOKEN_PARAM);
        String error = context.request().getParam(ERROR_PARAM);
        String success = context.request().getParam(SUCCESS_PARAM);
        String warning = context.request().getParam(WARNING_PARAM);

        // user action completed, continue
        if (success != null) {
            context.next();
            return;
        }

        // user has been redirected due to warning, continue
        if (warning != null && token == null) {
            context.next();
            return;
        }

        // user has been redirected due to errors, continue
        if (error != null) {
            context.next();
            return;
        }

        // missing required token param
        // redirect user error message
        if (token == null) {
            redirectToPage(context, Collections.singletonMap("error","token_missing"));
            return;
        }

        parseToken(token, handler -> {
            if (handler.failed()) {
                redirectToPage(context, Collections.singletonMap("error","invalid_token"));
                return;
            }

            // put user and client in context
            UserToken userToken = handler.result();
            context.put("user", userToken.getUser());
            context.put("client", userToken.getClient());
            context.next();
        });
    }

    private void parseToken(String token, Handler<AsyncResult<UserToken>> handler) {
        userService.verifyToken(token)
                .subscribe(
                        userToken -> handler.handle(Future.succeededFuture(userToken)),
                        error -> handler.handle(Future.failedFuture(error)),
                        () -> handler.handle(Future.failedFuture(new InvalidTokenException("The JWT token is invalid"))));
    }
}
