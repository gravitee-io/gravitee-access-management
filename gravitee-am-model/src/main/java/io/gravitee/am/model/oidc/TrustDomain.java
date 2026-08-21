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

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * External authority an AM domain trusts, either as a SPIFFE trust domain whose JWT-SVIDs are
 * accepted as client assertions, or as a token-exchange issuer whose tokens are accepted during an
 * RFC 8693 exchange. Owns the key material used to verify what it vouches for.
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

    private String id;
    private String referenceId;
    private ReferenceType referenceType;

    /**
     * Which kind of trust this domain represents. Trusted domains stored before the token-exchange
     * kind existed read back as {@link TrustDomainKind#SPIFFE}.
     */
    @Builder.Default
    private TrustDomainKind kind = TrustDomainKind.SPIFFE;

    /**
     * Name as it appears in SPIFFE IDs. Unique per kind within an AM domain.
     */
    private String name;

    private String description;

    private TrustDomainKeyMaterial keyMaterial;

    private int refreshIntervalSeconds = DEFAULT_REFRESH_INTERVAL_SECONDS;

    /**
     * Optional override of {@link SpiffeDomainSettings#getDefaultAllowedAlgorithms()}.
     */
    private List<String> allowedAlgorithms;

    private TrustDomainTokenExchangeSettings tokenExchange;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    public TrustDomain(TrustDomain other) {
        this.id = other.id;
        this.referenceId = other.referenceId;
        this.referenceType = other.referenceType;
        this.kind = other.kind;
        this.name = other.name;
        this.description = other.description;
        this.keyMaterial = other.keyMaterial != null ? new TrustDomainKeyMaterial(other.keyMaterial) : null;
        this.refreshIntervalSeconds = other.refreshIntervalSeconds;
        this.allowedAlgorithms = other.allowedAlgorithms;
        this.tokenExchange = other.tokenExchange != null ? new TrustDomainTokenExchangeSettings(other.tokenExchange) : null;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    /**
     * The {@code iss} claim this trusted domain vouches for, or null when it is not of the
     * token-exchange kind.
     */
    @JsonIgnore
    public String issuer() {
        return tokenExchange != null ? tokenExchange.getIssuer() : null;
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
