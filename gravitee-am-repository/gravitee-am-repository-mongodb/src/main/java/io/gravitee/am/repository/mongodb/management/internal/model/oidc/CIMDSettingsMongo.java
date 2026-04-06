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
package io.gravitee.am.repository.mongodb.management.internal.model.oidc;

import java.util.List;

/**
 * @author GraviteeSource Team
 */
public class CIMDSettingsMongo {

    private boolean enabled;
    private boolean allowUnsecuredHttpUri;
    private boolean allowPrivateIpAddress;
    private int fetchTimeoutMs;
    private int maxResponseSizeKb;
    private List<String> allowedDomains;
    private int cacheTtlSeconds;
    private int cacheMaxEntries;
    private List<String> allowedGrantTypes;
    private List<String> allowedScopes;
    private List<String> identityProviders;
    private String certificateId;
    private int accessTokenValiditySeconds;
    private int refreshTokenValiditySeconds;
    private int idTokenValiditySeconds;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAllowUnsecuredHttpUri() {
        return allowUnsecuredHttpUri;
    }

    public void setAllowUnsecuredHttpUri(boolean allowUnsecuredHttpUri) {
        this.allowUnsecuredHttpUri = allowUnsecuredHttpUri;
    }

    public boolean isAllowPrivateIpAddress() {
        return allowPrivateIpAddress;
    }

    public void setAllowPrivateIpAddress(boolean allowPrivateIpAddress) {
        this.allowPrivateIpAddress = allowPrivateIpAddress;
    }

    public int getFetchTimeoutMs() {
        return fetchTimeoutMs;
    }

    public void setFetchTimeoutMs(int fetchTimeoutMs) {
        this.fetchTimeoutMs = fetchTimeoutMs;
    }

    public int getMaxResponseSizeKb() {
        return maxResponseSizeKb;
    }

    public void setMaxResponseSizeKb(int maxResponseSizeKb) {
        this.maxResponseSizeKb = maxResponseSizeKb;
    }

    public List<String> getAllowedDomains() {
        return allowedDomains;
    }

    public void setAllowedDomains(List<String> allowedDomains) {
        this.allowedDomains = allowedDomains;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(int cacheMaxEntries) {
        this.cacheMaxEntries = cacheMaxEntries;
    }

    public List<String> getAllowedGrantTypes() {
        return allowedGrantTypes;
    }

    public void setAllowedGrantTypes(List<String> allowedGrantTypes) {
        this.allowedGrantTypes = allowedGrantTypes;
    }

    public List<String> getAllowedScopes() {
        return allowedScopes;
    }

    public void setAllowedScopes(List<String> allowedScopes) {
        this.allowedScopes = allowedScopes;
    }

    public List<String> getIdentityProviders() {
        return identityProviders;
    }

    public void setIdentityProviders(List<String> identityProviders) {
        this.identityProviders = identityProviders;
    }

    public String getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(String certificateId) {
        this.certificateId = certificateId;
    }

    public int getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    public void setAccessTokenValiditySeconds(int accessTokenValiditySeconds) {
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
    }

    public int getRefreshTokenValiditySeconds() {
        return refreshTokenValiditySeconds;
    }

    public void setRefreshTokenValiditySeconds(int refreshTokenValiditySeconds) {
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public int getIdTokenValiditySeconds() {
        return idTokenValiditySeconds;
    }

    public void setIdTokenValiditySeconds(int idTokenValiditySeconds) {
        this.idTokenValiditySeconds = idTokenValiditySeconds;
    }
}
