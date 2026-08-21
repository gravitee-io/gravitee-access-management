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
import lombok.Getter;
import lombok.Setter;

/**
 * Domain-level limits applied whenever key material is retrieved for a trusted domain, whatever
 * kind of trust it represents.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Schema(title = "Trusted domain key retrieval settings",
        description = "Fetch, SSRF and cache limits applied to every trusted domain in the security domain, " +
                "regardless of its kind.")
public class KeyRetrievalSettings {

    public static final int DEFAULT_FETCH_TIMEOUT_MS = 5000;
    public static final int DEFAULT_MAX_RESPONSE_SIZE_KB = 32;
    public static final int DEFAULT_CACHE_TTL_SECONDS = 300;
    public static final int DEFAULT_CACHE_MAX_ENTRIES = 50;

    @Schema(description = "Whether key material can be fetched over unsecured HTTP URIs.",
            defaultValue = "false")
    private boolean allowUnsecuredHttpUri;

    @Schema(description = "Whether key material can be fetched from private IP addresses.",
            defaultValue = "false")
    private boolean allowPrivateIpAddress;

    @Schema(description = "Timeout, in milliseconds, for fetching key material.",
            defaultValue = "5000")
    private int fetchTimeoutMs = DEFAULT_FETCH_TIMEOUT_MS;

    @Schema(description = "Maximum key material response size, in kilobytes.",
            defaultValue = "32")
    private int maxResponseSizeKb = DEFAULT_MAX_RESPONSE_SIZE_KB;

    @Schema(description = "Time-to-live, in seconds, for cached key material.",
            defaultValue = "300")
    private int cacheTtlSeconds = DEFAULT_CACHE_TTL_SECONDS;

    @Schema(description = "Maximum number of key material entries retained in the cache.",
            defaultValue = "50")
    private int cacheMaxEntries = DEFAULT_CACHE_MAX_ENTRIES;

    public KeyRetrievalSettings() {
    }

    public KeyRetrievalSettings(KeyRetrievalSettings other) {
        this.allowUnsecuredHttpUri = other.allowUnsecuredHttpUri;
        this.allowPrivateIpAddress = other.allowPrivateIpAddress;
        this.fetchTimeoutMs = other.fetchTimeoutMs;
        this.maxResponseSizeKb = other.maxResponseSizeKb;
        this.cacheTtlSeconds = other.cacheTtlSeconds;
        this.cacheMaxEntries = other.cacheMaxEntries;
    }

    public static KeyRetrievalSettings defaultSettings() {
        return new KeyRetrievalSettings();
    }

    public static KeyRetrievalSettings fromLegacySpiffeSettings(SpiffeDomainSettings legacy) {
        KeyRetrievalSettings settings = new KeyRetrievalSettings();
        if (legacy == null) {
            return settings;
        }
        if (legacy.getAllowUnsecuredHttpUri() != null) {
            settings.setAllowUnsecuredHttpUri(legacy.getAllowUnsecuredHttpUri());
        }
        if (legacy.getAllowPrivateIpAddress() != null) {
            settings.setAllowPrivateIpAddress(legacy.getAllowPrivateIpAddress());
        }
        if (legacy.getFetchTimeoutMs() != null) {
            settings.setFetchTimeoutMs(legacy.getFetchTimeoutMs());
        }
        if (legacy.getMaxResponseSizeKb() != null) {
            settings.setMaxResponseSizeKb(legacy.getMaxResponseSizeKb());
        }
        if (legacy.getCacheTtlSeconds() != null) {
            settings.setCacheTtlSeconds(legacy.getCacheTtlSeconds());
        }
        if (legacy.getCacheMaxEntries() != null) {
            settings.setCacheMaxEntries(legacy.getCacheMaxEntries());
        }
        return settings;
    }
}
