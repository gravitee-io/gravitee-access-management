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
package io.gravitee.am.gateway.handler.aauth.service.token;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadataFetcher;
import io.gravitee.am.gateway.handler.aauth.service.JWKSDocument;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Validates {@code aa-resource+jwt} tokens per AAUTH spec Section 6.6.2.
 * <p>
 * Verification steps:
 * <ol>
 *   <li>Verify {@code typ} is {@code aa-resource+jwt}</li>
 *   <li>Verify {@code dwk} is {@code aauth-resource.json}. Fetch JWKS via {@code {iss}/.well-known/{dwk}}. Verify signature.</li>
 *   <li>Verify {@code exp} in future, {@code iat} not in future</li>
 *   <li>Verify {@code aud} matches PS own identifier</li>
 *   <li>Verify {@code agent} matches requesting agent's identifier</li>
 *   <li>Verify {@code agent_jkt} matches JWK Thumbprint of request signing key</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public class ResourceTokenValidator {

    private final AgentMetadataFetcher metadataFetcher;

    /**
     * Validate a resource token and return its parsed claims.
     *
     * @param resourceTokenJwt the compact JWT string
     * @param agentVerification the agent's HTTP signature verification result
     * @param psIssuerUrl this PS's own issuer URL for {@code aud} verification
     * @return parsed and verified claims
     * @throws ResourceTokenException if validation fails
     */
    public ResourceTokenClaims validate(String resourceTokenJwt, VerificationResult agentVerification,
                                         String psIssuerUrl)
            throws ResourceTokenException {
        try {
            SignedJWT signedJWT = SignedJWT.parse(resourceTokenJwt);
            JWSHeader header = signedJWT.getHeader();
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Step 1: Verify typ
            String typ = header.getType() != null ? header.getType().toString() : null;
            if (!"aa-resource+jwt".equals(typ)) {
                throw new ResourceTokenException("invalid_resource_token", "Invalid typ: expected aa-resource+jwt");
            }

            // Step 2: Verify dwk and signature
            String dwk = claims.getStringClaim("dwk");
            if (!"aauth-resource.json".equals(dwk)) {
                throw new ResourceTokenException("invalid_resource_token", "Invalid dwk: expected aauth-resource.json");
            }

            String iss = claims.getIssuer();
            if (iss == null) {
                throw new ResourceTokenException("invalid_resource_token", "Missing iss claim");
            }

            // Fetch resource JWKS and verify signature
            verifySignature(signedJWT, iss, header.getKeyID());

            // Step 3: Verify timestamps
            long now = Instant.now().getEpochSecond();
            long exp = claims.getExpirationTime() != null ? claims.getExpirationTime().getTime() / 1000 : 0;
            long iat = claims.getIssueTime() != null ? claims.getIssueTime().getTime() / 1000 : 0;

            if (exp <= now) {
                throw new ResourceTokenException("expired_resource_token", "Resource token has expired");
            }
            if (iat > now) {
                throw new ResourceTokenException("invalid_resource_token", "iat is in the future");
            }

            // Step 4: Verify aud matches this PS
            // Nimbus always stores aud as a list, so use getAudience()
            var audList = claims.getAudience();
            String aud = (audList != null && !audList.isEmpty()) ? audList.getFirst() : null;
            if (!psIssuerUrl.equals(aud)) {
                throw new ResourceTokenException("invalid_resource_token",
                        "aud does not match this PS: expected " + psIssuerUrl + ", got " + aud);
            }

            // Step 5: Verify agent matches the request signer
            String agent = claims.getStringClaim("agent");
            if (agentVerification.agentIdentityUrl() != null && !agentVerification.agentIdentityUrl().equals(agent)) {
                throw new ResourceTokenException("invalid_resource_token",
                        "agent claim does not match request signer");
            }

            // Step 6: Verify agent_jkt matches the signer's thumbprint
            String agentJkt = claims.getStringClaim("agent_jkt");
            if (agentJkt != null && !agentJkt.equals(agentVerification.jwkThumbprint())) {
                throw new ResourceTokenException("invalid_resource_token",
                        "agent_jkt does not match request signer's key thumbprint");
            }

            String scope = claims.getStringClaim("scope");
            String jti = claims.getJWTID();

            return new ResourceTokenClaims(iss, aud, jti, agent, agentJkt, scope, iat, exp);

        } catch (ResourceTokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate resource token: {}", e.getMessage());
            throw new ResourceTokenException("invalid_resource_token", e.getMessage());
        }
    }

    private void verifySignature(SignedJWT signedJWT, String issuer, String kid) throws Exception {
        // Fetch resource server metadata to get JWKS URI.
        // Reuses AgentMetadata record — works because both have issuer and jwks_uri fields.
        String metadataUrl = issuer + "/.well-known/aauth-resource.json";
        var metadata = metadataFetcher.fetchMetadataByUrl(metadataUrl);

        String jwksUri = metadata.jwksUri();
        if (jwksUri == null) {
            throw new ResourceTokenException("invalid_resource_token", "Resource metadata has no jwks_uri");
        }

        JWKSDocument jwks = metadataFetcher.fetchJWKS(jwksUri);
        JWK jwk = jwks.findByKid(kid);
        if (jwk == null) {
            // Re-fetch once per spec
            jwks = metadataFetcher.fetchJWKS(jwksUri, true);
            jwk = jwks.findByKid(kid);
        }
        if (jwk == null) {
            throw new ResourceTokenException("invalid_resource_token", "Resource signing key not found: kid=" + kid);
        }

        // Verify using Java native crypto (Nimbus toPublicKey() fails on Java 25)
        PublicKey publicKey = toNativePublicKey(jwk);

        // JWT signature = sign(base64url(header) + "." + base64url(payload))
        String[] parts = signedJWT.serialize().split("\\.");
        byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] signatureBytes = signedJWT.getSignature().decode();

        Signature sig = Signature.getInstance("Ed25519");
        sig.initVerify(publicKey);
        sig.update(signingInput);
        if (!sig.verify(signatureBytes)) {
            throw new ResourceTokenException("invalid_resource_token", "Resource token signature verification failed");
        }
    }

    /**
     * Convert a Nimbus JWK to a Java native PublicKey.
     * Uses the raw key bytes from the OKP (Octet Key Pair) representation.
     */
    private PublicKey toNativePublicKey(JWK jwk) throws Exception {
        if (!(jwk instanceof OctetKeyPair okp)) {
            throw new ResourceTokenException("invalid_resource_token",
                    "Unsupported resource signing key type: " + jwk.getKeyType());
        }

        // Decode the x-coordinate (raw 32-byte Ed25519 public key, RFC 8032 little-endian)
        byte[] raw = okp.getX().decode();

        // Extract the x-sign bit from the high bit of the last byte
        boolean xOdd = (raw[31] & 0x80) != 0;
        raw[31] &= 0x7F; // clear the sign bit

        // Convert little-endian to big-endian BigInteger
        byte[] bigEndian = new byte[raw.length];
        for (int i = 0; i < raw.length; i++) {
            bigEndian[i] = raw[raw.length - 1 - i];
        }

        EdECPoint point = new EdECPoint(xOdd, new java.math.BigInteger(1, bigEndian));
        EdECPublicKeySpec spec = new EdECPublicKeySpec(NamedParameterSpec.ED25519, point);
        return KeyFactory.getInstance("Ed25519").generatePublic(spec);
    }
}
