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
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.common.service.uma.UMAPermissionTicketService;
import io.gravitee.am.gateway.handler.common.service.uma.UMAResourceGatewayService;
import io.gravitee.am.gateway.handler.oauth2.exception.UnsupportedGrantTypeException;
import io.gravitee.am.gateway.handler.oauth2.service.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.StrategyGranterAdapter;
import io.gravitee.am.gateway.handler.oauth2.service.grant.impl.AuthorizationCodeStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.impl.CibaStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.impl.ClientCredentialsStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.impl.PasswordStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.impl.RefreshTokenStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.impl.TokenExchangeStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.impl.UmaStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeService;
import io.gravitee.am.gateway.handler.oauth2.service.validation.ResourceConsistencyValidationService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.gateway.api.Response;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Composite TokenGranter that uses the Strategy pattern for standard grant types.
 * Extension grants are still supported via the addTokenGranter/removeTokenGranter methods.
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CompositeTokenGranter implements TokenGranter, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompositeTokenGranter.class);

    private final ConcurrentMap<String, TokenGranter> tokenGranters = new ConcurrentHashMap<>();
    private final TokenRequestResolver tokenRequestResolver = new TokenRequestResolver();

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
    private UMAPermissionTicketService permissionTicketService;

    @Autowired
    private UMAResourceGatewayService resourceService;

    @Autowired
    private RulesEngine rulesEngine;

    @Autowired
    private AuthenticationFlowContextService authenticationFlowContextService;

    @Autowired
    private Environment environment;

    @Autowired
    private ScopeManager scopeManager;

    @Autowired
    private ProtectedResourceManager protectedResourceManager;

    @Autowired
    private AuthenticationRequestService authenticationRequestService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private SubjectManager subjectManager;

    @Autowired
    private ResourceConsistencyValidationService resourceConsistencyValidationService;

    @Autowired
    private TokenExchangeService tokenExchangeService;

    @Autowired
    private ExecutionContextFactory executionContextFactory;

    @Override
    public Single<Token> grant(TokenRequest tokenRequest, Client client) {
        return findGranter(tokenRequest, client)
                .flatMap(tokenGranter -> tokenGranter.grant(tokenRequest, client))
                .doOnError(error -> auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class).tokenActor(client).throwable(error)));
    }

    @Override
    public Single<Token> grant(TokenRequest tokenRequest, Response response, Client client) {
        return findGranter(tokenRequest, client)
                .flatMap(tokenGranter -> tokenGranter.grant(tokenRequest, response, client))
                .doOnError(error -> auditService.report(AuditBuilder.builder(ClientTokenAuditBuilder.class).tokenActor(client).throwable(error)));
    }

    private Single<TokenGranter> findGranter(TokenRequest tokenRequest, Client client) {
        return Observable
                .fromIterable(tokenGranters.values())
                .filter(tokenGranter -> tokenGranter.handle(tokenRequest.getGrantType(), client))
                .firstElement()
                .switchIfEmpty(Single.error(() -> new UnsupportedGrantTypeException("Unsupported grant type: " + tokenRequest.getGrantType())));
    }

    public void addTokenGranter(String tokenGranterId, TokenGranter tokenGranter) {
        Objects.requireNonNull(tokenGranterId);
        Objects.requireNonNull(tokenGranter);
        tokenGranters.put(tokenGranterId, tokenGranter);
        LOGGER.debug("Added token granter: {}", tokenGranterId);
    }

    public void removeTokenGranter(String tokenGranterId) {
        tokenGranters.remove(tokenGranterId);
        LOGGER.debug("Removed token granter: {}", tokenGranterId);
    }

    @Override
    public boolean handle(String grantType, Client client) {
        return true;
    }

    @Override
    public void afterPropertiesSet() {
        LOGGER.info("Initializing grant strategies for domain: {}", domain.getName());
        tokenRequestResolver.setManagers(scopeManager, protectedResourceManager);

        // Register Client Credentials strategy
        registerStrategy(GrantType.CLIENT_CREDENTIALS, new ClientCredentialsStrategy());

        // Register Password strategy
        registerStrategy(GrantType.PASSWORD, new PasswordStrategy(userAuthenticationManager));

        // Register Authorization Code strategy
        boolean exitOnError = environment.getProperty("authenticationFlow.exitOnError", Boolean.class, Boolean.FALSE);
        registerStrategy(GrantType.AUTHORIZATION_CODE, new AuthorizationCodeStrategy(
                authorizationCodeService,
                userAuthenticationManager,
                authenticationFlowContextService,
                resourceConsistencyValidationService,
                exitOnError
        ));

        // Register Refresh Token strategy
        registerStrategy(GrantType.REFRESH_TOKEN, new RefreshTokenStrategy(
                tokenService,
                userAuthenticationManager,
                resourceConsistencyValidationService
        ));

        // Register Token Exchange strategy
        registerStrategy(GrantType.TOKEN_EXCHANGE, new TokenExchangeStrategy(tokenExchangeService));

        // Register CIBA strategy
        registerStrategy(GrantType.CIBA_GRANT_TYPE, new CibaStrategy(
                authenticationRequestService,
                userAuthenticationManager
        ));

        // Register UMA strategy
        registerStrategy(GrantType.UMA, new UmaStrategy(
                userAuthenticationManager,
                permissionTicketService,
                resourceService,
                jwtService,
                subjectManager,
                rulesEngine,
                executionContextFactory
        ));

        LOGGER.info("Grant strategies initialized for domain: {}", domain.getName());
    }

    private void registerStrategy(String grantType, GrantStrategy strategy) {
        TokenGranter adapter = new StrategyGranterAdapter(strategy, domain, tokenService, rulesEngine, tokenRequestResolver);
        addTokenGranter(grantType, adapter);
    }
}
