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
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadata;
import io.gravitee.am.gateway.handler.aauth.service.AgentMetadataFetcher;
import io.gravitee.am.gateway.handler.aauth.service.JWKSDocument;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureKeyInfo;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureVerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.security.PublicKey;

/**
 * JWKS URI scheme: resolves a public key by fetching the agent's metadata document
 * and JWKS, then looking up the key by {@code kid}.
 * <p>
 * Signature-Key header parameters:
 * <ul>
 *   <li><code>id</code> — agent server URL (the verified identity)</li>
 *   <li><code>kid</code> — key identifier to look up in the JWKS</li>
 *   <li><code>well-known</code> — metadata document name (e.g. "aauth-agent", without .json)</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class JWKSUriScheme implements SignatureScheme {

    private final AgentMetadataFetcher fetcher;

    @Override
    public ResolvedKey resolve(SignatureKeyInfo keyInfo) throws SignatureVerificationException {
        String agentId = keyInfo.getParam("id");    // Agent server URL
        String kid = keyInfo.getParam("kid");        // Key ID in the JWKS

        if (agentId == null || kid == null) {
            throw new SignatureVerificationException("invalid_key");
        }

        try {
            // 1. Fetch agent metadata
            AgentMetadata metadata = fetcher.fetchMetadata(agentId);
            if (metadata.jwksUri() == null) {
                throw new SignatureVerificationException("invalid_key");
            }

            // 2. Fetch JWKS and find key by kid
            JWKSDocument jwks = fetcher.fetchJWKS(metadata.jwksUri());
            JWK jwk = jwks.findByKid(kid);

            // 3. If not found, re-fetch once per spec requirement
            if (jwk == null) {
                log.debug("Key kid={} not found in JWKS, re-fetching from {}", kid, metadata.jwksUri());
                jwks = fetcher.fetchJWKS(metadata.jwksUri(), true);
                jwk = jwks.findByKid(kid);
            }

            if (jwk == null) {
                throw new SignatureVerificationException("unknown_key");
            }

            // 4. Convert JWK to PublicKey using Java native crypto
            return convertJwkToResolvedKey(jwk, agentId);
        } catch (SignatureVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to resolve JWKS URI key (id={}, kid={}): {}", agentId, kid, e.getMessage());
            throw new SignatureVerificationException("invalid_key");
        }
    }

    private ResolvedKey convertJwkToResolvedKey(JWK jwk, String agentServerUrl) throws Exception {
        PublicKey publicKey = JwkKeyConverter.toNativePublicKey(jwk);
        String algorithm = JwkKeyConverter.algorithmForJwk(jwk);
        String thumbprint = JwkKeyConverter.computeThumbprint(jwk);
        return new ResolvedKey(publicKey, algorithm, thumbprint, agentServerUrl, null);
    }
}
