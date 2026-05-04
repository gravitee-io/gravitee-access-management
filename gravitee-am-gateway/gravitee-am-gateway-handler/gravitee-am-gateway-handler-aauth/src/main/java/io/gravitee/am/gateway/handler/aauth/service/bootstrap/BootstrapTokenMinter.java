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
package io.gravitee.am.gateway.handler.aauth.service.bootstrap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Mints bootstrap_token JWTs ({@code typ=aa-bootstrap+jwt}) per draft-hardt-aauth-bootstrap §6.4.
 *
 * @author GraviteeSource Team
 */
@Slf4j
public class BootstrapTokenMinter {

    private static final String BOOTSTRAP_TOKEN_TYP = "aa-bootstrap+jwt";
    /** Spec §6.4: bootstrap_token "SHOULD NOT exceed 5 minutes after iat". Hard cap. */
    public static final int MAX_BOOTSTRAP_TOKEN_LIFESPAN_SECONDS = 300;
    /** Practical floor — too short and slow agent polling races mint→delivery. */
    public static final int MIN_BOOTSTRAP_TOKEN_LIFESPAN_SECONDS = 30;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CertificateManager certificateManager;
    private final int lifespanSeconds;

    /**
     * @param certificateManager domain-scoped certificate manager
     * @param lifespanSeconds    bootstrap_token lifespan in seconds. Clamped to
     *                           [{@value #MIN_BOOTSTRAP_TOKEN_LIFESPAN_SECONDS},
     *                           {@value #MAX_BOOTSTRAP_TOKEN_LIFESPAN_SECONDS}].
     */
    public BootstrapTokenMinter(CertificateManager certificateManager, int lifespanSeconds) {
        this.certificateManager = certificateManager;
        this.lifespanSeconds = clamp(lifespanSeconds);
    }

    private static int clamp(int requested) {
        if (requested > MAX_BOOTSTRAP_TOKEN_LIFESPAN_SECONDS) {
            log.warn("Configured bootstrap_token lifespan {}s exceeds spec maximum {}s — capping",
                    requested, MAX_BOOTSTRAP_TOKEN_LIFESPAN_SECONDS);
            return MAX_BOOTSTRAP_TOKEN_LIFESPAN_SECONDS;
        }
        if (requested < MIN_BOOTSTRAP_TOKEN_LIFESPAN_SECONDS) {
            log.warn("Configured bootstrap_token lifespan {}s is below practical minimum {}s — raising",
                    requested, MIN_BOOTSTRAP_TOKEN_LIFESPAN_SECONDS);
            return MIN_BOOTSTRAP_TOKEN_LIFESPAN_SECONDS;
        }
        return requested;
    }

    /**
     * Mint a bootstrap token JWT.
     *
     * @param psIssuerUrl      PS issuer URL (becomes {@code iss})
     * @param agentServerUrl   Agent Server URL (becomes {@code aud})
     * @param pairwiseSub      Pairwise user identifier (becomes {@code sub})
     * @param ephemeralKeyJwk  Agent's ephemeral public key as JSON string (goes in {@code cnf.jwk})
     * @return the signed JWT string, or an error {@link Single} if the JWK is malformed or no
     *         asymmetric signing certificate is available
     */
    public Single<String> mint(String psIssuerUrl, String agentServerUrl, String pairwiseSub, String ephemeralKeyJwk) {
        long now = Instant.now().getEpochSecond();
        long exp = now + lifespanSeconds;

        JWT jwt = new JWT();
        jwt.setIss(psIssuerUrl);
        jwt.put("dwk", "aauth-person.json");
        jwt.setAud(agentServerUrl);
        jwt.setSub(pairwiseSub);
        jwt.setJti(UUID.randomUUID().toString());
        jwt.setIat(now);
        jwt.put("exp", exp);

        try {
            Map<String, Object> jwkMap = OBJECT_MAPPER.readValue(ephemeralKeyJwk,
                    new TypeReference<Map<String, Object>>() {});
            jwt.put("cnf", Map.of("jwk", jwkMap));
        } catch (Exception e) {
            return Single.error(new IllegalArgumentException("Invalid ephemeral key JWK: " + e.getMessage(), e));
        }

        // The bootstrap_token must be verifiable by the Agent Server via the PS JWKS,
        // which only carries asymmetric keys. Falling back to a symmetric / HMAC key would
        // produce a token nobody can verify and silently break bootstrap at the AS step.
        // Fail-fast here with a typed exception so the operator gets a clear log line.
        return certificateManager.findByAlgorithm("RS256")
                .switchIfEmpty(certificateManager.findByAlgorithm("ES256"))
                .switchIfEmpty(Maybe.error(() -> new BootstrapTokenSigningException(
                        "No asymmetric (RS256/ES256) signing certificate is configured for this domain. "
                                + "Bootstrap tokens must be verifiable via the PS JWKS — symmetric keys "
                                + "cannot be published there. Configure at least one RSA or EC certificate "
                                + "in the management console (Domain → Settings → Certificates).")))
                .toSingle()
                .map(certProvider -> certProvider.getJwtBuilder().sign(jwt, BOOTSTRAP_TOKEN_TYP));
    }
}
