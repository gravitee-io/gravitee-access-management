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
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerResponse;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.AuthenticationFlowContext;
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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class AuthorizationRequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationRequestFactory.class);

    public AuthorizationRequest create(RoutingContext context) {
        HttpServerRequest request = context.request();
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        // set technical information
        authorizationRequest.setTimestamp(System.currentTimeMillis());
        authorizationRequest.setId(RandomString.generate());
        authorizationRequest.setTransactionId(context.session().get(ConstantKeys.TRANSACTION_ID_KEY));

        if (context.get(ConstantKeys.AUTH_FLOW_CONTEXT_KEY) != null) {
            AuthenticationFlowContext authFlowContext = context.get(ConstantKeys.AUTH_FLOW_CONTEXT_KEY);
            authorizationRequest.setContextVersion(authFlowContext.getVersion());
            authorizationRequest.getContext().put(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY, authFlowContext.getData());
        }

        authorizationRequest.setUri(request.uri());
        authorizationRequest.setOrigin(extractOrigin(context));
        authorizationRequest.setContextPath(request.path() != null ? request.path().split("/")[0] : null);
        authorizationRequest.setPath(request.path());
        authorizationRequest.setHeaders(extractHeaders(request));
        authorizationRequest.setParameters(extractRequestParameters(request));
        authorizationRequest.setSslSession(request.sslSession());
        authorizationRequest.setMethod(request.method() != null ? HttpMethod.valueOf(request.method().name()) : null);
        authorizationRequest.setScheme(request.scheme());
        authorizationRequest.setVersion(request.version() != null ? HttpVersion.valueOf(request.version().name()) : null);
        authorizationRequest.setRemoteAddress(request.remoteAddress() != null ? request.remoteAddress().host() : null);
        authorizationRequest.setLocalAddress(request.localAddress() != null ? request.localAddress().host() : null);
        authorizationRequest.setHttpResponse(new VertxHttpServerResponse(request.getDelegate(), new VertxHttpServerRequest(request.getDelegate()).metrics()));

        // set OAuth 2.0 information
        authorizationRequest.setClientId(request.params().get(Parameters.CLIENT_ID));
        authorizationRequest.setResponseType(request.params().get(Parameters.RESPONSE_TYPE));
        authorizationRequest.setRedirectUri(request.params().get(Parameters.REDIRECT_URI));
        String scope = request.params().get(Parameters.SCOPE);
        authorizationRequest.setScopes(scope != null && !scope.isEmpty() ? new HashSet<>(Arrays.asList(scope.split("\\s+"))) : null);
        authorizationRequest.setState(request.params().get(Parameters.STATE));
        authorizationRequest.setResponseMode(request.params().get(Parameters.RESPONSE_MODE));
        authorizationRequest.setAdditionalParameters(extractAdditionalParameters(request));
        authorizationRequest.setApproved(Boolean.TRUE.equals(context.session().get(ConstantKeys.USER_CONSENT_APPROVED_KEY)));

        // set OIDC information
        String prompt = request.params().get(io.gravitee.am.common.oidc.Parameters.PROMPT);
        authorizationRequest.setPrompts(prompt != null ? new HashSet<>(Arrays.asList(prompt.split("\\s+"))) : Collections.emptySet());

        context.put(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);

        return authorizationRequest;
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
            logger.error("Unable to resolve OAuth 2.0 Authorization Request origin uri", e);
        }
        return basePath;
    }
}
