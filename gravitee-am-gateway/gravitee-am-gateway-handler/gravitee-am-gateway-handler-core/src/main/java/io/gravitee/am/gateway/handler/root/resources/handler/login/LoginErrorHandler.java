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

import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginErrorHandler implements Handler<RoutingContext> {

    private static final String ERROR_PARAM = "error";
    private static final String ERROR_CODE_PARAM = "error_code";
    private static final String ERROR_DESCRIPTION_PARAM = "error_description";
    private static final String ERROR_CONTEXT_KEY = "error";
    private static final String ERROR_CODE_CONTEXT_KEY = "errorCode";
    private static final String ERROR_DESCRIPTION_CONTEXT_KEY = "errorDescription";

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
        context.put(ERROR_CONTEXT_KEY, errorContext);
        context.next();
    }
}
