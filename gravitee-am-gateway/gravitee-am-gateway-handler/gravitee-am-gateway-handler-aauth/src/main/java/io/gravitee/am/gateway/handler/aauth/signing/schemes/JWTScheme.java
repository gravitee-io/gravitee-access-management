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
package io.gravitee.am.gateway.handler.aauth.signing.schemes;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadataFetcher;
import io.gravitee.am.gateway.handler.aauth.service.JWKSDocument;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureKeyInfo;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureVerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * JWT signature scheme: the agent presents a JWT ({@code aa-agent+jwt} or {@code aa-auth+jwt})
 * in the Signature-Key header. The server verifies the JWT against the issuer's JWKS, then
 * extracts {@code cnf.jwk} as the public key for HTTP message signature verification.
 * <p>
 * Per AAUTH spec Sections 9 (Agent Tokens), 11 (Auth Tokens), and 15 (Request Verification).
 *
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class JWTScheme implements SignatureScheme {

    private static final String TYP_AGENT_TOKEN = "aa-agent+jwt";
    private static final String TYP_AUTH_TOKEN = "aa-auth+jwt";
    private static final Set<String> SUPPORTED_TYPES = Set.of(TYP_AGENT_TOKEN, TYP_AUTH_TOKEN);

    private static final String DWK_AGENT = "aauth-agent.json";
    private static final String DWK_PERSON = "aauth-person.json";

    private static final long MAX_AGENT_TOKEN_LIFETIME_SECONDS = 86400; // 24 hours

    private final AgentMetadataFetcher fetcher;

    @Override
    public ResolvedKey resolve(SignatureKeyInfo keyInfo) throws SignatureVerificationException {
        String jwtString = keyInfo.getParam("jwt");
        if (jwtString == null || jwtString.isBlank()) {
            throw new SignatureVerificationException("invalid_jwt");
        }

        try {
            // 1. Parse the JWT
            SignedJWT signedJWT = SignedJWT.parse(jwtString);
            String typ = signedJWT.getHeader().getType() != null
                    ? signedJWT.getHeader().getType().getType() : null;
            String kid = signedJWT.getHeader().getKeyID();

            // 2. Validate typ
            if (typ == null || !SUPPORTED_TYPES.contains(typ)) {
                throw new SignatureVerificationException("invalid_jwt");
            }

            // 3. Extract claims
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            String iss = claims.getIssuer();
            String dwk = (String) claims.getClaim("dwk");

            if (iss == null || iss.isBlank()) {
                throw new SignatureVerificationException("invalid_jwt", "Missing iss claim");
            }

            // 4. Validate dwk
            String expectedDwk = TYP_AGENT_TOKEN.equals(typ) ? DWK_AGENT : DWK_PERSON;
            if (!expectedDwk.equals(dwk)) {
                throw new SignatureVerificationException("invalid_jwt",
                        "Invalid dwk claim: expected " + expectedDwk + ", got " + dwk);
            }

            // 5. Validate timestamps
            validateTimestamps(claims, typ);

            // 6. Verify JWT signature against issuer's JWKS
            verifyJwtSignature(signedJWT, iss, dwk, kid);

            // 7. Extract cnf.jwk
            Map<String, Object> cnf = getRequiredMapClaim(claims, "cnf");
            Map<String, Object> cnfJwkMap = getRequiredMapClaim("cnf.jwk", cnf, "jwk");

            // 8. Convert cnf.jwk to PublicKey
            JWK cnfJwk = JWK.parse(cnfJwkMap);
            PublicKey cnfPublicKey = JwkKeyConverter.toNativePublicKey(cnfJwk);
            String algorithm = JwkKeyConverter.algorithmForJwk(cnfJwk);
            String thumbprint = JwkKeyConverter.computeThumbprint(cnfJwk);

            // 9. Determine agent server URL, agent identifier, and (for agent tokens) the ps claim
            // For aa-agent+jwt: agentServerUrl = iss, agentIdentifier = sub (aauth:local@domain), agentTokenPs = ps claim
            // For aa-auth+jwt:  agentServerUrl = iss, agentIdentifier = agent claim, agentTokenPs = null (ps is not on auth tokens)
            String agentServerUrl = iss;
            String agentIdentifier;
            String agentTokenPs = null;
            if (TYP_AGENT_TOKEN.equals(typ)) {
                agentIdentifier = claims.getSubject();
                if (agentIdentifier == null) {
                    throw new SignatureVerificationException("invalid_jwt", "Missing sub claim in agent token");
                }
                if (!io.gravitee.am.gateway.handler.aauth.util.AAuthIdentifierValidator.isValidAgentIdentifier(agentIdentifier)) {
                    throw new SignatureVerificationException("invalid_jwt",
                            "Invalid agent identifier format: expected aauth:local@domain, got " + agentIdentifier);
                }
                Object psClaim = claims.getClaim("ps");
                if (psClaim != null && !(psClaim instanceof String)) {
                    throw new SignatureVerificationException("invalid_jwt",
                            "ps claim on agent token must be a string");
                }
                agentTokenPs = (String) psClaim;
            } else {
                agentIdentifier = (String) claims.getClaim("agent");
                if (agentIdentifier == null) {
                    throw new SignatureVerificationException("invalid_jwt", "Missing agent claim in auth token");
                }
            }

            return new ResolvedKey(cnfPublicKey, algorithm, thumbprint, agentServerUrl, agentIdentifier, agentTokenPs);

        } catch (SignatureVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to resolve JWT scheme: {}", e.getMessage());
            throw new SignatureVerificationException("invalid_jwt", e.getMessage());
        }
    }

    private void validateTimestamps(JWTClaimsSet claims, String typ) throws SignatureVerificationException {
        long now = Instant.now().getEpochSecond();

        if (claims.getExpirationTime() == null) {
            throw new SignatureVerificationException("invalid_jwt", "Missing exp claim");
        }
        long exp = claims.getExpirationTime().toInstant().getEpochSecond();
        if (exp <= now) {
            throw new SignatureVerificationException("expired_jwt", "JWT has expired");
        }

        if (claims.getIssueTime() == null) {
            throw new SignatureVerificationException("invalid_jwt", "Missing iat claim");
        }
        long iat = claims.getIssueTime().toInstant().getEpochSecond();

        // Agent tokens SHOULD NOT exceed 24 hours
        if (TYP_AGENT_TOKEN.equals(typ) && (exp - iat) > MAX_AGENT_TOKEN_LIFETIME_SECONDS) {
            throw new SignatureVerificationException("invalid_jwt",
                    "Agent token lifetime exceeds 24 hours");
        }
    }

    private void verifyJwtSignature(SignedJWT signedJWT, String iss, String dwk, String kid)
            throws Exception {
        // Fetch issuer metadata via dwk
        String metadataUrl = iss + "/.well-known/" + dwk;
        var metadata = fetcher.fetchMetadataByUrl(metadataUrl);
        if (metadata.jwksUri() == null) {
            throw new SignatureVerificationException("invalid_jwt",
                    "Issuer metadata has no jwks_uri: " + metadataUrl);
        }

        // Fetch JWKS and find key by kid
        JWKSDocument jwks = fetcher.fetchJWKS(metadata.jwksUri());
        JWK jwk = kid != null ? jwks.findByKid(kid) : null;
        if (jwk == null && kid != null) {
            // Re-fetch once per spec
            jwks = fetcher.fetchJWKS(metadata.jwksUri(), true);
            jwk = jwks.findByKid(kid);
        }
        if (jwk == null) {
            throw new SignatureVerificationException("invalid_jwt",
                    "Issuer signing key not found" + (kid != null ? ": kid=" + kid : ""));
        }

        // Verify JWT signature using native crypto
        PublicKey issuerKey = JwkKeyConverter.toNativePublicKey(jwk);
        String algorithm = JwkKeyConverter.algorithmForJwk(jwk);

        String[] parts = signedJWT.serialize().split("\\.");
        byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
        byte[] signatureBytes = signedJWT.getSignature().decode();

        Signature sig = Signature.getInstance(algorithm);
        sig.initVerify(issuerKey);
        sig.update(signingInput);
        if (!sig.verify(signatureBytes)) {
            throw new SignatureVerificationException("invalid_jwt", "JWT signature verification failed");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getRequiredMapClaim(JWTClaimsSet claims, String name)
            throws SignatureVerificationException {
        Object value = claims.getClaim(name);
        if (!(value instanceof Map)) {
            throw new SignatureVerificationException("invalid_jwt", "Missing or invalid " + name + " claim");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getRequiredMapClaim(String parentPath, Map<String, Object> parent, String name)
            throws SignatureVerificationException {
        Object value = parent.get(name);
        if (!(value instanceof Map)) {
            throw new SignatureVerificationException("invalid_jwt",
                    "Missing or invalid " + parentPath + "." + name + " claim");
        }
        return (Map<String, Object>) value;
    }
}
