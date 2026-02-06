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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ActorTokenInfo;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenValidator;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeResult;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeService;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TokenExchangeServiceImpl implements TokenExchangeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenExchangeServiceImpl.class);

    @Autowired
    private List<TokenValidator> validators;

    @Autowired
    private SubjectManager subjectManager;

    @Override
    public Single<TokenExchangeResult> exchange(TokenRequest tokenRequest, Client client, Domain domain) {
        return Single.fromCallable(() -> parseRequest(tokenRequest, domain))
                .flatMap(request -> {
                    if (request.isDelegation()) {
                        return processDelegation(tokenRequest, request, client, domain);
                    } else {
                        return processImpersonation(tokenRequest, request, client, domain);
                    }
                });
    }

    private Single<TokenExchangeResult> processImpersonation(TokenRequest tokenRequest,
                                                              ParsedRequest request,
                                                              Client client,
                                                              Domain domain) {
        return validateSubjectToken(request.subjectToken(), request.subjectTokenType(), domain)
                .flatMap(subjectToken -> buildImpersonationResult(tokenRequest, subjectToken, request, client));
    }

    private Single<TokenExchangeResult> processDelegation(TokenRequest tokenRequest,
                                                           ParsedRequest request,
                                                           Client client,
                                                           Domain domain) {
        TokenExchangeSettings settings = domain.getTokenExchangeSettings();

        return validateSubjectToken(request.subjectToken(), request.subjectTokenType(), domain)
                .flatMap(subjectToken ->
                        validateActorToken(request.actorToken(), request.actorTokenType(), domain)
                                .flatMap(actorToken -> {
                                    // Per RFC 8693 Section 4.1, delegation depth is based on
                                    // the subject token's "act" claim chain
                                    int currentDepth = calculateDelegationDepth(subjectToken);
                                    int resultingDepth = currentDepth + 1;
                                    int maxDepth = settings.getMaxDelegationDepth();

                                    if (maxDepth > 0 && resultingDepth > maxDepth) {
                                        return Single.error(new InvalidRequestException(
                                                "Maximum delegation depth exceeded. Current: " + currentDepth +
                                                        ", Max allowed: " + maxDepth));
                                    }

                                    ActorTokenInfo actorInfo = extractActorInfo(actorToken, subjectToken, resultingDepth);
                                    return buildDelegationResult(tokenRequest, subjectToken, actorToken, actorInfo, request, client);
                                })
                );
    }

    private ParsedRequest parseRequest(TokenRequest tokenRequest, Domain domain) {
        Map<String, String> params = tokenRequest.parameters().toSingleValueMap();
        TokenExchangeSettings settings = domain.getTokenExchangeSettings();

        if (settings == null || !settings.isEnabled()) {
            throw new InvalidRequestException("Token exchange is not enabled for this domain");
        }

        String subjectToken = params.get(Parameters.SUBJECT_TOKEN);
        String subjectTokenType = params.get(Parameters.SUBJECT_TOKEN_TYPE);
        String actorToken = params.get(Parameters.ACTOR_TOKEN);
        String actorTokenType = params.get(Parameters.ACTOR_TOKEN_TYPE);
        String scope = params.get(Parameters.SCOPE);
        String requestedTokenType = params.get(Parameters.REQUESTED_TOKEN_TYPE);

        // Default requested token type to access_token if allowed, otherwise require explicit type
        if (StringUtils.isEmpty(requestedTokenType)) {
            if (settings.getAllowedRequestedTokenTypes() != null &&
                    settings.getAllowedRequestedTokenTypes().contains(TokenType.ACCESS_TOKEN)) {
                requestedTokenType = TokenType.ACCESS_TOKEN;
            } else {
                throw new InvalidRequestException("requested_token_type is required when access_token is not allowed");
            }
        }
        validateSubjectParameters(subjectToken, subjectTokenType, requestedTokenType, settings);

        // Determine if this is delegation or impersonation
        boolean isDelegation = StringUtils.isNotEmpty(actorToken);

        if (isDelegation) {
            validateDelegationAllowed(settings);
            validateDelegationParameters(actorTokenType, settings);
        } else {
            validateImpersonationAllowed(settings);
        }

        return new ParsedRequest(
                subjectToken,
                subjectTokenType,
                actorToken,
                actorTokenType,
                scope,
                requestedTokenType,
                tokenRequest.getClientId(),
                isDelegation
        );
    }

    private void validateSubjectParameters(String subjectToken, String subjectTokenType,
                                            String requestedTokenType, TokenExchangeSettings settings) {
        if (StringUtils.isEmpty(subjectToken)) {
            throw new InvalidRequestException("Missing required parameter: subject_token");
        }
        if (StringUtils.isEmpty(subjectTokenType)) {
            throw new InvalidRequestException("Missing required parameter: subject_token_type");
        }

        if (settings.getAllowedSubjectTokenTypes() == null ||
                !settings.getAllowedSubjectTokenTypes().contains(subjectTokenType)) {
            throw new InvalidRequestException("Unsupported subject_token_type: " + subjectTokenType);
        }

        // Validate requested_token_type is a supported type (ACCESS_TOKEN or ID_TOKEN)
        Set<String> supportedTypes = Set.of(TokenType.ACCESS_TOKEN, TokenType.ID_TOKEN);
        if (!supportedTypes.contains(requestedTokenType)) {
            throw new InvalidRequestException("Unsupported requested_token_type: " + requestedTokenType);
        }

        // Validate requested_token_type is allowed by domain settings
        if (settings.getAllowedRequestedTokenTypes() == null ||
                !settings.getAllowedRequestedTokenTypes().contains(requestedTokenType)) {
            throw new InvalidRequestException("requested_token_type not allowed: " + requestedTokenType);
        }
    }

    private void validateDelegationAllowed(TokenExchangeSettings settings) {
        if (!settings.isAllowDelegation()) {
            throw new InvalidRequestException("Delegation is not allowed for this domain");
        }
    }

    private void validateDelegationParameters(String actorTokenType,
                                               TokenExchangeSettings settings) {
        if (StringUtils.isEmpty(actorTokenType)) {
            throw new InvalidRequestException("Missing required parameter: actor_token_type (required when actor_token is provided)");
        }

        if (settings.getAllowedActorTokenTypes() == null ||
                !settings.getAllowedActorTokenTypes().contains(actorTokenType)) {
            throw new InvalidRequestException("Unsupported actor_token_type: " + actorTokenType);
        }
    }

    private void validateImpersonationAllowed(TokenExchangeSettings settings) {
        if (!settings.isAllowImpersonation()) {
            throw new InvalidRequestException("Impersonation is not allowed for this domain");
        }
    }

    private Single<ValidatedToken> validateSubjectToken(String token, String tokenType, Domain domain) {
        TokenExchangeSettings settings = domain.getTokenExchangeSettings();
        TokenValidator validator = findValidator(tokenType);
        return validator.validate(token, settings, domain)
                .doOnSuccess(t -> LOGGER.debug("Subject token validated for subject: {}", t.getSubject()))
                .doOnError(error -> LOGGER.debug("Subject token validation failed: {}", error.getMessage()));
    }

    private Single<ValidatedToken> validateActorToken(String token, String tokenType, Domain domain) {
        TokenExchangeSettings settings = domain.getTokenExchangeSettings();
        TokenValidator validator = findValidator(tokenType);
        return validator.validate(token, settings, domain)
                .doOnSuccess(t -> {
                    // Actor token must have a "sub" claim
                    if (StringUtils.isEmpty(t.getSubject())) {
                        throw new InvalidRequestException("Actor token must contain a 'sub' claim");
                    }
                    LOGGER.debug("Actor token validated for subject: {}", t.getSubject());
                })
                .doOnError(error -> LOGGER.debug("Actor token validation failed: {}", error.getMessage()));
    }

    private TokenValidator findValidator(String tokenType) {
        return validators.stream()
                .filter(v -> v.supports(tokenType))
                .findFirst()
                .orElseThrow(() -> new InvalidGrantException("No validator found for token type: " + tokenType));
    }

    /**
     * Calculate the current delegation depth from the subject token.
     * Per RFC 8693 Section 4.1, delegation depth is based on the subject token's "act" claim chain.
     * Depth is the number of nested "act" claims.
     */
    private int calculateDelegationDepth(ValidatedToken subjectToken) {
        Object actClaim = subjectToken.getClaim(Claims.ACT);
        if (actClaim == null) {
            return 0; // No existing delegation chain
        }
        return countActDepth(actClaim);
    }

    @SuppressWarnings("unchecked")
    private int countActDepth(Object actClaim) {
        if (actClaim == null) {
            return 0;
        }
        int depth = 0;
        Object currentAct = actClaim;
        while (currentAct instanceof Map) {
            depth++;
            currentAct = ((Map<String, Object>) currentAct).get(Claims.ACT);
        }
        if (currentAct != null) {
            depth++;
        }
        return depth;
    }

    /**
     * Extract actor information from the validated actor token and subject token.
     * Per RFC 8693 Section 4.1, the subject token's "act" claim represents the
     * prior delegation chain that should be nested under the current actor.
     * The "sub" claim is required per RFC 8693. The "gis" claim is included
     * to support actor identification
     * Additionally, if the actor token itself is a delegated token (has an "act" claim),
     * we capture it as "actor_act" to provide complete audit traceability.
     */
    private ActorTokenInfo extractActorInfo(ValidatedToken actorToken, ValidatedToken subjectToken, int delegationDepth) {
        String subject = actorToken.getSubject();
        String gis = extractGis(actorToken);
        Object subjectTokenActClaim = subjectToken.getClaim(Claims.ACT);
        Object actorTokenActClaim = actorToken.getClaim(Claims.ACT);

        return new ActorTokenInfo(subject, gis, subjectTokenActClaim, actorTokenActClaim, delegationDepth);
    }

    private String extractGis(ValidatedToken token) {
        Object gis = token.getClaim(Claims.GIO_INTERNAL_SUB);
        return gis instanceof String ? (String) gis : null;
    }

    private Single<TokenExchangeResult> buildImpersonationResult(TokenRequest tokenRequest,
                                                                  ValidatedToken subjectToken,
                                                                  ParsedRequest parsedRequest,
                                                                  Client client) {
        return Single.fromCallable(() -> {
            Set<String> grantedScopes = subjectToken.getScopes();
            User user = createUser(subjectToken, grantedScopes, client.getClientId(), false);
            tokenRequest.setScopes(grantedScopes);

            return TokenExchangeResult.forImpersonation(
                    user,
                    parsedRequest.requestedTokenType(),
                    subjectToken.getExpiration(),
                    subjectToken.getTokenId(),
                    parsedRequest.subjectTokenType()
            );
        });
    }

    private Single<TokenExchangeResult> buildDelegationResult(TokenRequest tokenRequest,
                                                               ValidatedToken subjectToken,
                                                               ValidatedToken actorToken,
                                                               ActorTokenInfo actorInfo,
                                                               ParsedRequest parsedRequest,
                                                               Client client) {
        return Single.fromCallable(() -> {
            Set<String> grantedScopes = subjectToken.getScopes();
            User user = createUser(subjectToken, grantedScopes, client.getClientId(), true);
            tokenRequest.setScopes(grantedScopes);

            return TokenExchangeResult.forDelegation(
                    user,
                    parsedRequest.requestedTokenType(),
                    subjectToken.getExpiration(),
                    subjectToken.getTokenId(),
                    parsedRequest.subjectTokenType(),
                    actorToken.getTokenId(),
                    parsedRequest.actorTokenType(),
                    actorInfo
            );
        });
    }

    private User createUser(ValidatedToken subjectToken,
                            Set<String> grantedScopes,
                            String clientId,
                            boolean isDelegation) {
        String subject = subjectToken.getSubject();

        String username = subject;
        Object preferredUsername = subjectToken.getClaim(StandardClaims.PREFERRED_USERNAME);
        if (preferredUsername != null) {
            username = preferredUsername.toString();
        }

        User user = new User();
        user.setId(subject);
        user.setUsername(username);

        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(Claims.SUB, subject);

        Object gioInternalSub = subjectToken.getClaim(Claims.GIO_INTERNAL_SUB);
        if (gioInternalSub instanceof String gioInternalSubValue) {
            if (subjectManager.hasValidInternalSub(gioInternalSubValue)) {
                user.setSource(subjectManager.extractSourceId(gioInternalSubValue));
                user.setExternalId(subjectManager.extractUserId(gioInternalSubValue));
            }
        }

        if (grantedScopes != null && !grantedScopes.isEmpty()) {
            additionalInformation.put(Claims.SCOPE, String.join(" ", grantedScopes));
        }

        additionalInformation.put(Claims.CLIENT_ID, clientId);
        additionalInformation.put("token_exchange", true);
        additionalInformation.put("delegation", isDelegation);

        if (subjectToken.getTokenId() != null) {
            additionalInformation.put("subject_token_id", subjectToken.getTokenId());
        }

        user.setAdditionalInformation(additionalInformation);
        return user;
    }

    /**
     * Internal record for parsed token exchange request parameters.
     */
    private record ParsedRequest(
            String subjectToken,
            String subjectTokenType,
            String actorToken,
            String actorTokenType,
            String scope,
            String requestedTokenType,
            String clientId,
            boolean isDelegation
    ) {}
}
