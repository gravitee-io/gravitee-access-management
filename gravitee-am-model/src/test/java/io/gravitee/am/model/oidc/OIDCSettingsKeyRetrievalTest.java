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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
class OIDCSettingsKeyRetrievalTest {

    @Test
    void shouldReturnDefaultsWhenNothingConfigured() {
        KeyRetrievalSettings settings = new OIDCSettings().getKeyRetrievalSettings();

        assertFalse(settings.isAllowUnsecuredHttpUri());
        assertFalse(settings.isAllowPrivateIpAddress());
        assertEquals(KeyRetrievalSettings.DEFAULT_FETCH_TIMEOUT_MS, settings.getFetchTimeoutMs());
        assertEquals(KeyRetrievalSettings.DEFAULT_MAX_RESPONSE_SIZE_KB, settings.getMaxResponseSizeKb());
        assertEquals(KeyRetrievalSettings.DEFAULT_CACHE_TTL_SECONDS, settings.getCacheTtlSeconds());
        assertEquals(KeyRetrievalSettings.DEFAULT_CACHE_MAX_ENTRIES, settings.getCacheMaxEntries());
    }

    @Test
    void shouldFallBackToLegacySpiffeValuesWhenNeutralBlockAbsent() {
        OIDCSettings oidc = new OIDCSettings();
        oidc.setWorkloadIdentitySettings(legacySettings());

        KeyRetrievalSettings settings = oidc.getKeyRetrievalSettings();

        assertTrue(settings.isAllowUnsecuredHttpUri());
        assertTrue(settings.isAllowPrivateIpAddress());
        assertEquals(1234, settings.getFetchTimeoutMs());
        assertEquals(64, settings.getMaxResponseSizeKb());
        assertEquals(60, settings.getCacheTtlSeconds());
        assertEquals(10, settings.getCacheMaxEntries());
    }

    @Test
    void shouldKeepDefaultsForLegacyValuesLeftUnset() {
        SpiffeDomainSettings legacy = new SpiffeDomainSettings();
        legacy.setFetchTimeoutMs(1234);

        OIDCSettings oidc = new OIDCSettings();
        oidc.setWorkloadIdentitySettings(legacy);

        KeyRetrievalSettings settings = oidc.getKeyRetrievalSettings();

        assertEquals(1234, settings.getFetchTimeoutMs());
        assertEquals(KeyRetrievalSettings.DEFAULT_MAX_RESPONSE_SIZE_KB, settings.getMaxResponseSizeKb());
        assertEquals(KeyRetrievalSettings.DEFAULT_CACHE_TTL_SECONDS, settings.getCacheTtlSeconds());
        assertEquals(KeyRetrievalSettings.DEFAULT_CACHE_MAX_ENTRIES, settings.getCacheMaxEntries());
        assertFalse(settings.isAllowPrivateIpAddress());
    }

    @Test
    void shouldPreferNeutralBlockOverLegacyValues() {
        KeyRetrievalSettings neutral = new KeyRetrievalSettings();
        neutral.setFetchTimeoutMs(999);

        OIDCSettings oidc = new OIDCSettings();
        oidc.setWorkloadIdentitySettings(legacySettings());
        oidc.setKeyRetrievalSettings(neutral);

        assertEquals(999, oidc.getKeyRetrievalSettings().getFetchTimeoutMs());
        assertFalse(oidc.getKeyRetrievalSettings().isAllowPrivateIpAddress());
    }

    @Test
    void shouldNotReportNeutralBlockAsConfiguredWhenOnlyLegacyValuesExist() {
        OIDCSettings oidc = new OIDCSettings();
        oidc.setWorkloadIdentitySettings(legacySettings());

        assertNull(oidc.getConfiguredKeyRetrievalSettings());
    }

    @Test
    void shouldCopyNeutralBlockOnCopy() {
        OIDCSettings oidc = new OIDCSettings();
        oidc.setKeyRetrievalSettings(new KeyRetrievalSettings());
        oidc.getKeyRetrievalSettings().setCacheMaxEntries(7);

        OIDCSettings copy = new OIDCSettings(oidc);

        assertNotSame(oidc.getKeyRetrievalSettings(), copy.getKeyRetrievalSettings());
        assertEquals(7, copy.getKeyRetrievalSettings().getCacheMaxEntries());
    }

    private static SpiffeDomainSettings legacySettings() {
        SpiffeDomainSettings legacy = new SpiffeDomainSettings();
        legacy.setAllowUnsecuredHttpUri(true);
        legacy.setAllowPrivateIpAddress(true);
        legacy.setFetchTimeoutMs(1234);
        legacy.setMaxResponseSizeKb(64);
        legacy.setCacheTtlSeconds(60);
        legacy.setCacheMaxEntries(10);
        return legacy;
    }
}
