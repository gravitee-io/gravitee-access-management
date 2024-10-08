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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.gateway.handler.root.resources.handler.error.AbstractErrorHandler;
import io.gravitee.am.service.exception.EmailFormatInvalidException;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_PARAM_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterFailureHandler extends AbstractErrorHandler {

    public RegisterFailureHandler() {
        super(RootProvider.PATH_ERROR);
    }

    @Override
    protected void doHandle(RoutingContext context) {
        // prepare response
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());
        // if failure, return to the register page with an error
        Throwable cause = context.failure();
        if (cause instanceof InvalidUserException) {
            queryParams.set(ConstantKeys.WARNING_PARAM_KEY, "invalid_user_information");
        } else if (cause instanceof EmailFormatInvalidException) {
            queryParams.set(ConstantKeys.WARNING_PARAM_KEY, "invalid_email");
        } else {
            logger.error("An error occurs while ending user registration", cause);
            queryParams.set(ConstantKeys.ERROR_PARAM_KEY, "registration_failed");
            if(context.session()!=null && !context.session().isDestroyed()){
                context.session().put(ConstantKeys.ERROR_HASH, HashUtil.generateSHA256("registration_failed"));
            }
        }
        redirectToPage(context, queryParams, cause);
    }

    private void redirectToPage(RoutingContext context, MultiMap queryParams, Throwable... exceptions) {
        try {
            if (exceptions != null && exceptions.length > 0) {
                logger.debug("Error user actions : " + queryParams.get(ERROR_PARAM_KEY), exceptions[0]);
            }
            String uri = UriBuilderRequest.resolveProxyRequest(context.request(), context.request().path(), queryParams, true);
            doRedirect(context.response(), uri);
        } catch (Exception ex) {
            logger.error("An error occurs while redirecting to {}", context.request().absoluteURI(), ex);
            context
                    .response()
                    .setStatusCode(503)
                    .end();
        }
    }
}
