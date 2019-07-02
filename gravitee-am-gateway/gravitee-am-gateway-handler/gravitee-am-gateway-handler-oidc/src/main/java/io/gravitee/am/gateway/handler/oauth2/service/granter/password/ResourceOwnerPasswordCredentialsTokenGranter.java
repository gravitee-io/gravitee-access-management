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
package io.gravitee.am.gateway.handler.oauth2.service.granter.password;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.common.auth.EndUserAuthentication;
import io.gravitee.am.gateway.handler.common.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.Maybe;
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

    final static String USERNAME_PARAMETER = "username";
    final static String PASSWORD_PARAMETER = "password";

    private UserAuthenticationManager userAuthenticationManager;

    public ResourceOwnerPasswordCredentialsTokenGranter() {
        super(GrantType.PASSWORD);
    }

    public ResourceOwnerPasswordCredentialsTokenGranter(TokenRequestResolver tokenRequestResolver, TokenService tokenService, UserAuthenticationManager userAuthenticationManager) {
        this();
        setTokenRequestResolver(tokenRequestResolver);
        setTokenService(tokenService);
        setUserAuthenticationManager(userAuthenticationManager);
    }

    @Override
    protected Single<TokenRequest> parseRequest(TokenRequest tokenRequest, Client client) {
        MultiValueMap<String, String> parameters = tokenRequest.parameters();
        String username = parameters.getFirst(USERNAME_PARAMETER);
        String password = parameters.getFirst(PASSWORD_PARAMETER);

        if (username == null) {
            return Single.error(new InvalidRequestException("Missing parameter: username"));
        }

        if (password == null) {
            return Single.error(new InvalidRequestException("Missing parameter: password"));
        }

        // set required parameters
        tokenRequest.setUsername(username);
        tokenRequest.setPassword(password);

        return super.parseRequest(tokenRequest, client);
    }

    @Override
    protected Maybe<User> resolveResourceOwner(TokenRequest tokenRequest, Client client) {
        String username = tokenRequest.getUsername();
        String password = tokenRequest.getPassword();

        return userAuthenticationManager.authenticate(client, new EndUserAuthentication(username, password))
                .onErrorResumeNext(ex -> Single.error(new InvalidGrantException(ex.getMessage())))
                .toMaybe();
    }

    public void setUserAuthenticationManager(UserAuthenticationManager userAuthenticationManager) {
        this.userAuthenticationManager = userAuthenticationManager;
    }
}
