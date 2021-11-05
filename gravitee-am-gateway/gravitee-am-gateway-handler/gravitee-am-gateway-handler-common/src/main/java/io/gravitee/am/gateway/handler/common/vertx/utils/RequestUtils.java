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

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.common.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.net.SocketAddress;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;

import java.net.URI;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.SUCCESS_PARAM_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RequestUtils {

    public static String remoteAddress(HttpServerRequest httpServerRequest) {
        return remoteAddress(httpServerRequest.getDelegate());
    }

    public static String userAgent(HttpServerRequest httpServerRequest) {
        return userAgent(httpServerRequest.getDelegate());
    }

    public static String remoteAddress(io.vertx.core.http.HttpServerRequest httpServerRequest) {
        String xForwardedFor = httpServerRequest.getHeader(HttpHeaders.X_FORWARDED_FOR);
        String remoteAddress;

        if (xForwardedFor != null && xForwardedFor.length() > 0) {
            int idx = xForwardedFor.indexOf(',');

            remoteAddress = (idx != -1) ? xForwardedFor.substring(0, idx) : xForwardedFor;

            idx = remoteAddress.indexOf(':');

            remoteAddress = (idx != -1) ? remoteAddress.substring(0, idx).trim() : remoteAddress.trim();
        } else {
            SocketAddress address = httpServerRequest.remoteAddress();
            remoteAddress = (address != null) ? address.host() : null;
        }

        return remoteAddress;
    }

    public static String userAgent(io.vertx.core.http.HttpServerRequest httpServerRequest) {
        return httpServerRequest.getHeader(HttpHeaders.USER_AGENT);
    }

    /**
     * Extract query parameters from request as a {@link MultiMap}
     *
     * @param httpServerRequest the request from which extract the query parameters.
     * @return all query parameters as a {@link MultiMap}.
     */
    public static MultiMap getQueryParams(HttpServerRequest httpServerRequest) {

        return getQueryParams(httpServerRequest.uri());
    }

    /**
     * Same as {@link #getQueryParams(HttpServerRequest)} but removes some query parameters used internally (eg: error, error_description, success, ...).
     *
     * @param httpServerRequest the request from which extract the query parameters.
     * @return all cleaned query parameters as a {@link MultiMap}.
     */
    public static MultiMap getCleanedQueryParams(HttpServerRequest httpServerRequest) {
        return getCleanedQueryParams(httpServerRequest.uri());
    }

    /**
     * Same as {@link #getQueryParams(HttpServerRequest)} but removes some query parameters used internally (eg: error, error_description, success, ...).
     *
     * @param uri the request uri from which extract the query parameters.
     * @return all cleaned query parameters as a {@link MultiMap}.
     */
    public static MultiMap getCleanedQueryParams(String uri) {
        final MultiMap queryParams = getQueryParams(uri);
        queryParams.remove(ConstantKeys.ERROR_PARAM_KEY);
        queryParams.remove(ConstantKeys.ERROR_DESCRIPTION_PARAM_KEY);
        queryParams.remove(ConstantKeys.WARNING_PARAM_KEY);
        queryParams.remove(SUCCESS_PARAM_KEY);
        return queryParams;
    }

    /**
     * Same as {@link #getQueryParams(HttpServerRequest)} but taking a complete url.
     *
     * @param url the url from which extract the query parameters.
     * @return all query parameters as a {@link MultiMap}.
     */
    public static MultiMap getQueryParams(String url) {

        return getQueryParams(url, true);
    }

    /**
     * Same as {@link #getQueryParams(HttpServerRequest)} but taking an url or only the query string part (eg: <code>hasPath</code>).
     *
     * @param queryString the url from which extract the query parameters.
     * @param hasPath flag indicating if the <code>queryString</code> is a complete url or is just the query string part.
     * @return all query parameters as a {@link MultiMap}.
     */
    public static MultiMap getQueryParams(String queryString, boolean hasPath) {
        MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
        if (queryString == null) {
            return queryParams;
        }

        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(queryString, hasPath);
        queryStringDecoder.parameters().forEach(queryParams::add);
        return queryParams;
    }

    public static String getDomain(String url) {
        try {
            URI uri = new URI(url);
            String domain = uri.getHost();
            return domain.startsWith("www.") ? domain.substring(4) : domain;
        } catch (Exception ex) {
            return null;
        }
    }
}
