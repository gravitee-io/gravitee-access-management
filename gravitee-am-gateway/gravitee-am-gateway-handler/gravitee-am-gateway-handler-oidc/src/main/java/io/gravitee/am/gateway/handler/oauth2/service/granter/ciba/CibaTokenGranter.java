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
package io.gravitee.am.gateway.handler.oauth2.service.granter.ciba;

import io.gravitee.am.common.ciba.Parameters;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.gateway.handler.ciba.exception.AuthenticationRequestNotFoundException;
import io.gravitee.am.gateway.handler.ciba.service.AuthenticationRequestService;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.emptyMap;
import static org.springframework.util.StringUtils.isEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.*;

/**
 * Implementation of the CIBA Grant Flow
 * See <a href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html#rfc.section.10"></a>
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CibaTokenGranter extends AbstractTokenGranter {

    private final Logger logger = LoggerFactory.getLogger(CibaTokenGranter.class);

    private UserAuthenticationManager userAuthenticationManager;

    private AuthenticationRequestService authenticationRequestService;

    private Domain domain;

    public CibaTokenGranter() {
        super(GrantType.CIBA_GRANT_TYPE);
    }

    public CibaTokenGranter(TokenRequestResolver tokenRequestResolver,
                            TokenService tokenService,
                            UserAuthenticationManager userAuthenticationManager,
                            AuthenticationRequestService authenticationRequestService,
                            Domain domain,
                            RulesEngine rulesEngine) {
        this();
        setTokenRequestResolver(tokenRequestResolver);
        setTokenService(tokenService);
        setRulesEngine(rulesEngine);
        this.userAuthenticationManager = userAuthenticationManager;
        this.authenticationRequestService = authenticationRequestService;
        this.domain = domain;
    }

    @Override
    protected Single<TokenRequest> parseRequest(TokenRequest tokenRequest, Client client) {
        MultiValueMap<String, String> parameters = tokenRequest.parameters();
        final String authReqId = parameters.getFirst(Parameters.AUTH_REQ_ID);

        if (isEmpty(authReqId)) {
            return Single.error(new InvalidRequestException("Missing parameter: auth_req_id"));
        }

        return super.parseRequest(tokenRequest, client)
                .flatMap(tokenRequest1 -> authenticationRequestService.retrieve(domain, authReqId)
                        .map(cibaRequest -> {
                            if (!cibaRequest.getClientId().equals(client.getClientId())) {
                                logger.warn("client_id '{}' requests token using not owned authentication request '{}'", client.getId(), authReqId);
                                throw new AuthenticationRequestNotFoundException("Authentication request not found");
                            }
                            return cibaRequest;
                        })
                        .map(cibaRequest -> {
                            // set resource owner
                            tokenRequest1.setSubject(cibaRequest.getSubject());
                            // set original scopes
                            tokenRequest1.setScopes(cibaRequest.getScopes());
                            // store only the AuthenticationFlowContext.data attributes in order to simplify EL templating
                            // and provide an up to date set of data if the enrichAuthFlow Policy ius used multiple time in a step
                            // {#context.attributes['authFlow']['entry']}
                            tokenRequest1.getContext().put(AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY, emptyMap());

                            return tokenRequest1;
                        }));
    }

    @Override
    protected Maybe<User> resolveResourceOwner(TokenRequest tokenRequest, Client client) {
        return userAuthenticationManager.loadPreAuthenticatedUser(tokenRequest.getSubject(), tokenRequest)
                .onErrorResumeNext(ex -> { return Maybe.error(new InvalidGrantException()); });
    }

    @Override
    protected Single<TokenRequest> resolveRequest(TokenRequest tokenRequest, Client client, User endUser) {
        // request has already been resolved during step1 of authorization code flow
        return Single.just(tokenRequest);
    }

}
