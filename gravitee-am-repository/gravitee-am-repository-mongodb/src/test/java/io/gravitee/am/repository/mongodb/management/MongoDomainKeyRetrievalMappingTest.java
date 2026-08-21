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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.model.oidc.KeyRetrievalSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.KeyRetrievalSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.OIDCSettingsMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.SpiffeDomainSettingsMongo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Domains stored before the retrieval limits were promoted out of the SPIFFE block must read back
 * with the same limits.
 */
public class MongoDomainKeyRetrievalMappingTest {

    @Test
    public void shouldReadLegacySpiffeLimitsAsKeyRetrievalSettings() {
        SpiffeDomainSettingsMongo legacy = new SpiffeDomainSettingsMongo();
        legacy.setEnabled(true);
        legacy.setAllowPrivateIpAddress(true);
        legacy.setFetchTimeoutMs(1234);
        legacy.setMaxResponseSizeKb(64);
        legacy.setCacheTtlSeconds(60);
        legacy.setCacheMaxEntries(10);
        OIDCSettingsMongo document = new OIDCSettingsMongo();
        document.setWorkloadIdentitySettings(legacy);

        KeyRetrievalSettings settings = MongoDomainRepository.convert(document).getKeyRetrievalSettings();

        assertTrue(settings.isAllowPrivateIpAddress());
        assertEquals(1234, settings.getFetchTimeoutMs());
        assertEquals(64, settings.getMaxResponseSizeKb());
        assertEquals(60, settings.getCacheTtlSeconds());
        assertEquals(10, settings.getCacheMaxEntries());
    }

    @Test
    public void shouldPreferStoredKeyRetrievalSettingsOverLegacyLimits() {
        SpiffeDomainSettingsMongo legacy = new SpiffeDomainSettingsMongo();
        legacy.setFetchTimeoutMs(1234);
        KeyRetrievalSettingsMongo relocated = new KeyRetrievalSettingsMongo();
        relocated.setFetchTimeoutMs(999);
        OIDCSettingsMongo document = new OIDCSettingsMongo();
        document.setWorkloadIdentitySettings(legacy);
        document.setKeyRetrievalSettings(relocated);

        assertEquals(999, MongoDomainRepository.convert(document).getKeyRetrievalSettings().getFetchTimeoutMs());
    }

    @Test
    public void shouldLeaveLegacyLimitsUnsetOnDocumentWithoutThem() {
        SpiffeDomainSettingsMongo legacy = new SpiffeDomainSettingsMongo();
        legacy.setEnabled(true);
        OIDCSettingsMongo document = new OIDCSettingsMongo();
        document.setWorkloadIdentitySettings(legacy);

        SpiffeDomainSettings spiffe = MongoDomainRepository.convert(document).getWorkloadIdentitySettings();

        assertTrue(spiffe.isEnabled());
        assertNull(spiffe.getFetchTimeoutMs());
    }

    @Test
    public void shouldRoundTripKeyRetrievalSettings() {
        KeyRetrievalSettings settings = new KeyRetrievalSettings();
        settings.setAllowUnsecuredHttpUri(true);
        settings.setCacheMaxEntries(7);
        OIDCSettings oidc = new OIDCSettings();
        oidc.setKeyRetrievalSettings(settings);

        KeyRetrievalSettings persisted = MongoDomainRepository.convert(MongoDomainRepository.convert(oidc))
                .getKeyRetrievalSettings();

        assertTrue(persisted.isAllowUnsecuredHttpUri());
        assertEquals(7, persisted.getCacheMaxEntries());
        assertEquals(KeyRetrievalSettings.DEFAULT_FETCH_TIMEOUT_MS, persisted.getFetchTimeoutMs());
    }

    @Test
    public void shouldNotWriteBackLegacyLimits() {
        SpiffeDomainSettingsMongo legacy = new SpiffeDomainSettingsMongo();
        legacy.setFetchTimeoutMs(1234);
        OIDCSettingsMongo document = new OIDCSettingsMongo();
        document.setWorkloadIdentitySettings(legacy);

        OIDCSettingsMongo rewritten = MongoDomainRepository.convert(MongoDomainRepository.convert(document));

        assertNull(rewritten.getWorkloadIdentitySettings().getFetchTimeoutMs());
    }
}
