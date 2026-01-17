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
package io.gravitee.am.extensiongrant.tokenexchange.provider;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenTypeURN;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import io.gravitee.am.extensiongrant.tokenexchange.delegation.ActorClaimBuilder;
import io.gravitee.am.extensiongrant.tokenexchange.delegation.DelegationContext;
import io.gravitee.am.extensiongrant.tokenexchange.delegation.ImpersonationHandler;
import io.gravitee.am.extensiongrant.tokenexchange.delegation.MayActValidator;
import io.gravitee.am.extensiongrant.tokenexchange.validation.SubjectTokenValidator;
import io.gravitee.am.extensiongrant.tokenexchange.validation.SubjectTokenValidatorFactory;
import io.gravitee.am.extensiongrant.tokenexchange.validation.ValidatedToken;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RFC 8693 OAuth 2.0 Token Exchange Extension Grant Provider.
 *
 * This provider implements the token exchange flow as defined in RFC 8693,
 * supporting both impersonation and delegation scenarios.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
public class TokenExchangeExtensionGrantProvider implements ExtensionGrantProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenExchangeExtensionGrantProvider.class);

    @Autowired
    private TokenExchangeExtensionGrantConfiguration configuration;

    private SubjectTokenValidatorFactory validatorFactory;
    private ActorClaimBuilder actorClaimBuilder;
    private MayActValidator mayActValidator;
    private ImpersonationHandler impersonationHandler;

    @PostConstruct
    public void init() {
        this.validatorFactory = new SubjectTokenValidatorFactory();
        this.actorClaimBuilder = new ActorClaimBuilder();
        this.mayActValidator = new MayActValidator();
        this.impersonationHandler = new ImpersonationHandler();
        LOGGER.info("Token Exchange Extension Grant Provider initialized with supported token types: {}",
                validatorFactory.getSupportedTokenTypes());
    }

    @Override
    public Maybe<User> grant(TokenRequest tokenRequest) throws InvalidGrantException {
        return Single.fromCallable(() -> parseRequest(tokenRequest))
                .flatMap(request -> validateSubjectToken(request)
                        .flatMap((ValidatedToken subjectToken) -> {
                            Maybe<ValidatedToken> actorTokenMaybe = validateActorTokenIfPresent(request);
                            return actorTokenMaybe
                                    .flatMapSingle((ValidatedToken actorToken) ->
                                            buildDelegationContext(subjectToken, actorToken, request)
                                                    .map(context -> createUser(context, request)))
                                    .switchIfEmpty(Single.defer(() ->
                                            buildDelegationContext(subjectToken, null, request)
                                                    .map(context -> createUser(context, request))));
                        })
                )
                .subscribeOn(Schedulers.io())
                .toMaybe()
                .observeOn(Schedulers.computation());
    }

    /**
     * Build the delegation context based on the validated tokens and request.
     */
    private Single<DelegationContext> buildDelegationContext(ValidatedToken subjectToken,
                                                              ValidatedToken actorToken,
                                                              TokenExchangeRequest request) {
        return Single.fromCallable(() -> {
            DelegationContext.DelegationContextBuilder builder = DelegationContext.builder()
                    .subjectToken(subjectToken)
                    .actorToken(actorToken)
                    .audience(request.getAudience())
                    .resource(request.getResource())
                    .requestedTokenType(request.getRequestedTokenType())
                    .clientId(request.getClientId());

            // Determine delegation vs impersonation scenario
            boolean hasActorToken = actorToken != null;
            boolean isImpersonation = impersonationHandler.isImpersonationScenario(configuration, hasActorToken);

            if (isImpersonation) {
                // Validate impersonation is allowed
                if (!configuration.isAllowImpersonation()) {
                    throw new InvalidGrantException("Impersonation is not allowed");
                }

                // Determine impersonation type
                if (!hasActorToken) {
                    builder.asDirectImpersonation();
                    LOGGER.debug("Processing direct impersonation for subject: {}", subjectToken.getSubject());
                } else {
                    builder.asDelegatedImpersonation();
                    LOGGER.debug("Processing delegated impersonation for subject: {} by actor: {}",
                            subjectToken.getSubject(), actorToken.getSubject());
                }

                // Create audit info for impersonation
                ImpersonationHandler.ImpersonationType impersonationType = hasActorToken ?
                        ImpersonationHandler.ImpersonationType.DELEGATED :
                        ImpersonationHandler.ImpersonationType.DIRECT;
                Map<String, Object> auditInfo = impersonationHandler.createAuditInfo(
                        subjectToken, actorToken, request.getClientId(), impersonationType);
                builder.auditInfo(auditInfo);

            } else if (hasActorToken && configuration.isAllowDelegation()) {
                // Delegation scenario - validate may_act claim
                MayActValidator.ValidationResult mayActResult = mayActValidator.validateMayAct(
                        subjectToken, actorToken, request.getAudience());

                if (!mayActResult.isValid()) {
                    throw new InvalidGrantException("Delegation denied: " + mayActResult.getReason());
                }

                // Build actor claim
                Map<String, Object> actClaim = actorClaimBuilder.buildActorClaim(
                        actorToken, subjectToken.getActClaim(), configuration);
                int chainDepth = actorClaimBuilder.calculateDelegationDepth(actClaim);

                builder.asDelegation(actClaim, chainDepth);
                LOGGER.debug("Processing delegation for subject: {} by actor: {}, chain depth: {}",
                        subjectToken.getSubject(), actorToken.getSubject(), chainDepth);

                // Create delegation audit info
                Map<String, Object> auditInfo = new LinkedHashMap<>();
                auditInfo.put("event_type", "TOKEN_EXCHANGE_DELEGATION");
                auditInfo.put("subject", subjectToken.getSubject());
                auditInfo.put("actor", actorToken.getSubject());
                auditInfo.put("chain_depth", chainDepth);
                auditInfo.put("client_id", request.getClientId());
                auditInfo.put("timestamp", System.currentTimeMillis());
                builder.auditInfo(auditInfo);

            } else {
                // Simple token exchange (no delegation, no impersonation)
                builder.asSimpleExchange();
                LOGGER.debug("Processing simple token exchange for subject: {}", subjectToken.getSubject());
            }

            // Determine granted scopes
            Set<String> grantedScopes = determineGrantedScopes(subjectToken.getScopes(), request.getScope());
            builder.grantedScopes(grantedScopes);

            return builder.build();
        });
    }

    /**
     * Parse the token exchange request parameters.
     */
    private TokenExchangeRequest parseRequest(TokenRequest tokenRequest) throws InvalidGrantException {
        Map<String, String> params = tokenRequest.getRequestParameters();

        String subjectToken = params.get(Parameters.SUBJECT_TOKEN);
        String subjectTokenType = params.get(Parameters.SUBJECT_TOKEN_TYPE);
        String actorToken = params.get(Parameters.ACTOR_TOKEN);
        String actorTokenType = params.get(Parameters.ACTOR_TOKEN_TYPE);
        String resource = params.get(Parameters.RESOURCE);
        String audience = params.get(Parameters.AUDIENCE);
        String scope = params.get(Parameters.SCOPE);
        String requestedTokenType = params.get(Parameters.REQUESTED_TOKEN_TYPE);

        // Validate required parameters
        if (subjectToken == null || subjectToken.isEmpty()) {
            throw new InvalidGrantException("Missing required parameter: subject_token");
        }
        if (subjectTokenType == null || subjectTokenType.isEmpty()) {
            throw new InvalidGrantException("Missing required parameter: subject_token_type");
        }

        // Validate actor_token_type is present when actor_token is provided
        if (actorToken != null && !actorToken.isEmpty()) {
            if (actorTokenType == null || actorTokenType.isEmpty()) {
                throw new InvalidGrantException("actor_token_type is required when actor_token is present");
            }
        }

        // Validate subject_token_type is allowed
        if (!configuration.getAllowedSubjectTokenTypes().contains(subjectTokenType)) {
            throw new InvalidGrantException("Unsupported subject_token_type: " + subjectTokenType);
        }

        // Validate actor_token_type is allowed (if present)
        if (actorTokenType != null && !configuration.getAllowedActorTokenTypes().contains(actorTokenType)) {
            throw new InvalidGrantException("Unsupported actor_token_type: " + actorTokenType);
        }

        // Validate requested_token_type is allowed (if present)
        if (requestedTokenType != null && !configuration.getAllowedRequestedTokenTypes().contains(requestedTokenType)) {
            throw new InvalidGrantException("Unsupported requested_token_type: " + requestedTokenType);
        }

        // Validate audience if required
        if (configuration.isRequireAudience() && (audience == null || audience.isEmpty())) {
            throw new InvalidGrantException("Missing required parameter: audience");
        }

        // Check delegation is allowed when actor_token is present
        if (actorToken != null && !actorToken.isEmpty() && !configuration.isAllowDelegation()) {
            throw new InvalidGrantException("Delegation is not allowed");
        }

        // Extract client_id from request
        String clientId = tokenRequest.getClientId();

        return TokenExchangeRequest.builder()
                .subjectToken(subjectToken)
                .subjectTokenType(subjectTokenType)
                .actorToken(actorToken)
                .actorTokenType(actorTokenType)
                .resource(resource)
                .audience(audience)
                .scope(scope)
                .requestedTokenType(requestedTokenType != null ? requestedTokenType : TokenTypeURN.ACCESS_TOKEN)
                .clientId(clientId)
                .build();
    }

    /**
     * Validate the subject token using the appropriate validator.
     */
    private Single<ValidatedToken> validateSubjectToken(TokenExchangeRequest request) {
        try {
            SubjectTokenValidator validator = validatorFactory.getValidator(request.getSubjectTokenType());
            return validator.validate(request.getSubjectToken(), configuration)
                    .doOnSuccess(token -> LOGGER.debug("Subject token validated successfully for subject: {}", token.getSubject()))
                    .doOnError(error -> LOGGER.debug("Subject token validation failed: {}", error.getMessage()));
        } catch (InvalidGrantException e) {
            return Single.error(e);
        }
    }

    /**
     * Validate the actor token if present.
     */
    private Maybe<ValidatedToken> validateActorTokenIfPresent(TokenExchangeRequest request) {
        if (request.getActorToken() == null || request.getActorToken().isEmpty()) {
            return Maybe.empty();
        }

        try {
            SubjectTokenValidator validator = validatorFactory.getValidator(request.getActorTokenType());
            return validator.validate(request.getActorToken(), configuration)
                    .doOnSuccess(token -> LOGGER.debug("Actor token validated successfully for subject: {}", token.getSubject()))
                    .doOnError(error -> LOGGER.debug("Actor token validation failed: {}", error.getMessage()))
                    .toMaybe();
        } catch (InvalidGrantException e) {
            return Maybe.error(e);
        }
    }

    /**
     * Create a user from the delegation context.
     */
    private User createUser(DelegationContext context, TokenExchangeRequest request) {
        ValidatedToken subjectToken = context.getSubjectToken();
        String subject = context.getEffectiveSubject();

        // Get username from claims or use subject
        String username = subject;
        Object preferredUsername = subjectToken.getClaim(StandardClaims.PREFERRED_USERNAME);
        if (preferredUsername != null) {
            username = preferredUsername.toString();
        }

        DefaultUser user = new DefaultUser(username);
        user.setId(subject);

        // Build additional information
        Map<String, Object> additionalInformation = new HashMap<>();

        // Add subject claim
        additionalInformation.put(Claims.SUB, subject);

        // Preserve GIO_INTERNAL_SUB if present
        Object gioInternalSub = subjectToken.getClaim(Claims.GIO_INTERNAL_SUB);
        if (gioInternalSub != null) {
            additionalInformation.put(Claims.GIO_INTERNAL_SUB, gioInternalSub);
        }

        // Handle scopes from delegation context
        Set<String> grantedScopes = context.getGrantedScopes();
        if (grantedScopes != null && !grantedScopes.isEmpty()) {
            additionalInformation.put(Claims.SCOPE, String.join(" ", grantedScopes));
        }

        // Add actor claim for delegation (but not for impersonation)
        if (context.isDelegation() && context.getActorClaim() != null) {
            additionalInformation.put(Claims.ACT, context.getActorClaim());
        }

        // Store token exchange metadata
        additionalInformation.put("token_exchange", true);
        additionalInformation.put("subject_token_type", request.getSubjectTokenType());
        additionalInformation.put("requested_token_type", request.getRequestedTokenType());
        additionalInformation.put("delegation_type", context.getDelegationType().name());

        if (context.isImpersonation()) {
            additionalInformation.put("impersonation", true);
        }

        if (request.getAudience() != null) {
            additionalInformation.put("audience", request.getAudience());
        }
        if (request.getResource() != null) {
            additionalInformation.put("resource", request.getResource());
        }

        // Add actor information for audit purposes (even in impersonation)
        if (context.hasActorToken()) {
            additionalInformation.put("actor_subject", context.getActorSubject());
        }

        // Add delegation chain depth if applicable
        if (context.getDelegationChainDepth() > 0) {
            additionalInformation.put("delegation_chain_depth", context.getDelegationChainDepth());
        }

        // Store audit info
        if (context.getAuditInfo() != null) {
            additionalInformation.put("audit_info", context.getAuditInfo());
        }

        // Apply claims mapping if configured
        List<TokenExchangeExtensionGrantConfiguration.ClaimMapping> claimsMapper = configuration.getClaimsMapper();
        if (claimsMapper != null && !claimsMapper.isEmpty()) {
            for (TokenExchangeExtensionGrantConfiguration.ClaimMapping mapping : claimsMapper) {
                Object value = subjectToken.getClaim(mapping.getSourceClaim());
                if (value != null) {
                    additionalInformation.put(mapping.getTargetClaim(), value);
                }
            }
        }

        user.setAdditionalInformation(additionalInformation);
        return user;
    }

    /**
     * Determine the granted scopes based on the scope policy.
     */
    private Set<String> determineGrantedScopes(Set<String> originalScopes, String requestedScope) {
        if (originalScopes == null) {
            originalScopes = Collections.emptySet();
        }

        if (requestedScope == null || requestedScope.isEmpty()) {
            // No scope requested, use original scopes
            return originalScopes;
        }

        Set<String> requested = new HashSet<>(Arrays.asList(requestedScope.split("\\s+")));

        switch (configuration.getScopePolicy()) {
            case REDUCE:
                // Only grant scopes that are both requested and in original
                Set<String> reduced = new HashSet<>(requested);
                reduced.retainAll(originalScopes);
                return reduced;
            case PRESERVE:
                // Ignore requested, use original
                return originalScopes;
            case CUSTOM:
                // Grant all requested scopes
                return requested;
            default:
                return originalScopes;
        }
    }

    /**
     * Internal class representing a parsed token exchange request.
     */
    @lombok.Builder
    @lombok.Getter
    private static class TokenExchangeRequest {
        private final String subjectToken;
        private final String subjectTokenType;
        private final String actorToken;
        private final String actorTokenType;
        private final String resource;
        private final String audience;
        private final String scope;
        private final String requestedTokenType;
        private final String clientId;
    }
}
