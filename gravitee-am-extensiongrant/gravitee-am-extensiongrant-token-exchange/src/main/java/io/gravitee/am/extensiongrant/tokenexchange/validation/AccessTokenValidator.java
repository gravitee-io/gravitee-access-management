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
package io.gravitee.am.extensiongrant.tokenexchange.validation;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.TokenTypeURN;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.*;

/**
 * Validator for Access Tokens in RFC 8693 Token Exchange.
 *
 * This validator handles tokens of type:
 * - urn:ietf:params:oauth:token-type:access_token
 *
 * Access tokens issued by Gravitee AM are JWTs, so this validator
 * parses and validates them as JWTs while applying access-token-specific
 * validation rules.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
public class AccessTokenValidator implements SubjectTokenValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessTokenValidator.class);

    private final JwtSubjectTokenValidator jwtValidator;

    public AccessTokenValidator() {
        this.jwtValidator = new JwtSubjectTokenValidator(TokenTypeURN.ACCESS_TOKEN);
    }

    @Override
    public Single<ValidatedToken> validate(String token, TokenExchangeExtensionGrantConfiguration configuration)
            throws InvalidGrantException {
        return Single.fromCallable(() -> doValidate(token, configuration));
    }

    private ValidatedToken doValidate(String token, TokenExchangeExtensionGrantConfiguration configuration)
            throws InvalidGrantException {
        try {
            // Try to parse as JWT (AM access tokens are JWTs)
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Validate issuer if trusted issuers are configured
            String issuer = claims.getIssuer();
            validateIssuer(issuer, configuration);

            // Validate temporal claims
            validateTemporalClaims(claims);

            // Extract scopes
            Set<String> scopes = extractScopes(claims);

            // Check delegation chain depth
            Object actClaim = claims.getClaim(Claims.ACT);
            validateDelegationChainDepth(actClaim, configuration);

            // Extract domain from claims
            String domain = claims.getStringClaim(Claims.DOMAIN);

            return ValidatedToken.builder()
                    .subject(claims.getSubject())
                    .issuer(issuer)
                    .claims(claims.getClaims())
                    .scopes(scopes)
                    .expiration(claims.getExpirationTime())
                    .issuedAt(claims.getIssueTime())
                    .notBefore(claims.getNotBeforeTime())
                    .tokenId(claims.getJWTID())
                    .audience(claims.getAudience())
                    .actClaim(actClaim)
                    .mayActClaim(claims.getClaim(Claims.MAY_ACT))
                    .clientId(claims.getStringClaim(Claims.CLIENT_ID))
                    .tokenType(TokenTypeURN.ACCESS_TOKEN)
                    .domain(domain)
                    .build();

        } catch (ParseException e) {
            LOGGER.debug("Failed to parse access token as JWT: {}", e.getMessage());
            throw new InvalidGrantException("Invalid access token format: " + e.getMessage());
        } catch (InvalidGrantException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error validating access token: {}", e.getMessage(), e);
            throw new InvalidGrantException("Error validating access token: " + e.getMessage());
        }
    }

    /**
     * Validate the issuer against trusted issuers list.
     */
    private void validateIssuer(String issuer, TokenExchangeExtensionGrantConfiguration configuration)
            throws InvalidGrantException {
        Set<String> trustedIssuers = configuration.getTrustedIssuers();
        if (trustedIssuers != null && !trustedIssuers.isEmpty()) {
            if (issuer == null || !trustedIssuers.contains(issuer)) {
                throw new InvalidGrantException("Untrusted issuer: " + issuer);
            }
        }
    }

    /**
     * Validate temporal claims (exp, nbf).
     */
    private void validateTemporalClaims(JWTClaimsSet claims) throws InvalidGrantException {
        Date now = new Date();

        // Check expiration
        Date expiration = claims.getExpirationTime();
        if (expiration != null && expiration.before(now)) {
            throw new InvalidGrantException("Access token has expired");
        }

        // Check not before
        Date notBefore = claims.getNotBeforeTime();
        if (notBefore != null && notBefore.after(now)) {
            throw new InvalidGrantException("Access token is not yet valid");
        }
    }

    /**
     * Validate delegation chain depth.
     */
    private void validateDelegationChainDepth(Object actClaim, TokenExchangeExtensionGrantConfiguration configuration)
            throws InvalidGrantException {
        int depth = calculateDelegationDepth(actClaim);
        if (depth >= configuration.getMaxDelegationChainDepth()) {
            throw new InvalidGrantException("Delegation chain depth exceeds maximum allowed: " +
                    configuration.getMaxDelegationChainDepth());
        }
    }

    /**
     * Calculate the depth of the delegation chain.
     */
    private int calculateDelegationDepth(Object actClaim) {
        if (actClaim == null) {
            return 0;
        }
        if (!(actClaim instanceof Map)) {
            return 0;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> act = (Map<String, Object>) actClaim;
        Object nestedAct = act.get("act");
        return 1 + calculateDelegationDepth(nestedAct);
    }

    /**
     * Extract scopes from JWT claims.
     */
    private Set<String> extractScopes(JWTClaimsSet claims) {
        Object scopeClaim = claims.getClaim(Claims.SCOPE);
        Set<String> scopes = new HashSet<>();

        if (scopeClaim instanceof String) {
            String[] scopeArray = ((String) scopeClaim).split("\\s+");
            scopes.addAll(Arrays.asList(scopeArray));
        } else if (scopeClaim instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> scopeList = (List<String>) scopeClaim;
            scopes.addAll(scopeList);
        }

        return scopes;
    }

    @Override
    public String getSupportedTokenType() {
        return TokenTypeURN.ACCESS_TOKEN;
    }
}
