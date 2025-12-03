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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.password;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserRequestHandler;
import io.gravitee.am.gateway.handler.root.service.response.ResetPasswordResponse;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.jsoup.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import static io.gravitee.am.common.utils.ConstantKeys.CLAIM_QUERY_PARAM;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResetPasswordSubmissionEndpoint extends UserRequestHandler {

    public static final String GATEWAY_ENDPOINT_RESET_PWD_KEEP_PARAMS = "legacy.resetPassword.keepParams";
    private static final Logger logger = LoggerFactory.getLogger(ResetPasswordSubmissionEndpoint.class);
    private final UserService userService;

    private final boolean keepParams;

    public ResetPasswordSubmissionEndpoint(UserService userService, Environment environment) {
        this.userService = userService;
        this.keepParams = environment.getProperty(GATEWAY_ENDPOINT_RESET_PWD_KEEP_PARAMS, boolean.class, true);
    }

    @Override
    public void handle(RoutingContext context) {
        // retrieve the client in context
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        // retrieve the user in context
        User user = context.get(ConstantKeys.USER_CONTEXT_KEY);

        // set user password entered during reset password process
        String password = context.request().getParam(ConstantKeys.PASSWORD_PARAM_KEY);
        user.setPassword(password);

        // reset password
        resetPassword(client, user, getAuthenticatedUser(context), h -> {
            // prepare response
            MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());

            // if failure, return to the reset password page with an error
            if (h.failed()) {
                logger.error("An error occurs while ending user reset password process", h.cause());
                queryParams.set(ConstantKeys.ERROR_PARAM_KEY, "reset_password_failed");
                redirectToPage(context, queryParams, h.cause());
                return;
            }
            // handle response
            ResetPasswordResponse resetPasswordResponse = h.result();
            // if auto login option is enabled add the user to the session
            if (resetPasswordResponse.isAutoLogin()) {
                context.setUser(io.vertx.rxjava3.ext.auth.User.newInstance(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(resetPasswordResponse.getUser())));
            } else {
                // Clear session to prevent interference with subsequent flows
                if (context.session() != null) {
                    context.session().destroy();
                }
            }
            // do on force password reset
            if (context.get(ConstantKeys.TOKEN_CONTEXT_KEY) != null) {
                JWT jwt = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
                String claimsRequestParameter = (String) jwt.get(CLAIM_QUERY_PARAM);
                if (!StringUtil.isBlank(claimsRequestParameter)) {
                    final var queryParamsToRestore = RequestUtils.getQueryParams(claimsRequestParameter, false);
                    context.response()
                            .putHeader(io.vertx.core.http.HttpHeaders.LOCATION, getReturnUrl(context, queryParamsToRestore))
                            .setStatusCode(302)
                            .end();
                    return;
                }
                queryParams.remove(ConstantKeys.TOKEN_CONTEXT_KEY);
            }

            // no redirect uri has been set, redirect to the default page
            if (resetPasswordResponse.getRedirectUri() == null || resetPasswordResponse.getRedirectUri().isEmpty()) {
                queryParams.set(ConstantKeys.SUCCESS_PARAM_KEY, "reset_password_completed");
                redirectToPage(context, queryParams);
                return;
            }
            // else, redirect to the custom redirect_uri
            var redirectTo = keepParams ? ParamUtils.appendQueryParameter(resetPasswordResponse.getRedirectUri(), queryParams) : resetPasswordResponse.getRedirectUri();
            context.response()
                    .putHeader(HttpHeaders.LOCATION, redirectTo)
                    .setStatusCode(302)
                    .end();
        });
    }

    private void resetPassword(Client client, User user, io.gravitee.am.identityprovider.api.User principal, Handler<AsyncResult<ResetPasswordResponse>> handler) {
        userService.resetPassword(client, user, principal)
                .subscribe(
                        response -> handler.handle(Future.succeededFuture(response)),
                        error -> handler.handle(Future.failedFuture(error)));
    }
}
