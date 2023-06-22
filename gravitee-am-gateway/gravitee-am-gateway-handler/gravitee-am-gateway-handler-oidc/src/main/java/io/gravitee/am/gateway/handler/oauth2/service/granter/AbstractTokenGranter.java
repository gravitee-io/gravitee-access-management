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
package io.gravitee.am.gateway.handler.oauth2.service.granter;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Objects;
import java.util.Optional;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AbstractTokenGranter implements TokenGranter {

    private final String grantType;

    private TokenRequestResolver tokenRequestResolver;

    private TokenService tokenService;

    private RulesEngine rulesEngine;

    private boolean supportRefreshToken = true;

    public AbstractTokenGranter(final String grantType) {
        Objects.requireNonNull(grantType);
        this.grantType = grantType;
    }

    @Override
    public boolean handle(String grantType, Client client) {
        return this.grantType.equals(grantType);
    }

    @Override
    public Single<Token> grant(TokenRequest tokenRequest, Client client) {
        return parseRequest(tokenRequest, client)
                .flatMapMaybe(tokenRequest1 -> resolveResourceOwner(tokenRequest1, client))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(user -> handleRequest(tokenRequest, client, user.orElse(null)));
    }

    /**
     * The authorization server validates the request to ensure that all required parameters are present and valid.
     * @param tokenRequest Access Token Request
     * @param client OAuth2 client
     * @return Access Token Request or invalid request exception
     */
    protected Single<TokenRequest> parseRequest(TokenRequest tokenRequest, Client client) {
        // Is client allowed to use such grant type ?
        if (client.getAuthorizedGrantTypes() != null && !client.getAuthorizedGrantTypes().isEmpty()
                && !client.getAuthorizedGrantTypes().contains(grantType)) {
            throw new UnauthorizedClientException("Unauthorized grant type: " + grantType);
        }
        return Single.just(tokenRequest);
    }

    /**
     * If the request is valid, the authorization server authenticates the resource owner and obtains an authorization decision
     * @param tokenRequest Access Token Request
     * @param client OAuth2 client
     * @return Resource Owner or empty for protocol flow like client_credentials
     */
    protected Maybe<User> resolveResourceOwner(TokenRequest tokenRequest, Client client) {
        return Maybe.empty();
    }

    /**
     * Validates the request to ensure that all required parameters meet the Client and Resource Owner requirements
     * @param tokenRequest Access Token Request
     * @param client OAuth2 client
     * @param endUser Resource Owner (if exists)
     * @return Access Token Request or OAuth 2.0 exception
     */
    protected Single<TokenRequest> resolveRequest(TokenRequest tokenRequest, Client client, User endUser) {
        return tokenRequestResolver.resolve(tokenRequest, client, endUser);
    }

    /**
     * Determines if a refresh token should be included in the token response
     * @param supportRefreshToken
     */
    protected void setSupportRefreshToken(boolean supportRefreshToken) {
        this.supportRefreshToken = supportRefreshToken;
    }

    protected Single<Token> createAccessToken(OAuth2Request oAuth2Request, Client client, User endUser) {
        return tokenService.create(oAuth2Request, client, endUser);
    }

    protected boolean isSupportRefreshToken(Client client) {
        return supportRefreshToken && client.getAuthorizedGrantTypes().contains(GrantType.REFRESH_TOKEN);
    }

    public TokenRequestResolver getTokenRequestResolver() {
        return tokenRequestResolver;
    }

    public void setTokenRequestResolver(TokenRequestResolver tokenRequestResolver) {
        this.tokenRequestResolver = tokenRequestResolver;
    }

    public TokenService getTokenService() {
        return tokenService;
    }

    public void setTokenService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public RulesEngine getRulesEngine() {
        return rulesEngine;
    }

    public void setRulesEngine(RulesEngine rulesEngine) {
        this.rulesEngine = rulesEngine;
    }

    private Single<Token> handleRequest(TokenRequest tokenRequest, Client client, User endUser) {
        return resolveRequest(tokenRequest, client, endUser)
                .flatMap(tokenRequest1 -> createOAuth2Request(tokenRequest1, client, endUser))
                .flatMap(oAuth2Request -> {
                    // fire PRE TOKEN flow
                    return rulesEngine.fire(ExtensionPoint.PRE_TOKEN, oAuth2Request, client, endUser)
                            // add the policy chain context values to the oauth2 request context
                            .map(executionContext -> {
                                oAuth2Request.getExecutionContext().putAll(executionContext.getAttributes());
                                return oAuth2Request;
                            })
                            // create the access token
                            .flatMap(oAuth2Request1 -> createAccessToken(oAuth2Request1, client, endUser))
                            // fire POST TOKEN flow
                            .flatMap(token -> rulesEngine.fire(ExtensionPoint.POST_TOKEN, oAuth2Request, client, endUser)
                                    .map(executionContext -> token));
                });
    }

    private Single<OAuth2Request> createOAuth2Request(TokenRequest tokenRequest, Client client, User endUser) {
        return Single.just(tokenRequest.createOAuth2Request())
                .map(oAuth2Request -> {
                    if (endUser != null) {
                        oAuth2Request.setSubject(endUser.getId());
                    }
                    oAuth2Request.setSupportRefreshToken(isSupportRefreshToken(client));
                    return oAuth2Request;
                });
    }

}
