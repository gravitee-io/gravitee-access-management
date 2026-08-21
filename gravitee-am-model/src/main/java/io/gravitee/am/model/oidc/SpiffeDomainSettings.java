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

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Domain-level settings for SPIFFE workload identity support. The fetch, SSRF and cache limits this
 * block used to carry now live in {@link KeyRetrievalSettings}; they remain here, deprecated and
 * unset on new domains, only so values written before the move can be read back and relocated.
 *
 * @author GraviteeSource Team
 */
public class SpiffeDomainSettings {

    public static final int DEFAULT_MAX_JWT_LIFETIME_SECONDS = 300;
    public static final int DEFAULT_CLOCK_SKEW_SECONDS = 30;
    public static final List<String> DEFAULT_ALLOWED_ALGORITHMS =
            Arrays.asList("RS256", "RS384", "RS512", "ES256", "ES384", "ES512", "EdDSA");

    /**
     * Whether the {@code spiffe_jwt} authentication method is permitted in this domain.
     */
    @Schema(description = "Whether SPIFFE workload identity support is enabled for the domain.",
            defaultValue = "false")
    private boolean enabled;

    @Schema(description = "Deprecated: moved to oidc.keyRetrievalSettings.allowUnsecuredHttpUri.",
            deprecated = true)
    @Deprecated
    private Boolean allowUnsecuredHttpUri;

    @Schema(description = "Deprecated: moved to oidc.keyRetrievalSettings.allowPrivateIpAddress.",
            deprecated = true)
    @Deprecated
    private Boolean allowPrivateIpAddress;

    @Schema(description = "Deprecated: moved to oidc.keyRetrievalSettings.fetchTimeoutMs.",
            deprecated = true)
    @Deprecated
    private Integer fetchTimeoutMs;

    @Schema(description = "Deprecated: moved to oidc.keyRetrievalSettings.maxResponseSizeKb.",
            deprecated = true)
    @Deprecated
    private Integer maxResponseSizeKb;

    @Schema(description = "Deprecated: moved to oidc.keyRetrievalSettings.cacheTtlSeconds.",
            deprecated = true)
    @Deprecated
    private Integer cacheTtlSeconds;

    @Schema(description = "Deprecated: moved to oidc.keyRetrievalSettings.cacheMaxEntries.",
            deprecated = true)
    @Deprecated
    private Integer cacheMaxEntries;

    // --- JWT Validation Policy ---

    /**
     * Maximum permitted {@code exp - iat} on an incoming SVID (SPIFFE guidance: ≤ 5 min).
     */
    @Schema(description = "Maximum accepted JWT lifetime, in seconds, computed as exp minus iat.",
            defaultValue = "300")
    private int maxJwtLifetimeSeconds = DEFAULT_MAX_JWT_LIFETIME_SECONDS;

    @Schema(description = "Allowed clock skew, in seconds, when validating JWT temporal claims.",
            defaultValue = "30")
    private int clockSkewSeconds = DEFAULT_CLOCK_SKEW_SECONDS;

    /**
     * Default algorithm allowlist; {@code none} and HMAC are always rejected.
     */
    @Schema(description = "Default allowlist of signature algorithms accepted for SPIFFE JWT validation.")
    private List<String> defaultAllowedAlgorithms = DEFAULT_ALLOWED_ALGORITHMS;

    public SpiffeDomainSettings() {
    }

    public SpiffeDomainSettings(SpiffeDomainSettings other) {
        this.enabled = other.enabled;
        this.allowUnsecuredHttpUri = other.allowUnsecuredHttpUri;
        this.allowPrivateIpAddress = other.allowPrivateIpAddress;
        this.fetchTimeoutMs = other.fetchTimeoutMs;
        this.maxResponseSizeKb = other.maxResponseSizeKb;
        this.cacheTtlSeconds = other.cacheTtlSeconds;
        this.cacheMaxEntries = other.cacheMaxEntries;
        this.maxJwtLifetimeSeconds = other.maxJwtLifetimeSeconds;
        this.clockSkewSeconds = other.clockSkewSeconds;
        this.defaultAllowedAlgorithms = other.defaultAllowedAlgorithms != null
                ? new ArrayList<>(other.defaultAllowedAlgorithms) : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Deprecated
    public Boolean getAllowUnsecuredHttpUri() {
        return allowUnsecuredHttpUri;
    }

    @Deprecated
    public void setAllowUnsecuredHttpUri(Boolean allowUnsecuredHttpUri) {
        this.allowUnsecuredHttpUri = allowUnsecuredHttpUri;
    }

    @Deprecated
    public Boolean getAllowPrivateIpAddress() {
        return allowPrivateIpAddress;
    }

    @Deprecated
    public void setAllowPrivateIpAddress(Boolean allowPrivateIpAddress) {
        this.allowPrivateIpAddress = allowPrivateIpAddress;
    }

    @Deprecated
    public Integer getFetchTimeoutMs() {
        return fetchTimeoutMs;
    }

    @Deprecated
    public void setFetchTimeoutMs(Integer fetchTimeoutMs) {
        this.fetchTimeoutMs = fetchTimeoutMs;
    }

    @Deprecated
    public Integer getMaxResponseSizeKb() {
        return maxResponseSizeKb;
    }

    @Deprecated
    public void setMaxResponseSizeKb(Integer maxResponseSizeKb) {
        this.maxResponseSizeKb = maxResponseSizeKb;
    }

    @Deprecated
    public Integer getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    @Deprecated
    public void setCacheTtlSeconds(Integer cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    @Deprecated
    public Integer getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    @Deprecated
    public void setCacheMaxEntries(Integer cacheMaxEntries) {
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

    public boolean hasLegacyRetrievalSettings() {
        return allowUnsecuredHttpUri != null
                || allowPrivateIpAddress != null
                || fetchTimeoutMs != null
                || maxResponseSizeKb != null
                || cacheTtlSeconds != null
                || cacheMaxEntries != null;
    }

    public void clearLegacyRetrievalSettings() {
        this.allowUnsecuredHttpUri = null;
        this.allowPrivateIpAddress = null;
        this.fetchTimeoutMs = null;
        this.maxResponseSizeKb = null;
        this.cacheTtlSeconds = null;
        this.cacheMaxEntries = null;
    }
}
