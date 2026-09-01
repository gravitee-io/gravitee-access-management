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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyRetrievalSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.repository.jdbc.provider.common.JSONMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The oidc column is a JSON document, so domains stored before the retrieval limits were promoted
 * out of the SPIFFE block must read those limits back from the JSON already on disk, while the
 * relocated block is read from its own column.
 */
public class JdbcDomainKeyRetrievalMappingTest {

    private static final String LEGACY_OIDC_JSON = """
            {"workloadIdentitySettings":{"enabled":true,"allowUnsecuredHttpUri":false,\
            "allowPrivateIpAddress":true,"fetchTimeoutMs":1234,"maxResponseSizeKb":64,\
            "cacheTtlSeconds":60,"cacheMaxEntries":10,"maxJwtLifetimeSeconds":120,"clockSkewSeconds":15}}""";

    @Test
    public void shouldReadLegacySpiffeLimitsAsKeyRetrievalSettings() {
        KeyRetrievalSettings settings = legacyDomain().getKeyRetrievalSettings();

        assertTrue(settings.isAllowPrivateIpAddress());
        assertEquals(1234, settings.getFetchTimeoutMs());
        assertEquals(64, settings.getMaxResponseSizeKb());
        assertEquals(60, settings.getCacheTtlSeconds());
        assertEquals(10, settings.getCacheMaxEntries());
    }

    @Test
    public void shouldKeepSpiffeValidationPolicyOnLegacyDocument() {
        OIDCSettings oidc = legacyDomain().getOidc();

        assertTrue(oidc.getWorkloadIdentitySettings().isEnabled());
        assertEquals(120, oidc.getWorkloadIdentitySettings().getMaxJwtLifetimeSeconds());
        assertEquals(15, oidc.getWorkloadIdentitySettings().getClockSkewSeconds());
    }

    @Test
    public void shouldPreferStoredKeyRetrievalSettingsOverLegacyLimits() {
        Domain domain = legacyDomain();
        domain.setKeyRetrievalSettings(JSONMapper.toBean("""
                {"fetchTimeoutMs":999}""", KeyRetrievalSettings.class));

        assertEquals(999, domain.getKeyRetrievalSettings().getFetchTimeoutMs());
    }

    @Test
    public void shouldRoundTripKeyRetrievalSettings() {
        KeyRetrievalSettings settings = new KeyRetrievalSettings();
        settings.setAllowUnsecuredHttpUri(true);
        settings.setCacheMaxEntries(7);

        KeyRetrievalSettings persisted =
                JSONMapper.toBean(JSONMapper.toJson(settings), KeyRetrievalSettings.class);

        assertTrue(persisted.isAllowUnsecuredHttpUri());
        assertEquals(7, persisted.getCacheMaxEntries());
    }

    @Test
    public void shouldNotExposeTheUnmigratedBlockAsConfigured() {
        assertNull(legacyDomain().getConfiguredKeyRetrievalSettings());
    }

    private static Domain legacyDomain() {
        Domain domain = new Domain();
        domain.setOidc(JSONMapper.toBean(LEGACY_OIDC_JSON, OIDCSettings.class));
        return domain;
    }
}
