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
package io.gravitee.am.gateway.handler.root.resources.handler.error;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.common.utils.ConstantKeys.SERVER_ERROR;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractErrorHandler implements Handler<RoutingContext> {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    protected final String errorPage;

    protected AbstractErrorHandler(String errorPage) {
        this.errorPage = errorPage;
    }

    @Override
    public final void handle(RoutingContext routingContext) {
        if (routingContext.failed()) {
            try {
                doHandle(routingContext);
            } catch (Exception e) {
                logger.error("Unable to handle error response", e);
                String errorPageURL = routingContext.get(CONTEXT_PATH) + errorPage;

                final HttpServerRequest request = routingContext.request();
                // prepare query parameters
                MultiMap parameters = RequestUtils.getCleanedQueryParams(routingContext.request());
                // get client if exists
                Client client = routingContext.get(CLIENT_CONTEXT_KEY);
                if (client != null) {
                    parameters.set(Parameters.CLIENT_ID, client.getClientId());
                } else if (request.getParam(Parameters.CLIENT_ID) != null) {
                    parameters.set(Parameters.CLIENT_ID, (request.getParam(Parameters.CLIENT_ID)));
                }

                // append error information
                parameters.set(ConstantKeys.ERROR_PARAM_KEY, SERVER_ERROR);
                parameters.set(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY, "Unexpected error occurred");

                String toHash = SERVER_ERROR + "$" + "Unexpected error occurred";
                String hash = HashUtil.generateSHA256(toHash);
                if (routingContext.session() != null) {
                    routingContext.session().put(ERROR_HASH, hash);
                }

                // redirect
                String proxiedErrorPage = UriBuilderRequest.resolveProxyRequest(request, errorPageURL, parameters, true);
                doRedirect(routingContext.response(), proxiedErrorPage);
            }
        }
    }

    protected abstract void doHandle(RoutingContext routingContext);

    protected void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url);
        response.setStatusCode(302);
        response.end();
    }
}
