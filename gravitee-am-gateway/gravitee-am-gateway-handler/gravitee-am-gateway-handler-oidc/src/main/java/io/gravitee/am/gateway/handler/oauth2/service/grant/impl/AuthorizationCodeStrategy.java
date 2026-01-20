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
package io.gravitee.am.gateway.handler.oauth2.service.grant.impl;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.CodeChallengeMethod;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.code.AuthorizationCodeService;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.pkce.PKCEUtils;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.validation.ResourceConsistencyValidationService;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Strategy for OAuth 2.0 Authorization Code Grant.
 * Handles validation of authorization codes and PKCE.
 *
 * @see <a href="https://tools.ietf.org/html/rfc6749#section-4.1">RFC 6749 Section 4.1</a>
 * @author GraviteeSource Team
 */
public class AuthorizationCodeStrategy implements GrantStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationCodeStrategy.class);

    private final AuthorizationCodeService authorizationCodeService;
    private final UserAuthenticationManager userAuthenticationManager;
    private final AuthenticationFlowContextService authenticationFlowContextService;
    private final ResourceConsistencyValidationService resourceConsistencyValidationService;
    private final boolean exitOnError;

    public AuthorizationCodeStrategy(
            AuthorizationCodeService authorizationCodeService,
            UserAuthenticationManager userAuthenticationManager,
            AuthenticationFlowContextService authenticationFlowContextService,
            ResourceConsistencyValidationService resourceConsistencyValidationService,
            boolean exitOnError) {
        this.authorizationCodeService = authorizationCodeService;
        this.userAuthenticationManager = userAuthenticationManager;
        this.authenticationFlowContextService = authenticationFlowContextService;
        this.resourceConsistencyValidationService = resourceConsistencyValidationService;
        this.exitOnError = exitOnError;
    }

    @Override
    public boolean supports(String grantType, Client client, Domain domain) {
        if (!GrantType.AUTHORIZATION_CODE.equals(grantType)) {
            return false;
        }

        if (!client.hasGrantType(GrantType.AUTHORIZATION_CODE)) {
            LOGGER.debug("Client {} does not support authorization_code grant type", client.getClientId());
            return false;
        }

        return true;
    }

    @Override
    public Single<TokenCreationRequest> process(TokenRequest request, Client client, Domain domain) {
        LOGGER.debug("Processing authorization code request for client: {}", client.getClientId());

        MultiValueMap<String, String> parameters = request.parameters();
        String code = parameters.getFirst(Parameters.CODE);

        if (code == null || code.isEmpty()) {
            return Single.error(new InvalidRequestException("Missing parameter: code"));
        }

        String codeVerifier = parameters.getFirst(Parameters.CODE_VERIFIER);
        String redirectUri = parameters.getFirst(Parameters.REDIRECT_URI);

        return authorizationCodeService.remove(code, client)
                .switchIfEmpty(Single.error(new InvalidGrantException("Invalid authorization code")))
                .flatMap(authorizationCode -> processAuthorizationCode(
                        request, client, authorizationCode, codeVerifier, redirectUri));
    }

    private Single<TokenCreationRequest> processAuthorizationCode(
            TokenRequest request,
            Client client,
            AuthorizationCode authorizationCode,
            String codeVerifier,
            String redirectUri) {

        // Validate redirect URI
        validateRedirectUri(redirectUri, authorizationCode);

        // Validate PKCE
        validatePKCE(codeVerifier, authorizationCode);

        // Load user and create token request
        return authenticationFlowContextService
                .removeContext(authorizationCode.getTransactionId(), authorizationCode.getContextVersion())
                .onErrorResumeNext(error -> exitOnError ? Single.error(error) : Single.just(new AuthenticationFlowContext()))
                .flatMap(ctx -> loadUserAndCreateRequest(request, client, authorizationCode, codeVerifier, redirectUri, ctx));
    }

    private Single<TokenCreationRequest> loadUserAndCreateRequest(
            TokenRequest request,
            Client client,
            AuthorizationCode authorizationCode,
            String codeVerifier,
            String redirectUri,
            AuthenticationFlowContext ctx) {

        return userAuthenticationManager.loadPreAuthenticatedUser(authorizationCode.getSubject(), request)
                .switchIfEmpty(Single.error(new InvalidGrantException("User not found")))
                .onErrorResumeNext(ex -> Single.error(
                        new InvalidGrantException(isBlank(ex.getMessage()) ? "unable to read user profile" : ex.getMessage())))
                .map(user -> createTokenCreationRequest(
                        request, client, user, authorizationCode, codeVerifier, redirectUri, ctx));
    }

    private TokenCreationRequest createTokenCreationRequest(
            TokenRequest request,
            Client client,
            User user,
            AuthorizationCode authorizationCode,
            String codeVerifier,
            String redirectUri,
            AuthenticationFlowContext ctx) {

        // Build decoded authorization code data
        Map<String, Object> decodedAuthorizationCode = new HashMap<>();
        decodedAuthorizationCode.put("code", authorizationCode.getCode());
        decodedAuthorizationCode.put("transactionId", authorizationCode.getTransactionId());

        // Store auth flow context in request for EL templating
        request.getContext().put(ConstantKeys.AUTH_FLOW_CONTEXT_ATTRIBUTES_KEY, ctx.getData());

        // Resolve resources according to RFC 8707
        Set<String> originalResources = authorizationCode.getResources();
        request.setOriginalAuthorizationResources(originalResources);
        Set<String> finalResources = resourceConsistencyValidationService.resolveFinalResources(request, originalResources);
        request.setResources(finalResources);

        // Set scopes from authorization code
        request.setScopes(authorizationCode.getScopes());

        // Copy initial request parameters from authorization step
        if (authorizationCode.getRequestParameters() != null) {
            authorizationCode.getRequestParameters().forEach((key, value) ->
                    request.parameters().putIfAbsent(key, value));
        }

        // Authorization code typically supports refresh tokens
        boolean supportRefresh = client.hasGrantType(GrantType.REFRESH_TOKEN);

        return TokenCreationRequest.forAuthorizationCode(
                request,
                user,
                authorizationCode.getCode(),
                codeVerifier,
                redirectUri,
                decodedAuthorizationCode,
                supportRefresh
        );
    }

    private void validateRedirectUri(String redirectUri, AuthorizationCode authorizationCode) {
        // https://tools.ietf.org/html/rfc6749#section-4.1.3
        // if redirect_uri was included in the authorization request, their values MUST be identical
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

    private void validatePKCE(String codeVerifier, AuthorizationCode authorizationCode) {
        // https://tools.ietf.org/html/rfc7636#section-4.6
        MultiValueMap<String, String> parameters = authorizationCode.getRequestParameters();
        String codeChallenge = parameters.getFirst(Parameters.CODE_CHALLENGE);
        var codeChallengeMethod = Objects.requireNonNullElse(
                CodeChallengeMethod.fromUriParam(parameters.getFirst(Parameters.CODE_CHALLENGE_METHOD)),
                CodeChallengeMethod.PLAIN);

        if (codeChallenge != null && codeVerifier == null) {
            LOGGER.debug("PKCE code_verifier parameter is missing, even if a code_challenge was initially defined");
            throw new InvalidGrantException("Missing parameter: code_verifier");
        }

        if (codeChallenge == null) {
            return;
        }

        // Check that code verifier is valid
        if (!PKCEUtils.validCodeVerifier(codeVerifier)) {
            LOGGER.debug("PKCE code_verifier is not valid");
            throw new InvalidGrantException("Invalid parameter: code_verifier");
        }

        String encodedCodeVerifier = getCodeChallenge(codeChallengeMethod, codeVerifier);
        if (!codeChallenge.equals(encodedCodeVerifier)) {
            throw new InvalidGrantException("Invalid code_verifier");
        }
    }

    private String getCodeChallenge(CodeChallengeMethod codeChallengeMethod, String codeVerifier) {
        try {
            return codeChallengeMethod.getChallenge(codeVerifier);
        } catch (Exception ex) {
            LOGGER.error("Not able to generate the codeChallenge from the given code verifier according to {} algorithm",
                    codeChallengeMethod, ex);
            throw new InvalidGrantException("Not supported algorithm");
        }
    }
}
