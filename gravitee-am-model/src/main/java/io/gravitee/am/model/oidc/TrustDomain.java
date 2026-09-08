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
import io.gravitee.am.model.UserBindingCriterion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Deprecated
@Getter
@Setter
@Schema(deprecated = true, title = "Trust domain",
        description = "Deprecated: use the /trusted-domains endpoint instead.")
public class TrustDomain {

    private String id;
    private String referenceId;
    private ReferenceType referenceType;
    private String name;
    private String description;
    private String domainIdentifier;
    private TrustDomainKeyMaterial keyMaterial;
    private SpiffeTrustSettings spiffe;
    private TokenExchangeTrustSettings tokenExchange;

    @Schema(type = "java.lang.Long")
    private Date createdAt;

    @Schema(type = "java.lang.Long")
    private Date updatedAt;

    @Schema(deprecated = true, description = "Use spiffe.spiffeTrustDomain instead.")
    private String spiffeTrustDomain;

    @Schema(deprecated = true, description = "Use domainIdentifier instead.")
    private String issuer;

    @Schema(deprecated = true, description = "Use keyMaterial.refreshIntervalSeconds instead.")
    private int refreshIntervalSeconds;

    @Schema(deprecated = true, description = "Use spiffe.allowedAlgorithms instead.")
    private List<String> allowedAlgorithms;

    @Schema(deprecated = true, description = "Use tokenExchange.scopeMappings instead.")
    private Map<String, String> scopeMappings;

    @Schema(deprecated = true, description = "Use tokenExchange.userBindingEnabled instead.")
    private boolean userBindingEnabled;

    @Schema(deprecated = true, description = "Use tokenExchange.userBindingCriteria instead.")
    private List<UserBindingCriterion> userBindingCriteria;

    @Schema(deprecated = true, description = "Use keyMaterial.source instead. Null when the key material is a PEM certificate.")
    private SpiffeBundleSource bundleSource;

    @Schema(deprecated = true, description = "Use keyMaterial.jwksUrl instead.")
    private String jwksUrl;

    @Schema(deprecated = true, description = "Use keyMaterial.jwkSet instead.")
    private JWKSet staticJwks;

    public static TrustDomain from(TrustedDomain trustedDomain) {
        if (trustedDomain == null) {
            return null;
        }
        TrustDomain td = new TrustDomain();
        td.setId(trustedDomain.getId());
        td.setReferenceId(trustedDomain.getReferenceId());
        td.setReferenceType(trustedDomain.getReferenceType());
        td.setName(trustedDomain.getName());
        td.setDescription(trustedDomain.getDescription());
        td.setDomainIdentifier(trustedDomain.getDomainIdentifier());
        td.setKeyMaterial(trustedDomain.getKeyMaterial());
        td.setSpiffe(trustedDomain.getSpiffe());
        td.setTokenExchange(trustedDomain.getTokenExchange());
        td.setCreatedAt(trustedDomain.getCreatedAt());
        td.setUpdatedAt(trustedDomain.getUpdatedAt());

        td.setSpiffeTrustDomain(trustedDomain.getSpiffeTrustDomain());
        td.setIssuer(trustedDomain.getDomainIdentifier());
        td.setRefreshIntervalSeconds(trustedDomain.getRefreshIntervalSeconds());
        td.setAllowedAlgorithms(trustedDomain.getAllowedAlgorithms());
        td.setScopeMappings(trustedDomain.getScopeMappings());
        td.setUserBindingEnabled(trustedDomain.isUserBindingEnabled());
        td.setUserBindingCriteria(trustedDomain.getUserBindingCriteria());

        TrustDomainKeyMaterial keyMaterial = trustedDomain.getKeyMaterial();
        if (keyMaterial != null) {
            td.setJwksUrl(keyMaterial.getJwksUrl());
            td.setStaticJwks(keyMaterial.getJwkSet());
            if (keyMaterial.getSource() != null) {
                td.setBundleSource(switch (keyMaterial.getSource()) {
                    case JWKS_URL -> SpiffeBundleSource.JWKS_URL;
                    case JWK_SET -> SpiffeBundleSource.STATIC_JWKS;
                    case PEM -> null;
                });
            }
        }
        return td;
    }
}
