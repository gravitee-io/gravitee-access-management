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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.oauth2.Parameters.CLIENT_ID;
import static io.gravitee.am.common.oauth2.Parameters.USERNAME;
import static io.gravitee.am.common.oidc.Parameters.LOGIN_HINT;
import static io.gravitee.am.gateway.handler.common.utils.UsernameHelper.escapeUsernameParam;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RedirectHandlerImpl implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(RedirectHandlerImpl.class);
    private final String path;

    public RedirectHandlerImpl(String path) {
        this.path = path;
    }

    @Override
    public void handle(RoutingContext context) {
        String redirectUrl = context.get(CONTEXT_PATH) + path;
        try {
            final HttpServerRequest request = context.request();
            final MultiMap queryParams = RequestUtils.getCleanedQueryParams(request);

            escapeUsernameParam(queryParams, LOGIN_HINT);
            escapeUsernameParam(queryParams, USERNAME);

            if (context.get(ConstantKeys.TOKEN_CONTEXT_KEY) != null) {
                queryParams.add(ConstantKeys.TOKEN_CONTEXT_KEY, (String) context.get(ConstantKeys.TOKEN_CONTEXT_KEY));
            }

            // client_id can be added dynamically via external protocol
            if (!queryParams.contains(CLIENT_ID) && request.params().contains(CLIENT_ID)) {
                queryParams.add(CLIENT_ID, request.getParam(CLIENT_ID));
            }

            // Now redirect the user.
            String uri = UriBuilderRequest.resolveProxyRequest(request, redirectUrl, queryParams, true);
            context.response()
                    .putHeader(HttpHeaders.LOCATION, uri)
                    .setStatusCode(302)
                    .end();
        } catch (Exception e) {
            logger.warn("Failed to decode redirect url", e);
            context.response()
                    .putHeader(HttpHeaders.LOCATION, redirectUrl)
                    .setStatusCode(302)
                    .end();
        }
    }

}
