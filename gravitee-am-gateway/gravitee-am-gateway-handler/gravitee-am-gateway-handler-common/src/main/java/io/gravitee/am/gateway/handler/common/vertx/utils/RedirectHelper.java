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

package io.gravitee.am.gateway.handler.common.vertx.utils;


import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RedirectHelper {

    /**
     * Compute the URL on which a user has to be redirected by looking into the
     * user session, then the request and finally if no returnUrl is found, forge
     * an URL to authorization endpoint.
     *
     * @param context
     * @param queryParams
     * @return
     */
    public static String getReturnUrl(RoutingContext context, MultiMap queryParams) {
        // look into the session
        if (context.session().get(ConstantKeys.RETURN_URL_KEY) != null) {
            return context.session().get(ConstantKeys.RETURN_URL_KEY);
        }
        // look into the request parameters
        if (context.request().getParam(ConstantKeys.RETURN_URL_KEY) != null) {
            return context.request().getParam(ConstantKeys.RETURN_URL_KEY);
        }
        // fallback to the OAuth 2.0 authorize endpoint
        return UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/oauth/authorize", queryParams, true);
    }

    public static void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url)
                .setStatusCode(302)
                .end();
    }

    /**
     * Retrieve the redirectUrl from the routing context using the {@link #getReturnUrl(RoutingContext, MultiMap)} method
     * and apply the redirect (302 status) on the HttpResponse.
     *
     * @param context
     */
    public static void doRedirect(RoutingContext context) {
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());
        final String redirectUri = getReturnUrl(context, queryParams);
        doRedirect(context.response(), redirectUri);
    }
}
