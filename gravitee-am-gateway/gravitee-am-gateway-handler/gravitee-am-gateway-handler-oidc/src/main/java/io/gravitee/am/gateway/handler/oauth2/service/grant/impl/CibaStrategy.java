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
package io.gravitee.am.gateway.handler.oauth2.service.grant.impl;

import io.gravitee.am.common.ciba.Parameters;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.gateway.handler.ciba.service.AuthenticationRequestService;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.utils.MapUtils;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static io.gravitee.am.common.oidc.Parameters.ACR_VALUES;
import static io.gravitee.am.common.utils.ConstantKeys.AUTH_FLOW_CONTEXT_ACR_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.util.StringUtils.isEmpty;

/**
 * Strategy for CIBA (Client Initiated Backchannel Authentication) Grant.
 * Handles backchannel authentication token requests.
 *
 * @see <a href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html">CIBA Core</a>
 * @author GraviteeSource Team
 */
public class CibaStrategy implements GrantStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(CibaStrategy.class);

    private final AuthenticationRequestService authenticationRequestService;
    private final UserAuthenticationManager userAuthenticationManager;

    public CibaStrategy(
            AuthenticationRequestService authenticationRequestService,
            UserAuthenticationManager userAuthenticationManager) {
        this.authenticationRequestService = authenticationRequestService;
        this.userAuthenticationManager = userAuthenticationManager;
    }

    @Override
    public boolean supports(String grantType, Client client, Domain domain) {
        if (!GrantType.CIBA_GRANT_TYPE.equals(grantType)) {
            return false;
        }

        if (!client.hasGrantType(GrantType.CIBA_GRANT_TYPE)) {
            LOGGER.debug("Client {} does not support CIBA grant type", client.getClientId());
            return false;
        }

        return true;
    }

    @Override
    public Single<TokenCreationRequest> process(TokenRequest request, Client client, Domain domain) {
        LOGGER.debug("Processing CIBA token request for client: {}", client.getClientId());

        MultiValueMap<String, String> parameters = request.parameters();
        String authReqId = parameters.getFirst(Parameters.AUTH_REQ_ID);

        if (isEmpty(authReqId)) {
            return Single.error(new InvalidRequestException("Missing parameter: auth_req_id"));
        }

        return authenticationRequestService.retrieve(domain, authReqId, client)
                .flatMap(cibaRequest -> processCibaRequest(request, client, domain, cibaRequest, authReqId));
    }

    private Single<TokenCreationRequest> processCibaRequest(
            TokenRequest request,
            Client client,
            Domain domain,
            CibaAuthRequest cibaRequest,
            String authReqId) {

        // Validate client ownership of the authentication request
        if (!cibaRequest.getClientId().equals(client.getClientId())) {
            LOGGER.warn("client_id '{}' requests token using not owned authentication request '{}'",
                    client.getClientId(), authReqId);
            return Single.error(new InvalidGrantException("Authentication request not found"));
        }

        // Extract ACR values from CIBA request
        List<String> acrValues = null;
        if (cibaRequest.getExternalInformation() != null) {
            acrValues = MapUtils.extractStringList(cibaRequest.getExternalInformation(), ACR_VALUES)
                    .orElse(null);
        }

        // Store context attributes for EL templating
        request.getContext().put(AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY, Collections.emptyMap());
        if (acrValues != null) {
            request.getContext().put(AUTH_FLOW_CONTEXT_ACR_KEY, acrValues);
        }

        // Set scopes from CIBA request
        request.setScopes(cibaRequest.getScopes());

        List<String> finalAcrValues = acrValues;

        // Load the user
        return userAuthenticationManager.loadPreAuthenticatedUser(cibaRequest.getSubject(), request)
                .switchIfEmpty(Single.error(new InvalidGrantException("User not found")))
                .onErrorResumeNext(ex -> Single.error(
                        new InvalidGrantException(isBlank(ex.getMessage()) ? "unable to read user profile" : ex.getMessage())))
                .map(user -> {
                    // Determine if refresh token is supported
                    boolean supportRefresh = client.hasGrantType(GrantType.REFRESH_TOKEN);

                    return TokenCreationRequest.forCiba(
                            request,
                            user,
                            authReqId,
                            finalAcrValues,
                            supportRefresh
                    );
                });
    }
}
