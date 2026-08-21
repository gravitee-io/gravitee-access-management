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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.model.ReferenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * External authority an AM domain trusts, and the key material used to verify what it vouches for.
 *
 * <p>A trusted domain declares how tokens are recognised as coming from it: {@code spiffeTrustDomain}
 * matches the trust domain of a JWT-SVID presented as a client assertion.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class TrustDomain {

    public static final int DEFAULT_REFRESH_INTERVAL_SECONDS = 300;

    public static final int NAME_MAX_LENGTH = 255;

    public static final int SPIFFE_TRUST_DOMAIN_MAX_LENGTH = 255;

    private String id;
    private String referenceId;
    private ReferenceType referenceType;

    /**
     * Label this trusted domain is known by in the Console and in audit entries. Unique within an AM
     * domain, and carries no matching semantics.
     */
    @Schema(description = "Label the trusted domain is known by. Unique within the security domain.",
            example = "acme-corp")
    private String name;

    private String description;

    /**
     * Trust domain as it appears in the SPIFFE IDs this authority issues, matched against the
     * {@code sub} of a JWT-SVID. Null when this authority is not trusted for SPIFFE.
     */
    @Schema(description = "SPIFFE trust domain matched against the \"sub\" of a JWT-SVID, without the "
            + "\"spiffe://\" scheme. Required to accept SPIFFE client assertions.",
            example = "acme.org")
    private String spiffeTrustDomain;

    private TrustDomainKeyMaterial keyMaterial;

    private int refreshIntervalSeconds = DEFAULT_REFRESH_INTERVAL_SECONDS;

    /**
     * Optional override of {@link SpiffeDomainSettings#getDefaultAllowedAlgorithms()}.
     */
    private List<String> allowedAlgorithms;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public TrustDomain(TrustDomain other) {
        this.id = other.id;
        this.referenceId = other.referenceId;
        this.referenceType = other.referenceType;
        this.name = other.name;
        this.description = other.description;
        this.spiffeTrustDomain = other.spiffeTrustDomain;
        this.keyMaterial = other.keyMaterial != null ? new TrustDomainKeyMaterial(other.keyMaterial) : null;
        this.refreshIntervalSeconds = other.refreshIntervalSeconds;
        this.allowedAlgorithms = other.allowedAlgorithms;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    /**
     * Whether this authority is trusted for SPIFFE client assertions.
     */
    public boolean trustsSpiffe() {
        return spiffeTrustDomain != null;
    }

    /**
     * @deprecated superseded by {@link #getKeyMaterial()}; PEM key material has no representation here.
     */
    @Deprecated
    @JsonProperty
    @Schema(deprecated = true, description = "Use keyMaterial.source instead. Null when the key material is a PEM certificate.")
    public SpiffeBundleSource getBundleSource() {
        if (keyMaterial == null || keyMaterial.getSource() == null) {
            return null;
        }
        return switch (keyMaterial.getSource()) {
            case JWKS_URL -> SpiffeBundleSource.JWKS_URL;
            case JWK_SET -> SpiffeBundleSource.STATIC_JWKS;
            case PEM -> null;
        };
    }

    /**
     * @deprecated superseded by {@link #getKeyMaterial()}.
     */
    @Deprecated
    @JsonProperty
    @Schema(deprecated = true, description = "Use keyMaterial.jwksUrl instead.")
    public String getJwksUrl() {
        return keyMaterial != null ? keyMaterial.getJwksUrl() : null;
    }

    /**
     * @deprecated superseded by {@link #getKeyMaterial()}.
     */
    @Deprecated
    @JsonProperty
    public JWKSet getStaticJwks() {
        return keyMaterial != null ? keyMaterial.getJwkSet() : null;
    }
}
