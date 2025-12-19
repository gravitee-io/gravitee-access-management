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

import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;

/**
 * @author GraviteeSource Team
 */
public class RememberedLoginRedirectToAuthorizeHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext context) {
        // redirect back to /oauth/authorize while preserving original request parameters
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());
        final String contextPath = context.get(CONTEXT_PATH) != null ? context.get(CONTEXT_PATH) : "";
        final String location = resolveProxyRequest(context.request(), contextPath + "/oauth/authorize", queryParams, true);

        doRedirect(context.response(), location);
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url)
                .setStatusCode(302)
                .end();
    }
}


