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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.model.LoginAttempt;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.exception.authentication.AccountLockedException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginErrorHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginErrorHandler.class);
    private LoginAttemptService loginAttemptService;
    private static final String ERROR_PARAM = "error";
    private static final String ERROR_CODE_PARAM = "error_code";
    private static final String ERROR_DESCRIPTION_PARAM = "error_description";
    private static final String ATTEMPT_ID_PARAM = "attempt_id";
    private static final String ERROR_CONTEXT_KEY = "error";
    private static final String ERROR_CODE_CONTEXT_KEY = "errorCode";
    private static final String ERROR_DESCRIPTION_CONTEXT_KEY = "errorDescription";
    private static final String ERROR_DETAILS_CONTEXT_KEY = "details";
    private static final String ACCOUNT_LOCKED_UNTIL_CONTEXT_KEY = "accountLockedUntil";

    public LoginErrorHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void handle(RoutingContext context) {
        final HttpServerRequest request = context.request();
        final String error = request.getParam(ERROR_PARAM);
        final String errorCode = request.getParam(ERROR_CODE_PARAM);
        final String errorDescription = request.getParam(ERROR_DESCRIPTION_PARAM);

        // no error to handle, continue
        if (error == null) {
            context.next();
            return;
        }

        // put error data in context
        Map<String, Object> errorContext = new HashMap<>();
        errorContext.put(ERROR_CODE_CONTEXT_KEY, errorCode);
        errorContext.put(ERROR_DESCRIPTION_CONTEXT_KEY, errorDescription);
        if (AccountLockedException.ERROR_CODE.equals(errorCode)) {
            // handle account locked exception
            final String attemptId = request.getParam(ATTEMPT_ID_PARAM);
            if (attemptId != null) {
                parseLoginAttempt(attemptId, handler -> {
                    if (handler.failed()) {
                        logger.debug("Failed to get login attempt information", handler.cause());
                        context.next();
                        return;
                    }

                    // get login attempt information
                    LoginAttempt loginAttempt = handler.result();
                    errorContext.put(ERROR_DETAILS_CONTEXT_KEY, Collections.singletonMap(ACCOUNT_LOCKED_UNTIL_CONTEXT_KEY, loginAttempt.getExpireAt()));

                    // add data to the context
                    context.put(ERROR_CONTEXT_KEY, errorContext);
                    context.next();
                });
            }
        } else {
            context.put(ERROR_CONTEXT_KEY, errorContext);
            context.next();
        }
    }

    private void parseLoginAttempt(String attemptId, Handler<AsyncResult<LoginAttempt>> handler) {
        loginAttemptService.findById(attemptId)
                .subscribe(
                        loginAttempt -> handler.handle(Future.succeededFuture(loginAttempt)),
                        error -> handler.handle(Future.failedFuture(error)));

    }
}
