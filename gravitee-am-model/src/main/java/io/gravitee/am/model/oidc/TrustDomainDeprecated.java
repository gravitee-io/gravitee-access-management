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
import io.gravitee.am.model.UserBindingCriterion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public abstract class TrustDomainDeprecated {

    public static final int DEFAULT_REFRESH_INTERVAL_SECONDS = 300;

    public static final int SPIFFE_TRUST_DOMAIN_MAX_LENGTH = 255;

    public static final int ISSUER_MAX_LENGTH = 512;

    public abstract String getDomainIdentifier();

    public abstract TrustDomainKeyMaterial getKeyMaterial();

    public abstract SpiffeTrustSettings getSpiffe();

    public abstract TokenExchangeTrustSettings getTokenExchange();

    @Deprecated
    @JsonProperty
    @Schema(deprecated = true, description = "Use spiffe.spiffeTrustDomain instead.")
    public String getSpiffeTrustDomain() {
        return getSpiffe() != null ? getSpiffe().getSpiffeTrustDomain() : null;
    }

    @Deprecated
    @JsonProperty
    @Schema(deprecated = true, description = "Use domainIdentifier instead.")
    public String getIssuer() {
        return getDomainIdentifier();
    }

    @Deprecated
    @JsonProperty
    @Schema(deprecated = true, description = "Use keyMaterial.refreshIntervalSeconds instead.")
    public int getRefreshIntervalSeconds() {
        if (getKeyMaterial() == null || getKeyMaterial().getRefreshIntervalSeconds() == null) {
            return DEFAULT_REFRESH_INTERVAL_SECONDS;
        }
        return getKeyMaterial().getRefreshIntervalSeconds();
    }

    @Deprecated
    @JsonProperty
    @Schema(deprecated = true, description = "Use spiffe.allowedAlgorithms instead.")
    public List<String> getAllowedAlgorithms() {
        return getSpiffe() != null ? getSpiffe().getAllowedAlgorithms() : null;
    }

    @Deprecated
    @JsonProperty
    @Schema(deprecated = true, description = "Use tokenExchange.scopeMappings instead.")
    public Map<String, String> getScopeMappings() {
        return getTokenExchange() != null ? getTokenExchange().getScopeMappings() : null;
    }

    @Deprecated
    @JsonProperty
    @Schema(deprecated = true, description = "Use tokenExchange.userBindingEnabled instead.")
    public boolean isUserBindingEnabled() {
        return getTokenExchange() != null && getTokenExchange().isUserBindingEnabled();
    }

    @Deprecated
    @JsonProperty
    @Schema(deprecated = true, description = "Use tokenExchange.userBindingCriteria instead.")
    public List<UserBindingCriterion> getUserBindingCriteria() {
        return getTokenExchange() != null ? getTokenExchange().getUserBindingCriteria() : null;
    }

    @Deprecated
    @JsonProperty
    @Schema(deprecated = true, description = "Use keyMaterial.source instead. Null when the key material is a PEM certificate.")
    public SpiffeBundleSource getBundleSource() {
        if (getKeyMaterial() == null || getKeyMaterial().getSource() == null) {
            return null;
        }
        return switch (getKeyMaterial().getSource()) {
            case JWKS_URL -> SpiffeBundleSource.JWKS_URL;
            case JWK_SET -> SpiffeBundleSource.STATIC_JWKS;
            case PEM -> null;
        };
    }

    @Deprecated
    @JsonProperty
    @Schema(deprecated = true, description = "Use keyMaterial.jwksUrl instead.")
    public String getJwksUrl() {
        return getKeyMaterial() != null ? getKeyMaterial().getJwksUrl() : null;
    }

    @Deprecated
    @JsonProperty
    public JWKSet getStaticJwks() {
        return getKeyMaterial() != null ? getKeyMaterial().getJwkSet() : null;
    }
}
