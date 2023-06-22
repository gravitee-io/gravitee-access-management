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
package io.gravitee.am.gateway.handler.oauth2.service.granter.code;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.CodeChallengeMethod;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.service.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.pkce.PKCEUtils;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the Authorization Code Grant Flow
 * See <a href="https://tools.ietf.org/html/rfc6749#page-24"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationCodeTokenGranter extends AbstractTokenGranter {

    private final Logger logger = LoggerFactory.getLogger(AuthorizationCodeTokenGranter.class);

    private AuthorizationCodeService authorizationCodeService;

    private UserAuthenticationManager userAuthenticationManager;

    private AuthenticationFlowContextService authenticationFlowContextService;

    private boolean exitOnError;
    public AuthorizationCodeTokenGranter() {
        super(GrantType.AUTHORIZATION_CODE);
    }

    public AuthorizationCodeTokenGranter(TokenRequestResolver tokenRequestResolver,
                                         TokenService tokenService,
                                         AuthorizationCodeService authorizationCodeService,
                                         UserAuthenticationManager userAuthenticationManager,
                                         AuthenticationFlowContextService authenticationFlowContextService,
                                         Environment env,
                                         RulesEngine rulesEngine) {
        this();
        setTokenRequestResolver(tokenRequestResolver);
        setTokenService(tokenService);
        setRulesEngine(rulesEngine);
        this.authorizationCodeService = authorizationCodeService;
        this.userAuthenticationManager = userAuthenticationManager;
        this.authenticationFlowContextService = authenticationFlowContextService;
        this.exitOnError = env.getProperty("authenticationFlow.exitOnError", Boolean.class, Boolean.FALSE);
    }

    @Override
    protected Single<TokenRequest> parseRequest(TokenRequest tokenRequest, Client client) {
        MultiValueMap<String, String> parameters = tokenRequest.parameters();
        String code = parameters.getFirst(Parameters.CODE);

        if (code == null || code.isEmpty()) {
            return Single.error(new InvalidRequestException("Missing parameter: code"));
        }

        return super.parseRequest(tokenRequest, client)
                .flatMap(tokenRequest1 -> authorizationCodeService.remove(code, client)
                        .flatMap(authorizationCode ->
                                authenticationFlowContextService.removeContext(authorizationCode.getTransactionId(), authorizationCode.getContextVersion())
                                    .onErrorResumeNext(error -> (exitOnError) ? Maybe.error(error) : Maybe.just(new AuthenticationFlowContext()))
                                    .map(ctx -> {
                                        checkRedirectUris(tokenRequest1, authorizationCode);
                                        checkPKCE( tokenRequest1, authorizationCode);
                                        // set resource owner
                                        tokenRequest1.setSubject(authorizationCode.getSubject());
                                        // set original scopes
                                        tokenRequest1.setScopes(authorizationCode.getScopes());
                                        // set authorization code initial request parameters (step1 of authorization code flow)
                                        if (authorizationCode.getRequestParameters() != null) {
                                            authorizationCode.getRequestParameters().forEach((key, value) -> tokenRequest1.parameters().putIfAbsent(key, value));
                                        }
                                        // set decoded authorization code to the current request
                                        Map<String, Object> decodedAuthorizationCode = new HashMap<>();
                                        decodedAuthorizationCode.put("code", authorizationCode.getCode());
                                        decodedAuthorizationCode.put("transactionId", authorizationCode.getTransactionId());
                                        tokenRequest1.setAuthorizationCode(decodedAuthorizationCode);

                                        // store only the AuthenticationFlowContext.data attributes in order to simplify EL templating
                                        // and provide an up to date set of data if the enrichAuthFlow Policy ius used multiple time in a step
                                        // {#context.attributes['authFlow']['entry']}
                                        tokenRequest1.getContext().put(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY, ctx.getData());

                                        return tokenRequest1;
                                    })
                        ).toSingle());
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

    private void checkRedirectUris(TokenRequest tokenRequest, AuthorizationCode authorizationCode) {
        String redirectUri = tokenRequest.parameters().getFirst(Parameters.REDIRECT_URI);

        // This might be null, if the authorization was done without the redirect_uri parameter
        // https://tools.ietf.org/html/rfc6749#section-4.1.3 (4.1.3. Access Token Request); if provided
        // their values MUST be identical
        String redirectUriApprovalParameter = authorizationCode.getRequestParameters().getFirst(Parameters.REDIRECT_URI);
        if (redirectUriApprovalParameter != null) {
            if (redirectUri == null) {
                throw new InvalidGrantException("Redirect URI is missing");
            }
            if (!redirectUriApprovalParameter.equals(redirectUri)) {
                throw new InvalidGrantException("Redirect URI mismatch.");
            }
        }
    }

    /**
     * // https://tools.ietf.org/html/rfc7636#section-4.6
     * @param tokenRequest
     * @param authorizationCode
     */
    private void checkPKCE(TokenRequest tokenRequest, AuthorizationCode authorizationCode) {
        String codeVerifier = tokenRequest.parameters().getFirst(Parameters.CODE_VERIFIER);
        MultiValueMap<String, String> parameters = authorizationCode.getRequestParameters();

        String codeChallenge = parameters.getFirst(Parameters.CODE_CHALLENGE);
        String codeChallengeMethod = parameters.getFirst(Parameters.CODE_CHALLENGE_METHOD);

        if (codeChallenge != null && codeVerifier == null) {
            logger.debug("PKCE code_verifier parameter is missing, even if a code_challenge was initially defined");
            throw new InvalidGrantException("Missing parameter: code_verifier");
        }

        if (codeChallenge != null) {
            // Check that code challenge is valid
            if (!PKCEUtils.validCodeVerifier(codeVerifier)) {
                logger.debug("PKCE code_verifier is not valid");
                throw new InvalidGrantException("Invalid parameter: code_verifier");
            }

            // By default, assume a plain code_challenge_method
            String encodedCodeVerifier = codeVerifier;

            // Otherwise, generate is using s256
            if (CodeChallengeMethod.S256.equalsIgnoreCase(codeChallengeMethod)) {
                try {
                    encodedCodeVerifier = PKCEUtils.getS256CodeChallenge(codeVerifier);
                } catch (Exception ex) {
                    logger.error("Not able to generate the codeChallenge from the given code verifier according to S256 algorithm");
                    throw new InvalidGrantException("Not supported algorithm");
                }
            }

            if (! codeChallenge.equals(encodedCodeVerifier)) {
                throw new InvalidGrantException("Invalid code_verifier");
            }
        }
    }
}
