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
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.granter.client.ClientCredentialsTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.code.AuthorizationCodeTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.implicit.ImplicitTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.granter.password.ResourceOwnerPasswordCredentialsTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jws.Oneway;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CompositeTokenGranter implements TokenGranter, InitializingBean {

    private List<TokenGranter> tokenGranters = new ArrayList<>();

    @Autowired
    private ClientService clientService;

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

    public Single<AccessToken> grant(TokenRequest tokenRequest) {
        return Observable
                .fromIterable(tokenGranters)
                .filter(tokenGranter -> tokenGranter.handle(tokenRequest.getGrantType()))
                .flatMapSingle(tokenGranter -> tokenGranter.grant(tokenRequest)).singleOrError();
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
        addTokenGranter(new ClientCredentialsTokenGranter(clientService, tokenService));
        addTokenGranter(new ResourceOwnerPasswordCredentialsTokenGranter(clientService, tokenService, userAuthenticationManager));
        addTokenGranter(new ImplicitTokenGranter(clientService, tokenService));
        addTokenGranter(new AuthorizationCodeTokenGranter(clientService, tokenService, authorizationCodeService));
    }
}
