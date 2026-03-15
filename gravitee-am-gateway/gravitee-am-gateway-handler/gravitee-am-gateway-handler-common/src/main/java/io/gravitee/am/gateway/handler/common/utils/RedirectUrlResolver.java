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
package io.gravitee.am.gateway.handler.common.utils;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Map;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

public class RedirectUrlResolver {

    public String resolveRedirectUrl(RoutingContext routingContext){
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        return resolveRedirectUrl(routingContext, queryParams);
    }

    public String resolveRedirectUrl(RoutingContext routingContext, String path){
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        return resolveRedirectUrl0(routingContext, queryParams,  path);
    }

    public String resolveRedirectUrl(RoutingContext routingContext, Map<String, String> extraParams, String path){
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        extraParams.forEach(queryParams::add);
        return resolveRedirectUrl0(routingContext, queryParams,  path);
    }

    public String resolveRedirectUrl(RoutingContext context, MultiMap queryParams) {
        // look into the session
        if (context.session().get(ConstantKeys.RETURN_URL_KEY) != null) {
            return context.session().get(ConstantKeys.RETURN_URL_KEY);
        }
        // look into the request parameters
        if (context.request().getParam(ConstantKeys.RETURN_URL_KEY) != null) {
            return context.request().getParam(ConstantKeys.RETURN_URL_KEY);
        }
        // fallback to the OAuth 2.0 authorize endpoint
        return resolveRedirectUrl0(context, queryParams,  "/oauth/authorize");
    }

    private String resolveRedirectUrl0(RoutingContext context, MultiMap queryParams, String path) {
        // look into the session
        if (context.session().get(ConstantKeys.RETURN_URL_KEY) != null) {
            return context.session().get(ConstantKeys.RETURN_URL_KEY);
        }
        // look into the request parameters
        if (context.request().getParam(ConstantKeys.RETURN_URL_KEY) != null) {
            return context.request().getParam(ConstantKeys.RETURN_URL_KEY);
        }
        // fallback to the OAuth 2.0 authorize endpoint
        return UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + path, queryParams, true);
    }


}
