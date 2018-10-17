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
package io.gravitee.am.gateway.handler.vertx.handler.oauth2.request;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.vertx.reactivex.core.http.HttpServerRequest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class AuthorizationRequestFactory {

    public AuthorizationRequest create(HttpServerRequest request) {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(request.params().get(OAuth2Constants.CLIENT_ID));
        authorizationRequest.setResponseType(request.params().get(OAuth2Constants.RESPONSE_TYPE));
        authorizationRequest.setRedirectUri(request.params().get(OAuth2Constants.REDIRECT_URI));
        String scope = request.params().get(OAuth2Constants.SCOPE);
        authorizationRequest.setScopes(scope != null ? new HashSet<>(Arrays.asList(scope.split("\\s+"))) : null);
        authorizationRequest.setState(request.params().get(OAuth2Constants.STATE));
        authorizationRequest.setRequestParameters(extractRequestParameters(request));
        authorizationRequest.setAdditionalParameters(extractAdditionalParameters(request));
        return authorizationRequest;
    }

    private MultiValueMap<String, String> extractRequestParameters(HttpServerRequest request) {
        MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>(request.params().size());
        request.params().getDelegate().entries().forEach(entry -> requestParameters.add(entry.getKey(), entry.getValue()));
        return requestParameters;
    }

    private MultiValueMap<String, String> extractAdditionalParameters(HttpServerRequest request) {
        final Set<String> restrictedParameters = Stream.concat(Stream.of(Parameters.values()).map(p -> p.value()),
                Stream.of(io.gravitee.am.common.oidc.Parameters.values()).map(p -> p.value())).collect(Collectors.toSet());

        MultiValueMap<String, String> additionalParameters = new LinkedMultiValueMap<>();
        request.params().getDelegate().entries().stream().filter(entry -> !restrictedParameters.contains(entry.getKey())).forEach(entry -> additionalParameters.add(entry.getKey(), entry.getValue()));
        return additionalParameters;
    }
}
