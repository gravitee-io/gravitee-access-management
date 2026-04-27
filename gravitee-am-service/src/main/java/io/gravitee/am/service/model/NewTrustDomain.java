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

import io.gravitee.am.model.oidc.SpiffeBundleSource;

import java.util.List;

public class NewTrustDomain {
    private String name;
    private String description;
    private SpiffeBundleSource bundleSource;
    private String jwksUrl;
    private Integer refreshIntervalSeconds;
    private List<String> allowedAlgorithms;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public SpiffeBundleSource getBundleSource() { return bundleSource; }
    public void setBundleSource(SpiffeBundleSource bundleSource) { this.bundleSource = bundleSource; }

    public String getJwksUrl() { return jwksUrl; }
    public void setJwksUrl(String jwksUrl) { this.jwksUrl = jwksUrl; }

    public Integer getRefreshIntervalSeconds() { return refreshIntervalSeconds; }
    public void setRefreshIntervalSeconds(Integer refreshIntervalSeconds) { this.refreshIntervalSeconds = refreshIntervalSeconds; }

    public List<String> getAllowedAlgorithms() { return allowedAlgorithms; }
    public void setAllowedAlgorithms(List<String> allowedAlgorithms) { this.allowedAlgorithms = allowedAlgorithms; }
}
