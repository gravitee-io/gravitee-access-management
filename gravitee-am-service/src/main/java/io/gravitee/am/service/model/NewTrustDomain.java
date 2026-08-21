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
package io.gravitee.am.service.model;

import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public class NewTrustDomain {
    private String name;
    private String description;
    private String spiffeTrustDomain;
    private String issuer;
    private TrustDomainKeyMaterial keyMaterial;
    private SpiffeBundleSource bundleSource;
    private String jwksUrl;
    private Integer refreshIntervalSeconds;
    private List<String> allowedAlgorithms;
    private Map<String, String> scopeMappings;
    private boolean userBindingEnabled;
    private List<UserBindingCriterion> userBindingCriteria;

    @Size(max = TrustDomain.NAME_MAX_LENGTH, message = "Name must be at most {max} characters")
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /**
     * SPIFFE trust domain this authority issues IDs for. Defaults to {@code name} when neither matcher
     * is supplied, so a payload written against the SPIFFE-only API keeps its meaning.
     */
    @Schema(description = "SPIFFE trust domain matched against the \"sub\" of a JWT-SVID. Defaults to the "
            + "name when no matcher is supplied.")
    @Size(max = TrustDomain.SPIFFE_TRUST_DOMAIN_MAX_LENGTH, message = "SPIFFE trust domain must be at most {max} characters")
    public String getSpiffeTrustDomain() { return spiffeTrustDomain; }
    public void setSpiffeTrustDomain(String spiffeTrustDomain) { this.spiffeTrustDomain = spiffeTrustDomain; }

    @Size(max = TrustDomain.ISSUER_MAX_LENGTH, message = "Issuer must be at most {max} characters")
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public TrustDomainKeyMaterial getKeyMaterial() { return keyMaterial; }
    public void setKeyMaterial(TrustDomainKeyMaterial keyMaterial) { this.keyMaterial = keyMaterial; }

    /**
     * @deprecated supply {@code keyMaterial} instead; ignored when {@code keyMaterial} is present.
     */
    @Deprecated
    @Schema(deprecated = true, description = "Use keyMaterial.source instead.")
    public SpiffeBundleSource getBundleSource() { return bundleSource; }
    public void setBundleSource(SpiffeBundleSource bundleSource) { this.bundleSource = bundleSource; }

    /**
     * @deprecated supply {@code keyMaterial} instead; ignored when {@code keyMaterial} is present.
     */
    @Deprecated
    @Schema(deprecated = true, description = "Use keyMaterial.jwksUrl instead.")
    public String getJwksUrl() { return jwksUrl; }
    public void setJwksUrl(String jwksUrl) { this.jwksUrl = jwksUrl; }

    public Integer getRefreshIntervalSeconds() { return refreshIntervalSeconds; }
    public void setRefreshIntervalSeconds(Integer refreshIntervalSeconds) { this.refreshIntervalSeconds = refreshIntervalSeconds; }

    public List<String> getAllowedAlgorithms() { return allowedAlgorithms; }
    public void setAllowedAlgorithms(List<String> allowedAlgorithms) { this.allowedAlgorithms = allowedAlgorithms; }

    public Map<String, String> getScopeMappings() { return scopeMappings; }
    public void setScopeMappings(Map<String, String> scopeMappings) { this.scopeMappings = scopeMappings; }

    public boolean isUserBindingEnabled() { return userBindingEnabled; }
    public void setUserBindingEnabled(boolean userBindingEnabled) { this.userBindingEnabled = userBindingEnabled; }

    public List<UserBindingCriterion> getUserBindingCriteria() { return userBindingCriteria; }
    public void setUserBindingCriteria(List<UserBindingCriterion> userBindingCriteria) { this.userBindingCriteria = userBindingCriteria; }
}
