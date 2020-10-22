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
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserTokenRequestParseHandler extends UserRequestHandler {

    private final UserService userService;

    public UserTokenRequestParseHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext context) {
        String token = context.request().getParam(ConstantKeys.TOKEN_PARAM_KEY);
        String error = context.request().getParam(ConstantKeys.ERROR_PARAM_KEY);
        String success = context.request().getParam(ConstantKeys.SUCCESS_PARAM_KEY);
        String warning = context.request().getParam(ConstantKeys.WARNING_PARAM_KEY);

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

        MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());

        // missing required token param
        // redirect user error message
        if (token == null) {
            queryParams.set(ConstantKeys.ERROR_PARAM_KEY, "token_missing");
            redirectToPage(context, queryParams);
            return;
        }

        parseToken(token, handler -> {
            if (handler.failed()) {
                queryParams.set(ConstantKeys.ERROR_PARAM_KEY, "invalid_token");
                redirectToPage(context, queryParams);
                return;
            }

            // put user and client in context
            UserToken userToken = handler.result();
            context.put(ConstantKeys.USER_CONTEXT_KEY, userToken.getUser());
            context.put(ConstantKeys.CLIENT_CONTEXT_KEY, userToken.getClient());
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
