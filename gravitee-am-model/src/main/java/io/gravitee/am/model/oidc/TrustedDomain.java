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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserBindingCriterion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
public class TrustedDomain {

    public static final int NAME_MAX_LENGTH = 255;

    public static final int SPIFFE_TRUST_DOMAIN_MAX_LENGTH = 255;

    public static final int ISSUER_MAX_LENGTH = 512;

    public static final int DEFAULT_REFRESH_INTERVAL_SECONDS = 300;

    private String id;
    private String referenceId;
    private ReferenceType referenceType;

    @Schema(description = "Label the trusted domain is known by. Unique within the security domain.",
            example = "acme-corp")
    private String name;

    private String description;

    @Schema(description = "Issuer identifier of this authority's authorization server. Matched against "
            + "the \"iss\" of an external JWT during an RFC 8693 exchange, and carried by the \"aud\" "
            + "claim of an ID-JAG minted towards it. Unique within the security domain.",
            example = "https://sso.acme.com", maxLength = ISSUER_MAX_LENGTH)
    private String domainIdentifier;

    private TrustDomainKeyMaterial keyMaterial;

    @Schema(description = "Accepts JWT-SVIDs from this authority as client assertions. Absent when this "
            + "authority is not trusted for SPIFFE.")
    private SpiffeTrustSettings spiffe;

    @Schema(description = "Accepts this authority's JWTs as subject or actor tokens during an RFC 8693 "
            + "exchange. Absent when this authority is not trusted for token exchange.")
    private TokenExchangeTrustSettings tokenExchange;

    @Schema(description = "What AM issues towards this authority. Absent means Cross App Access disabled.")
    private CrossAppAccessSettings crossAppAccess;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    @Builder
    public TrustedDomain(String id,
                         String referenceId,
                         ReferenceType referenceType,
                         String name,
                         String description,
                         String domainIdentifier,
                         TrustDomainKeyMaterial keyMaterial,
                         SpiffeTrustSettings spiffe,
                         TokenExchangeTrustSettings tokenExchange,
                         CrossAppAccessSettings crossAppAccess,
                         Date createdAt,
                         Date updatedAt) {
        this.id = id;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.name = name;
        this.description = description;
        this.domainIdentifier = domainIdentifier;
        this.keyMaterial = keyMaterial;
        this.spiffe = spiffe;
        this.tokenExchange = tokenExchange;
        this.crossAppAccess = crossAppAccess;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public TrustedDomain(TrustedDomain other) {
        this.id = other.id;
        this.referenceId = other.referenceId;
        this.referenceType = other.referenceType;
        this.name = other.name;
        this.description = other.description;
        this.domainIdentifier = other.domainIdentifier;
        this.keyMaterial = other.keyMaterial != null ? new TrustDomainKeyMaterial(other.keyMaterial) : null;
        this.spiffe = other.spiffe != null ? new SpiffeTrustSettings(other.spiffe) : null;
        this.tokenExchange = other.tokenExchange != null ? new TokenExchangeTrustSettings(other.tokenExchange) : null;
        this.crossAppAccess = other.crossAppAccess != null ? new CrossAppAccessSettings(other.crossAppAccess) : null;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public boolean trustsSpiffe() {
        return spiffe != null && spiffe.getSpiffeTrustDomain() != null;
    }

    public boolean trustsTokenExchange() {
        return tokenExchange != null && tokenExchange.isEnabled() && domainIdentifier != null;
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

    @JsonIgnore
    @Schema(hidden = true)
    public String getSpiffeTrustDomain() {
        return spiffe != null ? spiffe.getSpiffeTrustDomain() : null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    public List<String> getAllowedAlgorithms() {
        return spiffe != null ? spiffe.getAllowedAlgorithms() : null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    public Map<String, String> getScopeMappings() {
        return tokenExchange != null ? tokenExchange.getScopeMappings() : null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean isUserBindingEnabled() {
        return tokenExchange != null && tokenExchange.isUserBindingEnabled();
    }

    @JsonIgnore
    @Schema(hidden = true)
    public List<UserBindingCriterion> getUserBindingCriteria() {
        return tokenExchange != null ? tokenExchange.getUserBindingCriteria() : null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    public int getRefreshIntervalSeconds() {
        if (keyMaterial == null || keyMaterial.getRefreshIntervalSeconds() == null) {
            return DEFAULT_REFRESH_INTERVAL_SECONDS;
        }
        return keyMaterial.getRefreshIntervalSeconds();
    }
}
