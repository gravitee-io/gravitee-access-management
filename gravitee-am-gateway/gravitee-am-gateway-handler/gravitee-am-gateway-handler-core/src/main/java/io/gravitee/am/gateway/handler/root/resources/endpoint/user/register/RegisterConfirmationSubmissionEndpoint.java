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
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserRequestHandler;
import io.gravitee.am.gateway.handler.root.service.response.RegistrationResponse;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterConfirmationSubmissionEndpoint extends UserRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(RegisterConfirmationSubmissionEndpoint.class);

    private final UserService userService;

    public RegisterConfirmationSubmissionEndpoint(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext context) {
        // retrieve the client in context
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        // retrieve the user in context
        User user = context.get(ConstantKeys.USER_CONTEXT_KEY);

        // set user password entered during confirmation registration process
        String password = context.request().getParam(ConstantKeys.PASSWORD_PARAM_KEY);
        user.setPassword(password);

        // confirm registration
        confirmRegistration(client, user, getAuthenticatedUser(context), h -> {
            // prepare response
            MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());

            // if failure, return to the registration confirmation page with an error
            if (h.failed()) {
                logger.error("An error occurs while ending user registration", h.cause());
                queryParams.set(ConstantKeys.ERROR_PARAM_KEY, "registration_failed");
                redirectToPage(context, queryParams, h.cause());
                return;
            }
            // handle response
            RegistrationResponse registrationResponse = h.result();
            // if auto login option is enabled add the user to the session
            if (registrationResponse.isAutoLogin()) {
                context.setUser(io.vertx.reactivex.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(registrationResponse.getUser())));
            }
            // no redirect uri has been set, redirect to the default page
            if (registrationResponse.getRedirectUri() == null || registrationResponse.getRedirectUri().isEmpty()) {
                queryParams.set(ConstantKeys.SUCCESS_PARAM_KEY, "registration_completed");
                redirectToPage(context, queryParams);
                return;
            }
            // else, redirect to the custom redirect_uri
            context.response()
                    .putHeader(HttpHeaders.LOCATION, registrationResponse.getRedirectUri())
                    .setStatusCode(302)
                    .end();
        });
    }

    private void confirmRegistration(Client client, User user, io.gravitee.am.identityprovider.api.User principal, Handler<AsyncResult<RegistrationResponse>> handler) {
        userService.confirmRegistration(client, user, principal)
                .subscribe(
                        response -> handler.handle(Future.succeededFuture(response)),
                        error -> handler.handle(Future.failedFuture(error)));
    }
}
