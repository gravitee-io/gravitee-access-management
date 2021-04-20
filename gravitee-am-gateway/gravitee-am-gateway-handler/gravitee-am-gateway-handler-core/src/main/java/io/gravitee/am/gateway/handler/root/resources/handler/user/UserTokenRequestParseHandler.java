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

import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserTokenRequestParseHandler extends UserRequestHandler {

    private static final String TOKEN_PARAM = "token";
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
        String clientId = context.request().getParam(Parameters.CLIENT_ID);

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
            Map<String, String> params = new HashMap<>();
            params.put("error", "token_missing");
            params.computeIfAbsent(Parameters.CLIENT_ID, val -> clientId);
            redirectToPage(context, params);
            return;
        }

        parseToken(
            token,
            handler -> {
                if (handler.failed()) {
                    Map<String, String> params = new HashMap<>();
                    params.put("error", "invalid_token");
                    params.computeIfAbsent(Parameters.CLIENT_ID, val -> clientId);
                    redirectToPage(context, params);
                    return;
                }

                // put user and client in context
                UserToken userToken = handler.result();
                context.put("user", userToken.getUser());
                context.put("client", userToken.getClient());
                context.next();
            }
        );
    }

    private void parseToken(String token, Handler<AsyncResult<UserToken>> handler) {
        userService
            .verifyToken(token)
            .subscribe(
                userToken -> handler.handle(Future.succeededFuture(userToken)),
                error -> handler.handle(Future.failedFuture(error)),
                () -> handler.handle(Future.failedFuture(new InvalidTokenException("The JWT token is invalid")))
            );
    }
}
