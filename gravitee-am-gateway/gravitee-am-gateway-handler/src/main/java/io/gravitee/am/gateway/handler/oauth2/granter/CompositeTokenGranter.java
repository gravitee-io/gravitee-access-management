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
package io.gravitee.am.gateway.handler.oauth2.granter;

import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedGrantTypeException;
import io.gravitee.am.gateway.handler.oauth2.granter.client.ClientCredentialsTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.code.AuthorizationCodeTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.password.ResourceOwnerPasswordCredentialsTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.refresh.RefreshTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.model.Client;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CompositeTokenGranter implements TokenGranter, InitializingBean {

    private List<TokenGranter> tokenGranters = new ArrayList<>();
    private TokenRequestResolver tokenRequestResolver = new TokenRequestResolver();

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserAuthenticationManager userAuthenticationManager;

    @Autowired
    private AuthorizationCodeService authorizationCodeService;

    public CompositeTokenGranter() { }

    public CompositeTokenGranter(List<TokenGranter> tokenGranters) {
        this.tokenGranters = new ArrayList<>(tokenGranters);
    }

    public Single<AccessToken> grant(TokenRequest tokenRequest, Client client) {
        return Observable
                .fromIterable(tokenGranters)
                .filter(tokenGranter -> tokenGranter.handle(tokenRequest.getGrantType()))
                .switchIfEmpty(Observable.error(new UnsupportedGrantTypeException("Unsupported grant type: " + tokenRequest.getGrantType())))
                .flatMapSingle(tokenGranter -> tokenGranter.grant(tokenRequest, client)).singleOrError();
    }

    public void addTokenGranter(TokenGranter tokenGranter) {
        Objects.requireNonNull(tokenGranter);
        tokenGranters.add(tokenGranter);
    }

    @Override
    public boolean handle(String grantType) {
        return true;
    }

    @Override
    public void afterPropertiesSet() {
        addTokenGranter(new ClientCredentialsTokenGranter(tokenRequestResolver, tokenService));
        addTokenGranter(new ResourceOwnerPasswordCredentialsTokenGranter(tokenRequestResolver, tokenService, userAuthenticationManager));
        addTokenGranter(new AuthorizationCodeTokenGranter(tokenRequestResolver, tokenService, authorizationCodeService, userAuthenticationManager));
        addTokenGranter(new RefreshTokenGranter(tokenRequestResolver, tokenService, userAuthenticationManager));
    }
}
