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

import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.model.PatchKeyRetrievalSettings;
import io.gravitee.am.service.utils.SetterUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The retrieval limits below are still accepted here so configuration written before they moved to
 * {@link PatchKeyRetrievalSettings} keeps working; they are applied to the block that now owns them.
 */
@NoArgsConstructor
public class PatchSpiffeDomainSettings {

    private Optional<Boolean> enabled;
    @Schema(description = "Deprecated: moved to keyRetrievalSettings.allowUnsecuredHttpUri.", deprecated = true)
    @Deprecated
    private Optional<Boolean> allowUnsecuredHttpUri;
    @Schema(description = "Deprecated: moved to keyRetrievalSettings.allowPrivateIpAddress.", deprecated = true)
    @Deprecated
    private Optional<Boolean> allowPrivateIpAddress;
    @Schema(description = "Deprecated: moved to keyRetrievalSettings.fetchTimeoutMs.", deprecated = true)
    @Deprecated
    private Optional<Integer> fetchTimeoutMs;
    @Schema(description = "Deprecated: moved to keyRetrievalSettings.maxResponseSizeKb.", deprecated = true)
    @Deprecated
    private Optional<Integer> maxResponseSizeKb;
    @Schema(description = "Deprecated: moved to keyRetrievalSettings.cacheTtlSeconds.", deprecated = true)
    @Deprecated
    private Optional<Integer> cacheTtlSeconds;
    @Schema(description = "Deprecated: moved to keyRetrievalSettings.cacheMaxEntries.", deprecated = true)
    @Deprecated
    private Optional<Integer> cacheMaxEntries;
    private Optional<Integer> maxJwtLifetimeSeconds;
    private Optional<Integer> clockSkewSeconds;
    private Optional<List<String>> defaultAllowedAlgorithms;

    public Optional<Boolean> getEnabled() { return enabled; }
    public void setEnabled(Optional<Boolean> enabled) { this.enabled = enabled; }

    @Deprecated
    public Optional<Boolean> getAllowUnsecuredHttpUri() { return allowUnsecuredHttpUri; }
    @Deprecated
    public void setAllowUnsecuredHttpUri(Optional<Boolean> allowUnsecuredHttpUri) { this.allowUnsecuredHttpUri = allowUnsecuredHttpUri; }

    @Deprecated
    public Optional<Boolean> getAllowPrivateIpAddress() { return allowPrivateIpAddress; }
    @Deprecated
    public void setAllowPrivateIpAddress(Optional<Boolean> allowPrivateIpAddress) { this.allowPrivateIpAddress = allowPrivateIpAddress; }

    @Deprecated
    public Optional<Integer> getFetchTimeoutMs() { return fetchTimeoutMs; }
    @Deprecated
    public void setFetchTimeoutMs(Optional<Integer> fetchTimeoutMs) { this.fetchTimeoutMs = fetchTimeoutMs; }

    @Deprecated
    public Optional<Integer> getMaxResponseSizeKb() { return maxResponseSizeKb; }
    @Deprecated
    public void setMaxResponseSizeKb(Optional<Integer> maxResponseSizeKb) { this.maxResponseSizeKb = maxResponseSizeKb; }

    @Deprecated
    public Optional<Integer> getCacheTtlSeconds() { return cacheTtlSeconds; }
    @Deprecated
    public void setCacheTtlSeconds(Optional<Integer> cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }

    @Deprecated
    public Optional<Integer> getCacheMaxEntries() { return cacheMaxEntries; }
    @Deprecated
    public void setCacheMaxEntries(Optional<Integer> cacheMaxEntries) { this.cacheMaxEntries = cacheMaxEntries; }

    public PatchKeyRetrievalSettings toKeyRetrievalPatch() {
        if (allowUnsecuredHttpUri == null && allowPrivateIpAddress == null && fetchTimeoutMs == null
                && maxResponseSizeKb == null && cacheTtlSeconds == null && cacheMaxEntries == null) {
            return null;
        }
        PatchKeyRetrievalSettings relocated = new PatchKeyRetrievalSettings();
        relocated.setAllowUnsecuredHttpUri(allowUnsecuredHttpUri);
        relocated.setAllowPrivateIpAddress(allowPrivateIpAddress);
        relocated.setFetchTimeoutMs(fetchTimeoutMs);
        relocated.setMaxResponseSizeKb(maxResponseSizeKb);
        relocated.setCacheTtlSeconds(cacheTtlSeconds);
        relocated.setCacheMaxEntries(cacheMaxEntries);
        return relocated;
    }

    public Optional<Integer> getMaxJwtLifetimeSeconds() { return maxJwtLifetimeSeconds; }
    public void setMaxJwtLifetimeSeconds(Optional<Integer> maxJwtLifetimeSeconds) { this.maxJwtLifetimeSeconds = maxJwtLifetimeSeconds; }

    public Optional<Integer> getClockSkewSeconds() { return clockSkewSeconds; }
    public void setClockSkewSeconds(Optional<Integer> clockSkewSeconds) { this.clockSkewSeconds = clockSkewSeconds; }

    public Optional<List<String>> getDefaultAllowedAlgorithms() { return defaultAllowedAlgorithms; }
    public void setDefaultAllowedAlgorithms(Optional<List<String>> defaultAllowedAlgorithms) { this.defaultAllowedAlgorithms = defaultAllowedAlgorithms; }

    public SpiffeDomainSettings patch(SpiffeDomainSettings toPatch) {
        validate();
        SpiffeDomainSettings result = toPatch != null ? toPatch : SpiffeDomainSettings.defaultSettings();
        SetterUtils.safeSet(result::setEnabled, this.getEnabled(), boolean.class);
        Optional.ofNullable(getMaxJwtLifetimeSeconds())
                .ifPresent(opt -> result.setMaxJwtLifetimeSeconds(opt.orElse(SpiffeDomainSettings.DEFAULT_MAX_JWT_LIFETIME_SECONDS)));
        Optional.ofNullable(getClockSkewSeconds())
                .ifPresent(opt -> result.setClockSkewSeconds(opt.orElse(SpiffeDomainSettings.DEFAULT_CLOCK_SKEW_SECONDS)));
        SetterUtils.safeSet(result::setDefaultAllowedAlgorithms, this.getDefaultAllowedAlgorithms());
        return result;
    }

    private void validate() {
        requirePositive("maxJwtLifetimeSeconds", maxJwtLifetimeSeconds);
        requireNonNegative("clockSkewSeconds", clockSkewSeconds);
        if (defaultAllowedAlgorithms != null && defaultAllowedAlgorithms.isPresent()) {
            for (String alg : defaultAllowedAlgorithms.get()) {
                if (alg == null || alg.isBlank()
                        || alg.equalsIgnoreCase("none")
                        || alg.toUpperCase(Locale.ROOT).startsWith("HS")) {
                    throw new InvalidParameterException(
                            "defaultAllowedAlgorithms must not contain 'none' or HMAC variants (HS256/HS384/HS512)");
                }
            }
        }
    }

    private static void requirePositive(String field, Optional<Integer> value) {
        if (value != null && value.isPresent() && value.get() <= 0) {
            throw new InvalidParameterException(field + " must be a positive integer");
        }
    }

    private static void requireNonNegative(String field, Optional<Integer> value) {
        if (value != null && value.isPresent() && value.get() < 0) {
            throw new InvalidParameterException(field + " must be zero or a positive integer");
        }
    }
}
