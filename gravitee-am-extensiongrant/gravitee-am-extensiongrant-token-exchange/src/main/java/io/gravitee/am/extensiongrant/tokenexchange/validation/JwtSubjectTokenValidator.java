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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.TokenTypeURN;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.ParseException;
import java.util.*;

/**
 * Validator for JWT tokens in RFC 8693 Token Exchange.
 *
 * This validator handles tokens of type:
 * - urn:ietf:params:oauth:token-type:jwt
 * - urn:ietf:params:oauth:token-type:id_token
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
public class JwtSubjectTokenValidator implements SubjectTokenValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtSubjectTokenValidator.class);

    private final String supportedTokenType;

    public JwtSubjectTokenValidator() {
        this(TokenTypeURN.JWT);
    }

    public JwtSubjectTokenValidator(String tokenType) {
        this.supportedTokenType = tokenType;
    }

    @Override
    public Single<ValidatedToken> validate(String token, TokenExchangeExtensionGrantConfiguration configuration)
            throws InvalidGrantException {
        return Single.fromCallable(() -> doValidate(token, configuration));
    }

    private ValidatedToken doValidate(String token, TokenExchangeExtensionGrantConfiguration configuration)
            throws InvalidGrantException {
        try {
            // Parse the JWT
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Validate issuer if trusted issuers are configured
            String issuer = claims.getIssuer();
            validateIssuer(issuer, configuration);

            // Validate signature if required
            if (configuration.isValidateSignature()) {
                validateSignature(signedJWT, configuration);
            }

            // Validate temporal claims
            validateTemporalClaims(claims);

            // Extract scopes
            Set<String> scopes = extractScopes(claims);

            // Check delegation chain depth
            Object actClaim = claims.getClaim(Claims.ACT);
            validateDelegationChainDepth(actClaim, configuration);

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
                    .tokenType(supportedTokenType)
                    .build();

        } catch (ParseException e) {
            LOGGER.debug("Failed to parse JWT: {}", e.getMessage());
            throw new InvalidGrantException("Invalid JWT format: " + e.getMessage());
        } catch (InvalidGrantException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error validating JWT: {}", e.getMessage(), e);
            throw new InvalidGrantException("Error validating JWT: " + e.getMessage());
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
     * Validate the JWT signature.
     */
    private void validateSignature(SignedJWT signedJWT, TokenExchangeExtensionGrantConfiguration configuration)
            throws InvalidGrantException {
        try {
            JWSAlgorithm algorithm = signedJWT.getHeader().getAlgorithm();
            String keyId = signedJWT.getHeader().getKeyID();

            JWSVerifier verifier = null;

            // Try to get keys from JWKS URL or provided public key
            if (TokenExchangeExtensionGrantConfiguration.KeyResolver.JWKS_URL.name()
                    .equals(configuration.getPublicKeyResolver())) {
                verifier = getVerifierFromJwks(configuration.getPublicKey(), keyId, algorithm);
            } else if (configuration.getPublicKey() != null && !configuration.getPublicKey().isEmpty()) {
                verifier = getVerifierFromKey(configuration.getPublicKey(), algorithm);
            }

            if (verifier != null) {
                if (!signedJWT.verify(verifier)) {
                    throw new InvalidGrantException("JWT signature verification failed");
                }
            } else {
                LOGGER.warn("No key configured for signature validation, skipping signature verification");
            }
        } catch (InvalidGrantException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error verifying JWT signature: {}", e.getMessage(), e);
            throw new InvalidGrantException("JWT signature verification error: " + e.getMessage());
        }
    }

    /**
     * Get a JWS verifier from a JWKS URL.
     */
    private JWSVerifier getVerifierFromJwks(String jwksUrl, String keyId, JWSAlgorithm algorithm)
            throws Exception {
        JWKSet jwkSet = JWKSet.load(new URL(jwksUrl));

        JWK jwk = null;
        if (keyId != null) {
            jwk = jwkSet.getKeyByKeyId(keyId);
        }
        if (jwk == null && !jwkSet.getKeys().isEmpty()) {
            // Fallback to first key if no keyId specified
            jwk = jwkSet.getKeys().get(0);
        }

        if (jwk == null) {
            throw new InvalidGrantException("Unable to find key for signature verification");
        }

        return createVerifier(jwk, algorithm);
    }

    /**
     * Get a JWS verifier from a provided public key.
     */
    private JWSVerifier getVerifierFromKey(String publicKey, JWSAlgorithm algorithm)
            throws Exception {
        // Parse the key based on algorithm family
        if (JWSAlgorithm.Family.RSA.contains(algorithm)) {
            // RSA key
            JWK jwk = JWK.parseFromPEMEncodedObjects(publicKey);
            return createVerifier(jwk, algorithm);
        } else if (JWSAlgorithm.Family.EC.contains(algorithm)) {
            // EC key
            JWK jwk = JWK.parseFromPEMEncodedObjects(publicKey);
            return createVerifier(jwk, algorithm);
        } else if (JWSAlgorithm.Family.HMAC_SHA.contains(algorithm)) {
            // HMAC key (symmetric)
            return new MACVerifier(publicKey.getBytes());
        }

        throw new InvalidGrantException("Unsupported algorithm: " + algorithm);
    }

    /**
     * Create a JWS verifier from a JWK.
     */
    private JWSVerifier createVerifier(JWK jwk, JWSAlgorithm algorithm) throws JOSEException {
        if (jwk instanceof RSAKey) {
            return new RSASSAVerifier((RSAKey) jwk);
        } else if (jwk instanceof ECKey) {
            return new ECDSAVerifier((ECKey) jwk);
        } else if (jwk instanceof OctetSequenceKey) {
            return new MACVerifier((OctetSequenceKey) jwk);
        }
        throw new JOSEException("Unsupported key type: " + jwk.getKeyType());
    }

    /**
     * Validate temporal claims (exp, nbf).
     */
    private void validateTemporalClaims(JWTClaimsSet claims) throws InvalidGrantException {
        Date now = new Date();

        // Check expiration
        Date expiration = claims.getExpirationTime();
        if (expiration != null && expiration.before(now)) {
            throw new InvalidGrantException("Token has expired");
        }

        // Check not before
        Date notBefore = claims.getNotBeforeTime();
        if (notBefore != null && notBefore.after(now)) {
            throw new InvalidGrantException("Token is not yet valid");
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
        return supportedTokenType;
    }

    @Override
    public boolean supports(String tokenType) {
        // JWT validator can handle JWT, ID_TOKEN, and ACCESS_TOKEN (when they are JWTs)
        return TokenTypeURN.JWT.equals(tokenType) ||
               TokenTypeURN.ID_TOKEN.equals(tokenType) ||
               supportedTokenType.equals(tokenType);
    }
}
