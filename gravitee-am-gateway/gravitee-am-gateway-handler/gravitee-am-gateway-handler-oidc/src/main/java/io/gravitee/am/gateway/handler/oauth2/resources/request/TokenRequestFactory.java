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
package io.gravitee.am.gateway.handler.oauth2.resources.request;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerResponse;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpVersion;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class TokenRequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(TokenRequestFactory.class);

    public TokenRequest create(RoutingContext context) {
        HttpServerRequest request = context.request();
        TokenRequest tokenRequest = new TokenRequest();
        // set technical information
        tokenRequest.setTimestamp(System.currentTimeMillis());
        tokenRequest.setId(RandomString.generate());
        tokenRequest.setTransactionId(RandomString.generate());
        tokenRequest.setUri(request.uri());
        tokenRequest.setOrigin(extractOrigin(context));
        tokenRequest.setContextPath(request.path() != null ? request.path().split("/")[0] : null);
        tokenRequest.setPath(request.path());
        tokenRequest.setHeaders(extractHeaders(request));
        tokenRequest.setParameters(extractRequestParameters(request));
        tokenRequest.setSslSession(request.sslSession());
        tokenRequest.setMethod(request.method() != null ? HttpMethod.valueOf(request.method().name()) : null);
        tokenRequest.setScheme(request.scheme());
        tokenRequest.setVersion(request.version() != null ? HttpVersion.valueOf(request.version().name()) : null);
        tokenRequest.setRemoteAddress(request.remoteAddress() != null ? request.remoteAddress().host() : null);
        tokenRequest.setLocalAddress(request.localAddress() != null ? request.localAddress().host() : null);
        tokenRequest.setHttpResponse(new VertxHttpServerResponse(request.getDelegate(), new VertxHttpServerRequest(request.getDelegate()).metrics()));

        // set OAuth 2.0 information
        tokenRequest.setClientId(request.params().get(Parameters.CLIENT_ID));
        tokenRequest.setGrantType(request.params().get(Parameters.GRANT_TYPE));
        String scope = request.params().get(Parameters.SCOPE);
        tokenRequest.setScopes(scope != null && !scope.isEmpty() ? new HashSet<>(Arrays.asList(scope.split("\\s+"))) : null);
        tokenRequest.setAdditionalParameters(extractAdditionalParameters(request));
        return tokenRequest;
    }

    private MultiValueMap<String, String> extractRequestParameters(HttpServerRequest request) {
        MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>(request.params().size());
        request.params().entries().forEach(entry -> requestParameters.add(entry.getKey(), entry.getValue()));
        return requestParameters;
    }

    private MultiValueMap<String, String> extractAdditionalParameters(HttpServerRequest request) {
        final Set<String> restrictedParameters = Stream.concat(Parameters.values.stream(),
                io.gravitee.am.common.oidc.Parameters.values.stream()).collect(Collectors.toSet());

        MultiValueMap<String, String> additionalParameters = new LinkedMultiValueMap<>();
        request.params().entries().stream().filter(entry -> !restrictedParameters.contains(entry.getKey())).forEach(entry -> additionalParameters.add(entry.getKey(), entry.getValue()));
        return additionalParameters;
    }

    private HttpHeaders extractHeaders(HttpServerRequest request) {
        MultiMap vertxHeaders = request.headers();
        if (vertxHeaders != null) {
            HttpHeaders headers = new HttpHeaders(vertxHeaders.size());
            for (Map.Entry<String, String> header : vertxHeaders.entries()) {
                headers.add(header.getKey(), header.getValue());
            }
            return headers;
        }
        return null;
    }

    private String extractOrigin(RoutingContext context) {
        String basePath = "/";
        try {
            basePath = UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH));
        } catch (Exception e) {
            logger.error("Unable to resolve OAuth 2.0 Token Request origin uri", e);
        }
        return basePath;
    }
}
