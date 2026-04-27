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

import java.util.Arrays;
import java.util.List;

/**
 * Domain-level settings for SPIFFE workload identity support.
 *
 * @author GraviteeSource Team
 */
public class SpiffeDomainSettings {

    public static final int DEFAULT_FETCH_TIMEOUT_MS = 5000;
    public static final int DEFAULT_MAX_RESPONSE_SIZE_KB = 32;
    public static final int DEFAULT_CACHE_TTL_SECONDS = 300;
    public static final int DEFAULT_CACHE_MAX_ENTRIES = 50;
    public static final int DEFAULT_MAX_JWT_LIFETIME_SECONDS = 300;
    public static final int DEFAULT_CLOCK_SKEW_SECONDS = 30;
    public static final List<String> DEFAULT_ALLOWED_ALGORITHMS =
            Arrays.asList("RS256", "RS384", "RS512", "ES256", "ES384", "ES512", "EdDSA");

    /**
     * Whether the {@code spiffe_jwt} authentication method is permitted in this domain.
     */
    private boolean enabled;

    // --- SSRF Protection ---

    private boolean allowUnsecuredHttpUri;

    private boolean allowPrivateIpAddress;

    private int fetchTimeoutMs = DEFAULT_FETCH_TIMEOUT_MS;

    private int maxResponseSizeKb = DEFAULT_MAX_RESPONSE_SIZE_KB;

    // --- Bundle Caching ---

    private int cacheTtlSeconds = DEFAULT_CACHE_TTL_SECONDS;

    private int cacheMaxEntries = DEFAULT_CACHE_MAX_ENTRIES;

    // --- JWT Validation Policy ---

    /**
     * Maximum permitted {@code exp - iat} on an incoming SVID (SPIFFE guidance: ≤ 5 min).
     */
    private int maxJwtLifetimeSeconds = DEFAULT_MAX_JWT_LIFETIME_SECONDS;

    private int clockSkewSeconds = DEFAULT_CLOCK_SKEW_SECONDS;

    /**
     * Default algorithm allowlist; {@code none} and HMAC are always rejected.
     */
    private List<String> defaultAllowedAlgorithms = DEFAULT_ALLOWED_ALGORITHMS;

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

    public int getMaxJwtLifetimeSeconds() {
        return maxJwtLifetimeSeconds;
    }

    public void setMaxJwtLifetimeSeconds(int maxJwtLifetimeSeconds) {
        this.maxJwtLifetimeSeconds = maxJwtLifetimeSeconds;
    }

    public int getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(int clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public List<String> getDefaultAllowedAlgorithms() {
        return defaultAllowedAlgorithms;
    }

    public void setDefaultAllowedAlgorithms(List<String> defaultAllowedAlgorithms) {
        this.defaultAllowedAlgorithms = defaultAllowedAlgorithms;
    }

    public static SpiffeDomainSettings defaultSettings() {
        return new SpiffeDomainSettings();
    }
}
