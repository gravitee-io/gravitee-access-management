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
package io.gravitee.am.gateway.handler.vertx.auth.handler.impl;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.ChainAuthHandlerImpl;
import io.vertx.ext.web.handler.impl.HttpStatusException;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientChainAuthHandler extends ChainAuthHandlerImpl {

    /**
     * Override process exception to handle custom OAuth 2.0 invalid client exception
     * @param ctx routing context
     * @param exception exception thrown
     */
    @Override
    protected void processException(RoutingContext ctx, Throwable exception) {
        if (exception != null) {
            if (exception instanceof HttpStatusException) {
                final int statusCode = ((HttpStatusException) exception).getStatusCode();
                if (statusCode == 401) {
                    // client authentication has failed return invalid client exception
                    ctx.fail(new InvalidClientException("Client authentication failed due to unknown or invalid client"));
                    return;
                }
            }
        }
        super.processException(ctx, exception);
    }

    @Override
    protected String authenticateHeader(RoutingContext context) {
        return context.request().headers().contains(HttpHeaders.AUTHORIZATION) ?  "Basic realm=\"gravitee-io\"" : null;
    }
}
