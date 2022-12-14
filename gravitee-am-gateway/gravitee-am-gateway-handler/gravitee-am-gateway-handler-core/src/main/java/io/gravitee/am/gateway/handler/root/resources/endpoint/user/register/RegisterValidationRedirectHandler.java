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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.register;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterValidationRedirectHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(RegisterValidationRedirectHandler.class);

    private final UserService userService; // should use io.gravitee.am.gateway.handler.root.service.user with a dedicated method to mark the profile a pending

    public RegisterValidationRedirectHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext context) {
        // TODO condition this handle method execution with registration settings

        User user = context.get(ConstantKeys.USER_CONTEXT_KEY);
        user.setEnabled(false); // TODO should we create a new pending state as we have notComplete one ?

        userService.update(user)
                .subscribe(disabledUser -> {

                    // add the user to the session
                    context.setUser(io.vertx.reactivex.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));

                    final MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());
                    String uri = UriBuilderRequest.resolveProxyRequest(
                            context.request(),
                            context.get(CONTEXT_PATH) + RootProvider.PATH_REGISTER_VALIDATION, queryParams, true);
                    doRedirect(context.response(), uri);
                }, context::fail);

    }
    private void doRedirect(HttpServerResponse response, String url) {
        response
                .putHeader(HttpHeaders.LOCATION, url)
                .setStatusCode(302)
                .end();
    }
}
