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
                .flatMap(request -> validateSubjectToken(request, domain)
                        .flatMap(subjectToken -> buildExchangeResult(tokenRequest, subjectToken, request, client)));
    }

    private ParsedRequest parseRequest(TokenRequest tokenRequest, Domain domain) {
        Map<String, String> params = tokenRequest.parameters().toSingleValueMap();
        TokenExchangeSettings settings = domain.getTokenExchangeSettings();

        if (settings == null || !settings.isEnabled()) {
            throw new InvalidRequestException("Token exchange is not enabled for this domain");
        }

        String subjectToken = params.get(Parameters.SUBJECT_TOKEN);
        String subjectTokenType = params.get(Parameters.SUBJECT_TOKEN_TYPE);
        String scope = params.get(Parameters.SCOPE);
        String requestedTokenType = params.get(Parameters.REQUESTED_TOKEN_TYPE);

        // Default requested token type to access_token
        if (StringUtils.isEmpty(requestedTokenType)) {
            requestedTokenType = TokenType.ACCESS_TOKEN;
        }
        // For now use only
        validateParameters(subjectToken, subjectTokenType, requestedTokenType, settings);

        // Currently only impersonation is supported — reject if not allowed
        boolean impersonationRequested = true;
        if (!settings.isAllowImpersonation() && impersonationRequested) {
            throw new InvalidRequestException("Impersonation is not allowed for this domain");
        }

        return new ParsedRequest(
                subjectToken,
                subjectTokenType,
                scope,
                requestedTokenType,
                tokenRequest.getClientId(),
                impersonationRequested
        );
    }

    private void validateParameters(String subjectToken, String subjectTokenType,
                                     String requestedTokenType, TokenExchangeSettings settings) {
        // Validate required parameters
        if (subjectToken == null || subjectToken.isEmpty()) {
            throw new InvalidRequestException("Missing required parameter: subject_token");
        }
        if (subjectTokenType == null || subjectTokenType.isEmpty()) {
            throw new InvalidRequestException("Missing required parameter: subject_token_type");
        }

        // Validate subject_token_type is allowed
        if (settings.getAllowedSubjectTokenTypes() == null ||
                !settings.getAllowedSubjectTokenTypes().contains(subjectTokenType)) {
            throw new InvalidRequestException("Unsupported subject_token_type: " + subjectTokenType);
        }

        // Ensure the requested_token_type is supported
        if (!TokenType.ACCESS_TOKEN.equals(requestedTokenType)) {
            throw new InvalidRequestException("Unsupported requested_token_type: " + requestedTokenType);
        }
    }

    private Single<ValidatedToken> validateSubjectToken(ParsedRequest request, Domain domain) {
        TokenExchangeSettings settings = domain.getTokenExchangeSettings();
        TokenValidator validator = findValidator(request.subjectTokenType());
        return validator.validate(request.subjectToken(), settings, domain)
                .doOnSuccess(token -> LOGGER.debug("Subject token validated for subject: {}", token.getSubject()))
                .doOnError(error -> LOGGER.debug("Subject token validation failed: {}", error.getMessage()));
    }

    private TokenValidator findValidator(String tokenType) {
        return validators.stream()
                .filter(v -> v.supports(tokenType))
                .findFirst()
                .orElseThrow(() -> new InvalidGrantException("No validator found for token type: " + tokenType));
    }

    private Single<TokenExchangeResult> buildExchangeResult(TokenRequest tokenRequest,
                                                              ValidatedToken subjectToken,
                                                              ParsedRequest parsedRequest,
                                                              Client client) {
        return Single.fromCallable(() -> {
            // Copy all scopes from the subject token
            // TODO: Handle scopes based on settings in AM-6291
            Set<String> grantedScopes = subjectToken.getScopes();

            // Build user
        User user = createUser(subjectToken, parsedRequest, grantedScopes, client.getClientId());

            // Set scopes on the token request
            tokenRequest.setScopes(grantedScopes);

            // Return result with token metadata
            return new TokenExchangeResult(
                    user,
                    parsedRequest.requestedTokenType(), // issued_token_type = requested_token_type
                    subjectToken.getExpiration(),
                    subjectToken.getTokenId(),
                    parsedRequest.subjectTokenType()
            );
        });
    }

    private User createUser(ValidatedToken subjectToken,
                            ParsedRequest request,
                            Set<String> grantedScopes,
                            String clientId) {
        String subject = subjectToken.getSubject();

        // Get username from claims or use subject
        String username = subject;
        Object preferredUsername = subjectToken.getClaim(StandardClaims.PREFERRED_USERNAME);
        if (preferredUsername != null) {
            username = preferredUsername.toString();
        }

        User user = new User();
        user.setId(subject);
        user.setUsername(username);

        // Build additional information
        Map<String, Object> additionalInformation = new HashMap<>();

        // Add subject claim
        additionalInformation.put(Claims.SUB, subject);

        // Preserve GIO_INTERNAL_SUB if present
        Object gioInternalSub = subjectToken.getClaim(Claims.GIO_INTERNAL_SUB);
        if (gioInternalSub instanceof String gioInternalSubValue) {
            if (subjectManager.hasValidInternalSub(gioInternalSubValue)) {
                user.setSource(subjectManager.extractSourceId(gioInternalSubValue));
                user.setExternalId(subjectManager.extractUserId(gioInternalSubValue));
            }
        }

        // Handle scopes
        if (grantedScopes != null && !grantedScopes.isEmpty()) {
            additionalInformation.put(Claims.SCOPE, String.join(" ", grantedScopes));
        }

        // Add client_id claim (the client performing the exchange)
        additionalInformation.put(Claims.CLIENT_ID, clientId);

        // Store token exchange metadata
        additionalInformation.put("token_exchange", true);
        additionalInformation.put("impersonation", request.impersonationRequested());
        additionalInformation.put("subject_token_type", request.subjectTokenType());
        additionalInformation.put("requested_token_type", request.requestedTokenType());
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
            String scope,
            String requestedTokenType,
            String clientId,
            boolean impersonationRequested
    ) {}
}
