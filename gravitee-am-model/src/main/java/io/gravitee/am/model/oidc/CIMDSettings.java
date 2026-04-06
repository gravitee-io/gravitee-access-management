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
 * Domain-level settings for Client ID Metadata Document (CIMD) support.
 *
 * @author GraviteeSource Team
 */
public class CIMDSettings {

    public static final int DEFAULT_FETCH_TIMEOUT_MS = 5000;
    public static final int DEFAULT_MAX_RESPONSE_SIZE_KB = 10;
    public static final int DEFAULT_CACHE_TTL_SECONDS = 86400;
    public static final int DEFAULT_CACHE_MAX_ENTRIES = 1000;
    public static final int DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS = 7200;
    public static final int DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS = 14400;
    public static final int DEFAULT_ID_TOKEN_VALIDITY_SECONDS = 14400;

    /**
     * Whether CIMD authentication is permitted for the domain.
     */
    private boolean enabled;

    // --- SSRF Protection ---

    /**
     * Whether http:// client_id URIs are permitted (default: false).
     */
    private boolean allowUnsecuredHttpUri;

    /**
     * Whether the resolved IP of the metadata URL may be private or loopback (default: false).
     */
    private boolean allowPrivateIpAddress;

    /**
     * Maximum time in milliseconds to wait for a metadata document response.
     */
    private int fetchTimeoutMs = DEFAULT_FETCH_TIMEOUT_MS;

    /**
     * Maximum bytes accepted from the metadata document server (enforced against bytes read, not Content-Length).
     */
    private int maxResponseSizeKb = DEFAULT_MAX_RESPONSE_SIZE_KB;

    // --- Domain Trust Policy ---

    /**
     * Permitted hostname patterns for client_id URLs. Supports wildcard (*) for first-level subdomain.
     */
    private List<String> allowedDomains;

    // --- Cache Settings ---

    /**
     * Maximum duration in seconds to retain a cached metadata document (default: 24 hours).
     * May be reduced by the source server's Cache-Control: max-age directive.
     */
    private int cacheTtlSeconds = DEFAULT_CACHE_TTL_SECONDS;

    /**
     * Ceiling on the number of cached metadata entries per gateway instance.
     */
    private int cacheMaxEntries = DEFAULT_CACHE_MAX_ENTRIES;

    // --- Security Policy ---

    /**
     * Grant types that CIMD clients are permitted to use.
     */
    private List<String> allowedGrantTypes = Arrays.asList("authorization_code", "password");

    /**
     * Scopes that CIMD clients may request. Scopes not in this list are stripped before token issuance.
     */
    private List<String> allowedScopes;

    /**
     * Identity provider IDs available to CIMD clients.
     */
    private List<String> identityProviders;

    /**
     * Certificate ID used to sign tokens issued to CIMD clients.
     */
    private String certificateId;

    /**
     * Access token validity in seconds for CIMD clients.
     */
    private int accessTokenValiditySeconds = DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS;

    /**
     * Refresh token validity in seconds for CIMD clients.
     */
    private int refreshTokenValiditySeconds = DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS;

    /**
     * ID token validity in seconds for CIMD clients.
     */
    private int idTokenValiditySeconds = DEFAULT_ID_TOKEN_VALIDITY_SECONDS;

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

    public static CIMDSettings defaultSettings() {
        return new CIMDSettings();
    }
}
