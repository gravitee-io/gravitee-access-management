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
import io.gravitee.am.gateway.handler.ciba.service.AuthenticationRequestService;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedGrantTypeException;
import io.gravitee.am.gateway.handler.oauth2.service.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.service.granter.ciba.CibaTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.client.ClientCredentialsTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.code.AuthorizationCodeTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.password.ResourceOwnerPasswordCredentialsTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.refresh.RefreshTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.granter.uma.UMATokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.PermissionTicketService;
import io.gravitee.am.service.ResourceService;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

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
    private Domain domain;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private UserAuthenticationManager userAuthenticationManager;

    @Autowired
    private AuthorizationCodeService authorizationCodeService;

    @Autowired
    private PermissionTicketService permissionTicketService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private RulesEngine rulesEngine;

    @Autowired
    private ExecutionContextFactory executionContextFactory;

    @Autowired
    private AuthenticationFlowContextService authenticationFlowContextService;

    @Autowired
    private Environment environment;

    @Autowired
    private ScopeManager scopeManager;

    @Autowired
    private AuthenticationRequestService authenticationRequestService;

    public CompositeTokenGranter() { }

    @Override
    public Single<Token> grant(TokenRequest tokenRequest, Client client) {
        return Observable
                .fromIterable(tokenGranters.values())
                .filter(tokenGranter -> tokenGranter.handle(tokenRequest.getGrantType(), client))
                .firstElement()
                .switchIfEmpty(Single.error(() -> new UnsupportedGrantTypeException("Unsupported grant type: " + tokenRequest.getGrantType())))
                .flatMap(tokenGranter -> tokenGranter.grant(tokenRequest, client));
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
    public boolean handle(String grantType, Client client) {
        return true;
    }

    @Override
    public void afterPropertiesSet() {
        this.tokenRequestResolver.setScopeManager(this.scopeManager);
        addTokenGranter(GrantType.CLIENT_CREDENTIALS, new ClientCredentialsTokenGranter(tokenRequestResolver, tokenService, rulesEngine));
        addTokenGranter(GrantType.PASSWORD, new ResourceOwnerPasswordCredentialsTokenGranter(tokenRequestResolver, tokenService, userAuthenticationManager, rulesEngine));
        addTokenGranter(GrantType.AUTHORIZATION_CODE, new AuthorizationCodeTokenGranter(tokenRequestResolver, tokenService, authorizationCodeService, userAuthenticationManager, authenticationFlowContextService, environment, rulesEngine));
        addTokenGranter(GrantType.REFRESH_TOKEN, new RefreshTokenGranter(tokenRequestResolver, tokenService, userAuthenticationManager, rulesEngine));
        addTokenGranter(GrantType.UMA, new UMATokenGranter(tokenService, userAuthenticationManager, permissionTicketService, resourceService, jwtService, domain, executionContextFactory, rulesEngine));
        addTokenGranter(GrantType.CIBA_GRANT_TYPE, new CibaTokenGranter(tokenRequestResolver, tokenService, userAuthenticationManager, authenticationRequestService, domain, rulesEngine));
    }
}
