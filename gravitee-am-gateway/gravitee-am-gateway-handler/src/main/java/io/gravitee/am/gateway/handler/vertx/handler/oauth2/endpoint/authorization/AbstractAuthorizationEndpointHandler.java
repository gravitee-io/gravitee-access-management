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
package io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization;

import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.response.AuthorizationCodeResponse;
import io.gravitee.am.gateway.handler.oauth2.response.HybridResponse;
import io.gravitee.am.gateway.handler.oauth2.response.IDTokenResponse;
import io.gravitee.am.gateway.handler.oauth2.response.ImplicitResponse;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.oidc.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.oidc.utils.OIDCClaims;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.request.TokenRequestFactory;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractAuthorizationEndpointHandler implements Handler<RoutingContext> {

    private final TokenRequestFactory tokenRequestFactory = new TokenRequestFactory();
    private AuthorizationCodeService authorizationCodeService;
    private TokenGranter tokenGranter;
    private TokenService tokenService;
    private IDTokenService idTokenService;

    public AbstractAuthorizationEndpointHandler(AuthorizationCodeService authorizationCodeService,
                                                TokenGranter tokenGranter,
                                                TokenService tokenService,
                                                IDTokenService idTokenService) {
        this.authorizationCodeService = authorizationCodeService;
        this.tokenGranter = tokenGranter;
        this.tokenService = tokenService;
        this.idTokenService = idTokenService;
    }

    protected Single<AuthorizationRequest> createAuthorizationResponse(AuthorizationRequest authorizationRequest, Client client, User authenticatedUser) {
        // request is not approved, the user will be redirect to the approval page
        if (!authorizationRequest.isApproved()) {
            return Single.just(authorizationRequest);
        }

        // Handle Response Type value that determines the authorization processing flow to be used
        // When using the Hybrid Flow, this value is code id_token, code token, or code id_token token.
        // https://openid.net/specs/openid-connect-core-1_0.html#HybridAuthRequest
        switch(authorizationRequest.getResponseType()) {
            case ResponseType.TOKEN :
            case io.gravitee.am.common.oidc.ResponseType.ID_TOKEN_TOKEN :
                return setImplicitResponse(authorizationRequest, client, authenticatedUser);
            case ResponseType.CODE :
                return setAuthorizationCodeResponse(authorizationRequest, authenticatedUser);
            case io.gravitee.am.common.oidc.ResponseType.ID_TOKEN:
                return setIdTokenResponse(authorizationRequest, client, authenticatedUser);
            case io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN :
            case io.gravitee.am.common.oidc.ResponseType.CODE_TOKEN :
            case io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN_TOKEN :
                return setHybridResponse(authorizationRequest, client, authenticatedUser);
            default:
                return Single.just(authorizationRequest);
        }
    }

    protected String buildRedirectUri(AuthorizationRequest authorizationRequest) throws Exception {
        return authorizationRequest.getResponse().buildRedirectUri(authorizationRequest);
    }

    private Single<AuthorizationRequest> setAuthorizationCodeResponse(AuthorizationRequest authorizationRequest, User authenticatedUser) {
        // prepare response
        return authorizationCodeService.create(authorizationRequest, authenticatedUser)
                .map(code -> {
                    AuthorizationCodeResponse response = new AuthorizationCodeResponse();
                    response.setCode(code.getCode());
                    response.setState(authorizationRequest.getState());
                    authorizationRequest.setResponse(response);
                    return authorizationRequest;
                });
    }

    private Single<AuthorizationRequest> setImplicitResponse(AuthorizationRequest authorizationRequest, Client client, User authenticatedUser) {
        TokenRequest tokenRequest = tokenRequestFactory.create(authorizationRequest);
        tokenRequest.setSubject(authenticatedUser.getId());
        tokenRequest.setGrantType(OAuth2Constants.IMPLICIT);
        return tokenGranter.grant(tokenRequest, client)
                .map(accessToken -> {
                    ImplicitResponse response = new ImplicitResponse();
                    response.setAccessToken(accessToken);
                    response.setState(authorizationRequest.getState());
                    authorizationRequest.setResponse(response);
                    return authorizationRequest;
                });
    }

    private Single<AuthorizationRequest> setHybridResponse(AuthorizationRequest authorizationRequest, Client client, User authenticatedUser) {
       // Authorization Code is always returned when using the Hybrid Flow.
        return authorizationCodeService.create(authorizationRequest, authenticatedUser)
                .flatMap(code -> {
                    // prepare response
                    HybridResponse hybridResponse = new HybridResponse();
                    hybridResponse.setState(authorizationRequest.getState());
                    hybridResponse.setCode(code.getCode());
                    OAuth2Request oAuth2Request = authorizationRequest.createOAuth2Request();
                    oAuth2Request.setSubject(authenticatedUser.getId());
                    oAuth2Request.getContext().put(OIDCClaims.c_hash, code.getCode());
                    switch (authorizationRequest.getResponseType()) {
                        // code id_token response type MUST include both an Authorization Code and an id_token
                        case io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN:
                            return idTokenService.create(oAuth2Request, client, authenticatedUser)
                                    .map(idToken -> {
                                        hybridResponse.setIdToken(idToken);
                                        authorizationRequest.setResponse(hybridResponse);
                                        return authorizationRequest;
                                    });
                        // others Hybrid Flow response type MUST include at least an Access Token, an Access Token Type and optionally an ID Token
                        default:
                            return tokenService.create(oAuth2Request, client)
                                    .map(accessToken -> {
                                        hybridResponse.setAccessToken(accessToken);
                                        authorizationRequest.setResponse(hybridResponse);
                                        return authorizationRequest;
                                    });
                    }
                });
    }

    private Single<AuthorizationRequest> setIdTokenResponse(AuthorizationRequest authorizationRequest, Client client, User authenticatedUser) {
        OAuth2Request oAuth2Request = authorizationRequest.createOAuth2Request();
        oAuth2Request.setSubject(authenticatedUser.getId());
        IDTokenResponse idTokenResponse = new IDTokenResponse();
        idTokenResponse.setState(authorizationRequest.getState());
        return idTokenService.create(oAuth2Request, client, authenticatedUser)
                .map(idToken -> {
                    idTokenResponse.setIdToken(idToken);
                    authorizationRequest.setResponse(idTokenResponse);
                    return authorizationRequest;
                });
    }
}
