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
package io.gravitee.am.gateway.handler.oauth2.granter.password;

import io.gravitee.am.gateway.handler.auth.EndUserAuthentication;
import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.model.Client;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.Single;

/**
 * Implementation of the Resource Owner Password Credentials Grant Flow
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.3"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResourceOwnerPasswordCredentialsTokenGranter extends AbstractTokenGranter {

    private final static String GRANT_TYPE = "password";
    final static String USERNAME_PARAMETER = "username";
    final static String PASSWORD_PARAMETER = "password";

    private UserAuthenticationManager userAuthenticationManager;

    public ResourceOwnerPasswordCredentialsTokenGranter() {
        super(GRANT_TYPE);
    }

    public ResourceOwnerPasswordCredentialsTokenGranter(ClientService clientService, TokenService tokenService) {
        this();
        setClientService(clientService);
        setTokenService(tokenService);
    }

    public ResourceOwnerPasswordCredentialsTokenGranter(ClientService clientService, TokenService tokenService, UserAuthenticationManager userAuthenticationManager) {
        this(clientService, tokenService);
        setUserAuthenticationManager(userAuthenticationManager);
    }

    @Override
    public Single<AccessToken> grant(TokenRequest tokenRequest) {
        return super.grant(tokenRequest);
    }

    protected Single<OAuth2Request> createOAuth2Request(TokenRequest tokenRequest, Client client) {
        MultiValueMap<String, String> parameters = tokenRequest.getRequestParameters();
        String username = parameters.getFirst(USERNAME_PARAMETER);
        String password = parameters.getFirst(PASSWORD_PARAMETER);

        return userAuthenticationManager.authenticate(tokenRequest.getClientId(), new EndUserAuthentication(username, password))
                .flatMap(user -> super.createOAuth2Request(tokenRequest, client)
                        .map(oAuth2Request -> {
                            oAuth2Request.setSubject(user.getId());
                            return oAuth2Request;
                        }))
                .onErrorResumeNext(ex -> Single.error(new InvalidGrantException()));
    }

    public void setUserAuthenticationManager(UserAuthenticationManager userAuthenticationManager) {
        this.userAuthenticationManager = userAuthenticationManager;
    }
}
