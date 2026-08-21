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

import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcTrustDomain;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Trust domains stored before the SPIFFE matcher was split out of the name, and before the shared
 * key-material shape existed, must keep working without a data migration.
 */
public class JdbcTrustDomainLegacyMappingTest {

    @Test
    public void shouldReadRowWithoutMatcherAsSpiffeUnderItsName() {
        JdbcTrustDomain legacy = new JdbcTrustDomain();
        legacy.setName("example.org");

        assertEquals("example.org", JdbcTrustDomainRepository.readSpiffeTrustDomain(legacy));
    }

    @Test
    public void shouldNotReadAMigratedIssuerRowAsSpiffe() {
        JdbcTrustDomain migrated = new JdbcTrustDomain();
        migrated.setName("issuer.example");
        migrated.setIssuer("https://issuer.example/realm");

        assertNull(JdbcTrustDomainRepository.readSpiffeTrustDomain(migrated));
    }

    @Test
    public void shouldPreferTheStoredMatcherOverTheName() {
        JdbcTrustDomain row = new JdbcTrustDomain();
        row.setName("acme-corp");
        row.setSpiffeTrustDomain("acme.org");

        assertEquals("acme.org", JdbcTrustDomainRepository.readSpiffeTrustDomain(row));
    }

    @Test
    public void shouldReadLegacyBundleSourceAsJwksUrlKeyMaterial() {
        JdbcTrustDomain legacy = new JdbcTrustDomain();
        legacy.setBundleSource("JWKS_URL");
        legacy.setJwksUrl("https://spire.example.org/keys");

        var keyMaterial = JdbcTrustDomainRepository.readKeyMaterial(legacy);

        assertEquals(KeyMaterialSource.JWKS_URL, keyMaterial.getSource());
        assertEquals("https://spire.example.org/keys", keyMaterial.getJwksUrl());
    }

    @Test
    public void shouldPreferStoredKeyMaterialOverLegacyColumns() {
        JdbcTrustDomain row = new JdbcTrustDomain();
        row.setBundleSource("JWKS_URL");
        row.setJwksUrl("https://legacy.example.org/keys");
        row.setKeyMaterial("{\"source\":\"PEM\",\"certificate\":\"cert\"}");

        var keyMaterial = JdbcTrustDomainRepository.readKeyMaterial(row);

        assertEquals(KeyMaterialSource.PEM, keyMaterial.getSource());
        assertEquals("cert", keyMaterial.getCertificate());
        assertNull(keyMaterial.getJwksUrl());
    }

    @Test
    public void shouldReadNoKeyMaterial_whenRowHasNeither() {
        assertNull(JdbcTrustDomainRepository.readKeyMaterial(new JdbcTrustDomain()));
    }
}
