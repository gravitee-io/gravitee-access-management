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
import io.gravitee.am.gateway.handler.common.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedGrantTypeException;
import io.gravitee.am.gateway.handler.oauth2.service.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.service.granter.client.ClientCredentialsTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.code.AuthorizationCodeTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.password.ResourceOwnerPasswordCredentialsTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.refresh.RefreshTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.Client;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CompositeTokenGranter implements TokenGranter, InitializingBean {

    private ConcurrentMap<String, TokenGranter> tokenGranters = new ConcurrentHashMap<>();
    private TokenRequestResolver tokenRequestResolver = new TokenRequestResolver();

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserAuthenticationManager userAuthenticationManager;

    @Autowired
    private AuthorizationCodeService authorizationCodeService;

    public CompositeTokenGranter() { }

    public Single<Token> grant(TokenRequest tokenRequest, Client client) {
        return Observable
                .fromIterable(tokenGranters.values())
                .filter(tokenGranter -> tokenGranter.handle(tokenRequest.getGrantType()))
                .switchIfEmpty(Observable.error(new UnsupportedGrantTypeException("Unsupported grant type: " + tokenRequest.getGrantType())))
                .flatMapSingle(tokenGranter -> tokenGranter.grant(tokenRequest, client)).singleOrError();
    }


    public void addTokenGranter(String tokenGranterId, TokenGranter tokenGranter) {
        Objects.requireNonNull(tokenGranterId);
        Objects.requireNonNull(tokenGranter);
        tokenGranters.put(tokenGranterId, tokenGranter);
    }

    public void removeTokenGranter(String tokenGranterId) {
        tokenGranters.remove(tokenGranterId);
    }

    @Override
    public boolean handle(String grantType) {
        return true;
    }

    @Override
    public void afterPropertiesSet() {
        addTokenGranter(GrantType.CLIENT_CREDENTIALS, new ClientCredentialsTokenGranter(tokenRequestResolver, tokenService));
        addTokenGranter(GrantType.PASSWORD, new ResourceOwnerPasswordCredentialsTokenGranter(tokenRequestResolver, tokenService,userAuthenticationManager));
        addTokenGranter(GrantType.AUTHORIZATION_CODE, new AuthorizationCodeTokenGranter(tokenRequestResolver, tokenService, authorizationCodeService, userAuthenticationManager));
        addTokenGranter(GrantType.REFRESH_TOKEN, new RefreshTokenGranter(tokenRequestResolver, tokenService, userAuthenticationManager));
    }
}
