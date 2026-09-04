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

import io.gravitee.am.model.ReferenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class TrustDomain extends TrustDomainDeprecated {

    public static final int NAME_MAX_LENGTH = 255;

    private String id;
    private String referenceId;
    private ReferenceType referenceType;

    @Schema(description = "Label the trusted domain is known by. Unique within the security domain.",
            example = "acme-corp")
    private String name;

    private String description;

    @Schema(description = "Issuer identifier of this authority's authorization server. Matched against "
            + "the \"iss\" of an external JWT during an RFC 8693 exchange.",
            example = "https://sso.acme.com")
    private String domainIdentifier;

    private TrustDomainKeyMaterial keyMaterial;

    @Schema(description = "Accepts JWT-SVIDs from this authority as client assertions. Absent when this "
            + "authority is not trusted for SPIFFE.")
    private SpiffeTrustSettings spiffe;

    @Schema(description = "Accepts this authority's JWTs as subject or actor tokens during an RFC 8693 "
            + "exchange. Absent when this authority is not trusted for token exchange.")
    private TokenExchangeTrustSettings tokenExchange;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    @Builder
    public TrustDomain(String id,
                       String referenceId,
                       ReferenceType referenceType,
                       String name,
                       String description,
                       String domainIdentifier,
                       TrustDomainKeyMaterial keyMaterial,
                       SpiffeTrustSettings spiffe,
                       TokenExchangeTrustSettings tokenExchange,
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
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public TrustDomain(TrustDomain other) {
        this.id = other.id;
        this.referenceId = other.referenceId;
        this.referenceType = other.referenceType;
        this.name = other.name;
        this.description = other.description;
        this.domainIdentifier = other.domainIdentifier;
        this.keyMaterial = other.keyMaterial != null ? new TrustDomainKeyMaterial(other.keyMaterial) : null;
        this.spiffe = other.spiffe != null ? new SpiffeTrustSettings(other.spiffe) : null;
        this.tokenExchange = other.tokenExchange != null ? new TokenExchangeTrustSettings(other.tokenExchange) : null;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public boolean trustsSpiffe() {
        return spiffe != null && spiffe.getSpiffeTrustDomain() != null;
    }

    public boolean trustsTokenExchange() {
        return tokenExchange != null && domainIdentifier != null;
    }
}
