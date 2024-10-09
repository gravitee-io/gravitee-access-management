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
package io.gravitee.am.gateway.handler.root.resources.handler.user.register;

import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.jwt.JWTException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserTokenRequestParseHandler;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.service.exception.UserAlreadyVerifiedException;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.auth.User;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.common.utils.ConstantKeys.INVALID_TOKEN;
import static java.util.function.Predicate.not;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterVerifyRequestParseHandler extends UserTokenRequestParseHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterVerifyRequestParseHandler.class);
    public static final String REGISTRATION_VERIFY_LINK_EXPIRED = "registration_verify_link_expired";
    public static final String UNEXPECTED_ERROR = "unexpected_error";
    private final Domain domain;

    public RegisterVerifyRequestParseHandler(Domain domain, UserService userService) {
        super(userService);
        this.domain = domain;
    }

    @Override
    protected Handler<AsyncResult<UserToken>> getResultHandler(RoutingContext context, MultiMap queryParams) {
        return handler -> {
            if (handler.failed()) {
                final String errorKey = getErrorKey(handler.cause());
                if(context.session()!=null){
                    context.session().put(ERROR_HASH, HashUtil.generateSHA256(errorKey));
                }
                context.put(ConstantKeys.ERROR_PARAM_KEY, errorKey);
                if (UNEXPECTED_ERROR.equals(errorKey)) {
                    LOGGER.error("An unexpected error has occurred", handler.cause());
                }
            }

            if (handler.succeeded()) {
                UserToken userToken = handler.result();
                context.put(ConstantKeys.USER_CONTEXT_KEY, userToken.getUser());
                context.put(ConstantKeys.CLIENT_CONTEXT_KEY, userToken.getClient());
                queryParams.remove(ConstantKeys.TOKEN_PARAM_KEY);
                var accountSettings = AccountSettings.getInstance(userToken.getClient(), domain);

                var redirectUri = accountSettings
                        .filter(AccountSettings::isSendVerifyRegistrationAccountEmail)
                        .filter(AccountSettings::isAutoLoginAfterRegistration)
                        .map(AccountSettings::getRedirectUriAfterRegistration)
                        .filter(not(String::isBlank));

                if (redirectUri.isPresent()) {
                    var webAuthUser = new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(userToken.getUser());
                    context.setUser(User.newInstance(webAuthUser));
                    context.response()
                            .putHeader(HttpHeaders.LOCATION, ParamUtils.appendQueryParameter(redirectUri.get(), queryParams))
                            .setStatusCode(302)
                            .end();
                    return;
                }
            }

            context.request().params().remove(ConstantKeys.TOKEN_PARAM_KEY);
            context.next();
        };
    }

    @Override
    protected Maybe<UserToken> parseToken(String token) {
        return userService.confirmVerifyRegistration(token);
    }

    private static String getErrorKey(Throwable cause) {
        if (isExpirationException(cause)) {
            return REGISTRATION_VERIFY_LINK_EXPIRED;
        }
        if (isTokenError(cause)) {
            return INVALID_TOKEN;
        }
        return UNEXPECTED_ERROR;
    }

    private static boolean isTokenError(Throwable cause) {
        return cause instanceof JWTException;
    }

    private static boolean isExpirationException(Throwable cause) {
        return cause instanceof ExpiredJWTException || cause instanceof UserAlreadyVerifiedException;
    }
}
