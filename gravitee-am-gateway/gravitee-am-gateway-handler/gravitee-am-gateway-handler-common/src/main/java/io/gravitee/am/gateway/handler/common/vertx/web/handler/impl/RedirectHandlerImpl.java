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

import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

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
