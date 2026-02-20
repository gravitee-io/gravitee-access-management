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
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ActorTokenInfo;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenValidator;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeResult;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeService;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.el.TemplateEngine;
import io.reactivex.rxjava3.core.Single;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TokenExchangeServiceImpl implements TokenExchangeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenExchangeServiceImpl.class);

    private final List<TokenValidator> validators;
    private final SubjectManager subjectManager;
    private final ProtectedResourceManager protectedResourceManager;
    private final UserGatewayService userGatewayService;

    public TokenExchangeServiceImpl(List<TokenValidator> validators,
                                    SubjectManager subjectManager,
                                    ProtectedResourceManager protectedResourceManager,
                                    UserGatewayService userGatewayService) {
        this.validators = validators;
        this.subjectManager = subjectManager;
        this.protectedResourceManager = protectedResourceManager;
        this.userGatewayService = userGatewayService;
    }

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
                .flatMap(subjectToken -> {
                    // Reject JWT actor tokens when subject is from an external trusted issuer.
                    // This prevents transitive trust chains across external issuers.
                    if (subjectToken.isTrustedIssuerValidated() && TokenType.JWT.equals(request.actorTokenType())) {
                        return Single.error(new InvalidGrantException(
                                "Actor token type JWT not allowed with external subject token"));
                    }
                    return validateActorToken(request.actorToken(), request.actorTokenType(), domain)
                                .flatMap(actorToken -> {
                                    // Per RFC 8693 Section 4.1, delegation depth is based on
                                    // the subject token's "act" claim chain
                                    int currentDepth = calculateDelegationDepth(subjectToken);
                                    int resultingDepth = currentDepth + 1;
                                    int maxDepth = settings.getMaxDelegationDepth();

                                    if (resultingDepth > maxDepth) {
                                        return Single.error(new InvalidRequestException(
                                                "Maximum delegation depth exceeded. Current: " + currentDepth +
                                                        ", Max allowed: " + maxDepth));
                                    }

                                    ActorTokenInfo actorInfo = extractActorInfo(actorToken, subjectToken, resultingDepth);
                                    return buildDelegationResult(tokenRequest, subjectToken, actorToken, actorInfo, request, client);
                                });
                });
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
            if (!StringUtils.isEmpty(actorTokenType)) {
                throw new InvalidRequestException("actor_token_type must not be provided when actor_token is not provided");
            }
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
                isDelegation,
                domain
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

    /**
     * Allowed = base ∩ scopePool; scopePool = client ∪ resource (when resource present), else client only.
     * When client has no scope settings and no resource scopes, scopePool stays empty: fail closed, grant no scopes.
     */
    private Set<String> computeAllowedScopesWithResource(Set<String> requestedResourceUris,
            Set<String> baseAllowedScopes,
            Set<String> clientScopes) {
        if (baseAllowedScopes.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> scopePool = new HashSet<>(clientScopes);
        if (requestedResourceUris != null && !requestedResourceUris.isEmpty()) {
            scopePool.addAll(protectedResourceManager.getScopesForResources(requestedResourceUris));
        }
        Set<String> allowedScopes = new HashSet<>(baseAllowedScopes);
        allowedScopes.retainAll(scopePool);
        return allowedScopes;
    }

    private static Set<String> getClientScopes(Client client) {
        if (client.getScopeSettings() == null || client.getScopeSettings().isEmpty()) {
            return Collections.emptySet();
        }
        return client.getScopeSettings().stream()
                .map(ApplicationScopeSettings::getScope)
                .collect(Collectors.toSet());
    }

    /**
     * Resolves allowed scopes from base (subject or subject∩actor), client and resource, then granted from requested.
     * Requested ⊆ allowed; if omitted grant full allowed. Throws InvalidScopeException if not allowed.
     */
    private Single<Set<String>> computeGrantedScopes(Set<String> requestedScopes, Set<String> baseAllowedScopes,
                                                      TokenRequest tokenRequest, Client client) {
        Set<String> clientScopes = getClientScopes(client);
        Set<String> allowedScopes = computeAllowedScopesWithResource(tokenRequest.getResources(), baseAllowedScopes, clientScopes);
        if (requestedScopes == null || requestedScopes.isEmpty()) {
            return Single.just(allowedScopes);
        }
        if (allowedScopes.containsAll(requestedScopes)) {
            return Single.just(requestedScopes);
        }
        return Single.error(new InvalidScopeException("Requested scope is not allowed"));
    }

    private Single<TokenExchangeResult> buildImpersonationResult(TokenRequest tokenRequest,
                                                                  ValidatedToken subjectToken,
                                                                  ParsedRequest parsedRequest,
                                                                  Client client) {
        Domain domain = parsedRequest.domain();
        Set<String> requestedScopes = Optional.ofNullable(ParamUtils.splitScopes(parsedRequest.scope())).orElse(Collections.emptySet());
        Set<String> baseAllowed = Optional.ofNullable(subjectToken.getScopes()).orElse(Collections.emptySet());
        return computeGrantedScopes(requestedScopes, baseAllowed, tokenRequest, client)
                .flatMap(grantedScopes -> resolveUser(subjectToken, grantedScopes, client.getClientId(), false, domain)
                        .map(user -> {
                            tokenRequest.setScopes(grantedScopes);
                            return TokenExchangeResult.forImpersonation(
                                    user,
                                    parsedRequest.requestedTokenType(),
                                    subjectToken.getExpiration(),
                                    subjectToken.getTokenId(),
                                    parsedRequest.subjectTokenType());
                        }));
    }

    private Single<TokenExchangeResult> buildDelegationResult(TokenRequest tokenRequest,
                                                               ValidatedToken subjectToken,
                                                               ValidatedToken actorToken,
                                                               ActorTokenInfo actorInfo,
                                                               ParsedRequest parsedRequest,
                                                               Client client) {
        Domain domain = parsedRequest.domain();
        Set<String> requestedScopes = Optional.ofNullable(ParamUtils.splitScopes(parsedRequest.scope())).orElse(Collections.emptySet());
        Set<String> baseAllowed = new HashSet<>(Optional.ofNullable(subjectToken.getScopes()).orElse(Collections.emptySet()));
        baseAllowed.retainAll(Optional.ofNullable(actorToken.getScopes()).orElse(Collections.emptySet()));
        return computeGrantedScopes(requestedScopes, baseAllowed, tokenRequest, client)
                .flatMap(grantedScopes -> resolveUser(subjectToken, grantedScopes, client.getClientId(), true, domain)
                        .map(user -> {
                            tokenRequest.setScopes(grantedScopes);
                            return TokenExchangeResult.forDelegation(
                                    user,
                                    parsedRequest.requestedTokenType(),
                                    subjectToken.getExpiration(),
                                    subjectToken.getTokenId(),
                                    parsedRequest.subjectTokenType(),
                                    actorToken.getTokenId(),
                                    parsedRequest.actorTokenType(),
                                    actorInfo);
                        }));
    }

    /**
     * Resolve the user for the minted token. When user binding is enabled for a trusted issuer,
     * look up the corresponding domain user. Otherwise, build a synthetic user from token claims.
     */
    private Single<User> resolveUser(ValidatedToken subjectToken,
                                     Set<String> grantedScopes,
                                     String clientId,
                                     boolean isDelegation,
                                     Domain domain) {
        if (!subjectToken.isTrustedIssuerValidated()) {
            return Single.fromCallable(() -> createSyntheticUser(subjectToken, grantedScopes, clientId, isDelegation));
        }

        TrustedIssuer matchingIssuer = findMatchingTrustedIssuer(subjectToken.getIssuer(), domain);
        if (matchingIssuer == null || !matchingIssuer.isUserBindingEnabled()) {
            return Single.fromCallable(() -> createSyntheticUser(subjectToken, grantedScopes, clientId, isDelegation));
        }

        Map<String, Object> claims = subjectToken.getClaims() != null ? subjectToken.getClaims() : Collections.emptyMap();
        FilterCriteria criteria = buildFilterCriteria(matchingIssuer.getUserBindingMappings(), claims, matchingIssuer.getIssuer());

        return userGatewayService.findByCriteria(criteria)
                .flatMap(users -> {
                    if (users.isEmpty()) {
                        return Single.error(new InvalidGrantException(
                                "No matching domain user found for trusted issuer: " + matchingIssuer.getIssuer()));
                    }
                    if (users.size() > 1) {
                        return Single.error(new InvalidGrantException(
                                "Multiple domain users match trusted issuer binding criteria"));
                    }
                    return userGatewayService.enhance(users.getFirst());
                })
                .map(user -> {
                    applyTokenExchangeMetadata(user, subjectToken, grantedScopes, clientId, isDelegation);
                    return user;
                });
    }

    private TrustedIssuer findMatchingTrustedIssuer(String issuer, Domain domain) {
        TokenExchangeSettings settings = domain.getTokenExchangeSettings();
        if (settings == null || settings.getTrustedIssuers() == null || issuer == null) {
            return null;
        }
        return settings.getTrustedIssuers().stream()
                .filter(ti -> issuer.equals(ti.getIssuer()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Evaluate a mapping value against token claims.
     * If value starts with '{' and ends with '}', evaluate as EL expression.
     * Otherwise, treat as a direct claim name lookup.
     * Following the pattern from DefaultIdentityProviderMapper.
     */
    private String evaluateMapping(String expression, Map<String, Object> claims) {
        String trimmed = expression.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            TemplateEngine templateEngine = TemplateEngine.templateEngine();
            templateEngine.getTemplateContext().setVariable("token", claims);
            try {
                Object result = templateEngine.getValue(trimmed, Object.class);
                return result != null ? result.toString() : null;
            } catch (Exception e) {
                LOGGER.warn("Failed to evaluate user binding expression: {}", trimmed, e);
                return null;
            }
        }
        Object value = claims.get(trimmed);
        return value != null ? value.toString() : null;
    }

    /**
     * Build FilterCriteria from user binding mappings and token claims.
     * Following the CIBA pattern from CibaAuthenticationRequestResolver.
     */
    private FilterCriteria buildFilterCriteria(Map<String, String> mappings, Map<String, Object> claims, String issuer) {
        List<FilterCriteria> criteriaList = new ArrayList<>();
        for (var entry : mappings.entrySet()) {
            String userAttribute = entry.getKey();
            String claimExpression = entry.getValue();
            String resolvedValue = evaluateMapping(claimExpression, claims);

            if (resolvedValue == null || resolvedValue.isBlank()) {
                throw new InvalidGrantException(
                        "User binding claim '" + claimExpression + "' resolved to empty value for trusted issuer: " + issuer);
            }

            FilterCriteria criteria = new FilterCriteria();
            criteria.setOperator("eq");
            criteria.setFilterName(userAttribute);
            criteria.setFilterValue(resolvedValue);
            criteria.setQuoteFilterValue(true);
            criteriaList.add(criteria);
        }

        if (criteriaList.size() == 1) {
            return criteriaList.getFirst();
        }

        FilterCriteria andCriteria = new FilterCriteria();
        andCriteria.setOperator("and");
        andCriteria.setFilterComponents(criteriaList);
        return andCriteria;
    }

    private void applyTokenExchangeMetadata(User user, ValidatedToken subjectToken,
                                             Set<String> grantedScopes, String clientId, boolean isDelegation) {
        Map<String, Object> additionalInformation = user.getAdditionalInformation();
        if (additionalInformation == null) {
            additionalInformation = new HashMap<>();
            user.setAdditionalInformation(additionalInformation);
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
    }

    private User createSyntheticUser(ValidatedToken subjectToken,
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
            boolean isDelegation,
            Domain domain
    ) {}
}
