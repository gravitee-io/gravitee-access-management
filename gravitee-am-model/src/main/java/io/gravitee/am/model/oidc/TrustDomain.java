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
import io.gravitee.am.model.UserBindingCriterion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * External authority an AM domain trusts, and the key material used to verify what it vouches for.
 *
 * <p>A trusted domain declares how tokens are recognised as coming from it: {@code spiffeTrustDomain}
 * matches the trust domain of a JWT-SVID presented as a client assertion, {@code issuer} matches the
 * {@code iss} claim of an external token presented during an RFC 8693 exchange. Setting both lets one
 * authority serve both usages on shared key material.
 *
 * <p>{@code crossAppAccess} is the other direction: what AM issues towards this authority. A trusted
 * domain must declare at least one of the three, and one that only declares Cross App Access needs
 * neither an issuer nor key material.
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

    public static final int ISSUER_MAX_LENGTH = 512;

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

    /**
     * Value of the {@code iss} claim this authority vouches for, matched against external subject and
     * actor tokens. Null when this authority is not trusted for token exchange.
     */
    @Schema(description = "Expected value of the \"iss\" claim in an external JWT. Required to accept "
            + "tokens during an RFC 8693 exchange.",
            example = "https://sso.acme.com")
    private String issuer;

    private TrustDomainKeyMaterial keyMaterial;

    private int refreshIntervalSeconds = DEFAULT_REFRESH_INTERVAL_SECONDS;

    /**
     * Optional override of {@link SpiffeDomainSettings#getDefaultAllowedAlgorithms()}.
     */
    private List<String> allowedAlgorithms;

    @Schema(description = "One-to-one mapping from external scope to domain scope. Unmapped issuer scopes "
            + "are dropped (fail-closed). Applies to tokens matched by \"issuer\".")
    private Map<String, String> scopeMappings;

    @Schema(description = "Whether the external JWT subject is resolved to a single domain user using the "
            + "user binding criteria. When false, a virtual user is built from the token claims only.",
            defaultValue = "false")
    private boolean userBindingEnabled;

    @Schema(description = "Criteria used to locate a domain user when user binding is enabled. All criteria "
            + "are combined with AND.")
    private List<UserBindingCriterion> userBindingCriteria;

    @Schema(description = "What AM issues towards this authority. Absent means Cross App Access disabled.")
    private CrossAppAccessSettings crossAppAccess;

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
        this.issuer = other.issuer;
        this.keyMaterial = other.keyMaterial != null ? new TrustDomainKeyMaterial(other.keyMaterial) : null;
        this.refreshIntervalSeconds = other.refreshIntervalSeconds;
        this.allowedAlgorithms = other.allowedAlgorithms;
        this.scopeMappings = other.scopeMappings != null ? new LinkedHashMap<>(other.scopeMappings) : null;
        this.userBindingEnabled = other.userBindingEnabled;
        this.userBindingCriteria = other.userBindingCriteria != null ? new ArrayList<>(other.userBindingCriteria) : null;
        this.crossAppAccess = other.crossAppAccess != null ? new CrossAppAccessSettings(other.crossAppAccess) : null;
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
     * Whether this authority is trusted for RFC 8693 token exchange.
     */
    public boolean trustsTokenExchange() {
        return issuer != null;
    }

    /**
     * Whether AM may mint an ID-JAG towards this authority.
     */
    public boolean trustsCrossAppAccess() {
        return crossAppAccess != null && crossAppAccess.isEnabled();
    }

    /**
     * The resource servers of this authority, empty when there is no Cross App Access block.
     */
    public List<CrossAppAccessResourceServer> crossAppAccessResourceServers() {
        if (crossAppAccess == null || crossAppAccess.getResourceServers() == null) {
            return List.of();
        }
        return crossAppAccess.getResourceServers().stream().filter(Objects::nonNull).toList();
    }

    /**
     * The issuer of this authority's authorization server, null when there is no Cross App Access block.
     */
    public String crossAppAccessAudience() {
        return crossAppAccess == null ? null : crossAppAccess.getAudience();
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
