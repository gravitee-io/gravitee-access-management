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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.user.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for OAuth 2.0 Resource Owner Password Credentials Grant.
 * Handles direct user authentication with username and password.
 *
 * @see <a href="https://tools.ietf.org/html/rfc6749#section-4.3">RFC 6749 Section 4.3</a>
 * @author GraviteeSource Team
 */
public class PasswordStrategy implements GrantStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordStrategy.class);

    private final UserAuthenticationManager userAuthenticationManager;

    public PasswordStrategy(UserAuthenticationManager userAuthenticationManager) {
        this.userAuthenticationManager = userAuthenticationManager;
    }

    @Override
    public boolean supports(String grantType, Client client, Domain domain) {
        if (!GrantType.PASSWORD.equals(grantType)) {
            return false;
        }

        if (!client.hasGrantType(GrantType.PASSWORD)) {
            LOGGER.debug("Client {} does not support password grant type", client.getClientId());
            return false;
        }

        return true;
    }

    @Override
    public Single<TokenCreationRequest> process(TokenRequest request, Client client, Domain domain) {
        LOGGER.debug("Processing password grant request for client: {}", client.getClientId());

        MultiValueMap<String, String> parameters = request.parameters();
        String username = parameters.getFirst(Parameters.USERNAME);
        String password = parameters.getFirst(Parameters.PASSWORD);

        if (username == null) {
            return Single.error(new InvalidRequestException("Missing parameter: username"));
        }

        if (password == null) {
            return Single.error(new InvalidRequestException("Missing parameter: password"));
        }

        // Authenticate the user
        EndUserAuthentication authentication = new EndUserAuthentication(
                username, password, new SimpleAuthenticationContext(request));

        return userAuthenticationManager.authenticate(client, authentication)
                .onErrorResumeNext(ex -> Single.error(new InvalidGrantException(ex.getMessage())))
                .map(user -> {
                    // Determine if refresh token is supported
                    boolean supportRefresh = client.hasGrantType(GrantType.REFRESH_TOKEN);

                    return TokenCreationRequest.forPassword(request, user, username, supportRefresh);
                });
    }
}
