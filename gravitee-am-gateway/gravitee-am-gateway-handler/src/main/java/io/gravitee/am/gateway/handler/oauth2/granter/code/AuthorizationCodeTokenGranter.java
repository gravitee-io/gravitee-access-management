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
package io.gravitee.am.gateway.handler.oauth2.granter.code;

import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.model.Client;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.Single;

/**
 * Implementation of the Authorization Code Grant Flow
 * See <a href="https://tools.ietf.org/html/rfc6749#page-24"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationCodeTokenGranter extends AbstractTokenGranter {

    private final static String GRANT_TYPE = "authorization_code";

    private AuthorizationCodeService authorizationCodeService;

    public AuthorizationCodeTokenGranter() {
        super(GRANT_TYPE);
    }

    public AuthorizationCodeTokenGranter(TokenService tokenService) {
        this();
        setTokenService(tokenService);
    }

    public AuthorizationCodeTokenGranter(TokenService tokenService, AuthorizationCodeService authorizationCodeService) {
        this(tokenService);
        this.authorizationCodeService = authorizationCodeService;
    }

    @Override
    protected Single<OAuth2Request> createOAuth2Request(TokenRequest tokenRequest, Client client) {
        MultiValueMap<String, String> parameters = tokenRequest.getRequestParameters();
        String code = parameters.getFirst(OAuth2Constants.CODE);
        String redirectUri = parameters.getFirst(OAuth2Constants.REDIRECT_URI);

        if (code == null || code.isEmpty()) {
            throw new InvalidRequestException("An authorization code must be supplied.");
        }

        return authorizationCodeService.remove(code, client)
                .flatMapSingle(authorizationCode -> {
                    // This might be null, if the authorization was done without the redirect_uri parameter
                    // https://tools.ietf.org/html/rfc6749#section-4.1.3 (4.1.3. Access Token Request); if provided
                    // their values MUST be identical
                    String redirectUriApprovalParameter = authorizationCode.getRedirectUri();
                    if (redirectUriApprovalParameter != null) {
                        if (redirectUri == null) {
                            throw new InvalidGrantException("Redirect URI is missing");
                        }
                        if (!redirectUriApprovalParameter.equals(redirectUri)) {
                            throw new InvalidGrantException("Redirect URI mismatch.");
                        }
                    }

                    return super.createOAuth2Request(tokenRequest, client)
                            .map(oAuth2Request -> {
                                oAuth2Request.setSubject(authorizationCode.getSubject());
                                // set authorization code initial request parameters (step1 of authorization code flow)
                                if (authorizationCode.getRequestParameters() != null) {
                                    authorizationCode.getRequestParameters().forEach((key, value) -> oAuth2Request.getRequestParameters().putIfAbsent(key, value));
                                }
                                return oAuth2Request;
                            });
                });
        }
}
