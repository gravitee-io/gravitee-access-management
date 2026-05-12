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
package io.gravitee.am.service.model.openid;

import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.service.utils.SetterUtils;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Optional;

/**
 * Patch model for {@link CIMDSettings}.
 *
 * @author GraviteeSource Team
 */
@NoArgsConstructor
public class PatchCIMDSettings {

    private Optional<Boolean> enabled;

    // SSRF Protection
    private Optional<Boolean> allowUnsecuredHttpUri;
    private Optional<Boolean> allowPrivateIpAddress;
    private Optional<Integer> fetchTimeoutMs;
    private Optional<Integer> maxResponseSizeKb;

    // Domain Trust Policy
    private Optional<List<String>> allowedDomains;

    // Cache Settings
    private Optional<Integer> cacheTtlSeconds;
    private Optional<Integer> cacheMaxEntries;

    // Security Policy
    private Optional<String> templateId;

    private Optional<Boolean> revokeOnDocumentChange;

    public Optional<Boolean> getEnabled() {
        return enabled;
    }

    public void setEnabled(Optional<Boolean> enabled) {
        this.enabled = enabled;
    }

    public Optional<Boolean> getAllowUnsecuredHttpUri() {
        return allowUnsecuredHttpUri;
    }

    public void setAllowUnsecuredHttpUri(Optional<Boolean> allowUnsecuredHttpUri) {
        this.allowUnsecuredHttpUri = allowUnsecuredHttpUri;
    }

    public Optional<Boolean> getAllowPrivateIpAddress() {
        return allowPrivateIpAddress;
    }

    public void setAllowPrivateIpAddress(Optional<Boolean> allowPrivateIpAddress) {
        this.allowPrivateIpAddress = allowPrivateIpAddress;
    }

    public Optional<Integer> getFetchTimeoutMs() {
        return fetchTimeoutMs;
    }

    public void setFetchTimeoutMs(Optional<Integer> fetchTimeoutMs) {
        this.fetchTimeoutMs = fetchTimeoutMs;
    }

    public Optional<Integer> getMaxResponseSizeKb() {
        return maxResponseSizeKb;
    }

    public void setMaxResponseSizeKb(Optional<Integer> maxResponseSizeKb) {
        this.maxResponseSizeKb = maxResponseSizeKb;
    }

    public Optional<List<String>> getAllowedDomains() {
        return allowedDomains;
    }

    public void setAllowedDomains(Optional<List<String>> allowedDomains) {
        this.allowedDomains = allowedDomains;
    }

    public Optional<Integer> getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(Optional<Integer> cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public Optional<Integer> getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public void setCacheMaxEntries(Optional<Integer> cacheMaxEntries) {
        this.cacheMaxEntries = cacheMaxEntries;
    }

    public Optional<String> getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Optional<String> templateId) {
        this.templateId = templateId;
    }

    public Optional<Boolean> getRevokeOnDocumentChange() {
        return revokeOnDocumentChange;
    }

    public void setRevokeOnDocumentChange(Optional<Boolean> revokeOnDocumentChange) {
        this.revokeOnDocumentChange = revokeOnDocumentChange;
    }

    public CIMDSettings patch(CIMDSettings toPatch) {
        CIMDSettings result = toPatch != null ? toPatch : CIMDSettings.defaultSettings();

        SetterUtils.safeSet(result::setEnabled, this.getEnabled(), boolean.class);
        SetterUtils.safeSet(result::setAllowUnsecuredHttpUri, this.getAllowUnsecuredHttpUri(), boolean.class);
        SetterUtils.safeSet(result::setAllowPrivateIpAddress, this.getAllowPrivateIpAddress(), boolean.class);
        Optional.ofNullable(getFetchTimeoutMs())
                .ifPresent(opt -> result.setFetchTimeoutMs(opt.orElse(CIMDSettings.DEFAULT_FETCH_TIMEOUT_MS)));
        Optional.ofNullable(getMaxResponseSizeKb())
                .ifPresent(opt -> result.setMaxResponseSizeKb(opt.orElse(CIMDSettings.DEFAULT_MAX_RESPONSE_SIZE_KB)));
        SetterUtils.safeSet(result::setAllowedDomains, this.getAllowedDomains());
        Optional.ofNullable(getCacheTtlSeconds())
                .ifPresent(opt -> result.setCacheTtlSeconds(opt.orElse(CIMDSettings.DEFAULT_CACHE_TTL_SECONDS)));
        Optional.ofNullable(getCacheMaxEntries())
                .ifPresent(opt -> result.setCacheMaxEntries(opt.orElse(CIMDSettings.DEFAULT_CACHE_MAX_ENTRIES)));
        SetterUtils.safeSet(result::setTemplateId, this.getTemplateId(), String.class);
        SetterUtils.safeSet(result::setRevokeOnDocumentChange, this.getRevokeOnDocumentChange(), boolean.class);

        return result;
    }
}
