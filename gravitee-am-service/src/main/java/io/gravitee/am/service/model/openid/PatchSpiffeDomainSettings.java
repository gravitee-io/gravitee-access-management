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
import io.gravitee.am.service.utils.SetterUtils;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@NoArgsConstructor
public class PatchSpiffeDomainSettings {

    private Optional<Boolean> enabled;
    private Optional<Boolean> allowUnsecuredHttpUri;
    private Optional<Boolean> allowPrivateIpAddress;
    private Optional<Integer> fetchTimeoutMs;
    private Optional<Integer> maxResponseSizeKb;
    private Optional<Integer> cacheTtlSeconds;
    private Optional<Integer> cacheMaxEntries;
    private Optional<Integer> maxJwtLifetimeSeconds;
    private Optional<Integer> clockSkewSeconds;
    private Optional<List<String>> defaultAllowedAlgorithms;

    public Optional<Boolean> getEnabled() { return enabled; }
    public void setEnabled(Optional<Boolean> enabled) { this.enabled = enabled; }

    public Optional<Boolean> getAllowUnsecuredHttpUri() { return allowUnsecuredHttpUri; }
    public void setAllowUnsecuredHttpUri(Optional<Boolean> allowUnsecuredHttpUri) { this.allowUnsecuredHttpUri = allowUnsecuredHttpUri; }

    public Optional<Boolean> getAllowPrivateIpAddress() { return allowPrivateIpAddress; }
    public void setAllowPrivateIpAddress(Optional<Boolean> allowPrivateIpAddress) { this.allowPrivateIpAddress = allowPrivateIpAddress; }

    public Optional<Integer> getFetchTimeoutMs() { return fetchTimeoutMs; }
    public void setFetchTimeoutMs(Optional<Integer> fetchTimeoutMs) { this.fetchTimeoutMs = fetchTimeoutMs; }

    public Optional<Integer> getMaxResponseSizeKb() { return maxResponseSizeKb; }
    public void setMaxResponseSizeKb(Optional<Integer> maxResponseSizeKb) { this.maxResponseSizeKb = maxResponseSizeKb; }

    public Optional<Integer> getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(Optional<Integer> cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }

    public Optional<Integer> getCacheMaxEntries() { return cacheMaxEntries; }
    public void setCacheMaxEntries(Optional<Integer> cacheMaxEntries) { this.cacheMaxEntries = cacheMaxEntries; }

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
        SetterUtils.safeSet(result::setAllowUnsecuredHttpUri, this.getAllowUnsecuredHttpUri(), boolean.class);
        SetterUtils.safeSet(result::setAllowPrivateIpAddress, this.getAllowPrivateIpAddress(), boolean.class);
        Optional.ofNullable(getFetchTimeoutMs())
                .ifPresent(opt -> result.setFetchTimeoutMs(opt.orElse(SpiffeDomainSettings.DEFAULT_FETCH_TIMEOUT_MS)));
        Optional.ofNullable(getMaxResponseSizeKb())
                .ifPresent(opt -> result.setMaxResponseSizeKb(opt.orElse(SpiffeDomainSettings.DEFAULT_MAX_RESPONSE_SIZE_KB)));
        Optional.ofNullable(getCacheTtlSeconds())
                .ifPresent(opt -> result.setCacheTtlSeconds(opt.orElse(SpiffeDomainSettings.DEFAULT_CACHE_TTL_SECONDS)));
        Optional.ofNullable(getCacheMaxEntries())
                .ifPresent(opt -> result.setCacheMaxEntries(opt.orElse(SpiffeDomainSettings.DEFAULT_CACHE_MAX_ENTRIES)));
        Optional.ofNullable(getMaxJwtLifetimeSeconds())
                .ifPresent(opt -> result.setMaxJwtLifetimeSeconds(opt.orElse(SpiffeDomainSettings.DEFAULT_MAX_JWT_LIFETIME_SECONDS)));
        Optional.ofNullable(getClockSkewSeconds())
                .ifPresent(opt -> result.setClockSkewSeconds(opt.orElse(SpiffeDomainSettings.DEFAULT_CLOCK_SKEW_SECONDS)));
        SetterUtils.safeSet(result::setDefaultAllowedAlgorithms, this.getDefaultAllowedAlgorithms());
        return result;
    }

    private void validate() {
        requirePositive("fetchTimeoutMs", fetchTimeoutMs);
        requirePositive("maxResponseSizeKb", maxResponseSizeKb);
        requirePositive("cacheTtlSeconds", cacheTtlSeconds);
        requirePositive("cacheMaxEntries", cacheMaxEntries);
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
