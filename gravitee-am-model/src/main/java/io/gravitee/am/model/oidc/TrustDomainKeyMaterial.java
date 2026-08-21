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
package io.gravitee.am.model.oidc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Where a {@link TrustDomain} gets the signing keys it validates tokens against. The same shape is
 * used by every {@link TrustDomainKind}; exactly one of the three sources applies, selected by
 * {@link #getSource()}.
 *
 * @author GraviteeSource Team
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "Trusted domain key material",
        description = "Where the trusted domain's signing keys come from. Exactly one source applies.")
public class TrustDomainKeyMaterial {

    @Schema(description = "Which of the three key sources this trusted domain uses.")
    private KeyMaterialSource source;

    @Schema(description = "JWKS endpoint URL. Required when source is JWKS_URL.",
            example = "https://issuer.example.com/.well-known/jwks.json")
    private String jwksUrl;

    @Schema(description = "Inline JWK set. Required when source is JWK_SET.")
    private JWKSet jwkSet;

    @Schema(description = "PEM-encoded X.509 certificate. Required when source is PEM.")
    private String certificate;

    /**
     * Reads the SPIFFE-only bundle source that preceded this shape. Returns null when there is no
     * bundle source to read.
     */
    public static TrustDomainKeyMaterial fromBundleSource(SpiffeBundleSource bundleSource, String jwksUrl) {
        if (bundleSource == null) {
            return null;
        }
        KeyMaterialSource source = bundleSource == SpiffeBundleSource.STATIC_JWKS
                ? KeyMaterialSource.JWK_SET
                : KeyMaterialSource.JWKS_URL;
        return TrustDomainKeyMaterial.builder().source(source).jwksUrl(jwksUrl).build();
    }

    public TrustDomainKeyMaterial(TrustDomainKeyMaterial other) {
        this.source = other.source;
        this.jwksUrl = other.jwksUrl;
        this.jwkSet = cloneJwkSet(other.jwkSet);
        this.certificate = other.certificate;
    }

    private static JWKSet cloneJwkSet(JWKSet source) {
        if (source == null) {
            return null;
        }
        try {
            return source.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("JWKSet clone failed", e);
        }
    }
}
