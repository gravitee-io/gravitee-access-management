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

import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.repository.mongodb.management.internal.model.TrustDomainKeyMaterialMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.TrustDomainMongo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Trust domains stored before the SPIFFE matcher was split out of the name, and before the shared
 * key-material shape existed, must keep working without a data migration.
 */
public class MongoTrustDomainLegacyMappingTest {

    @Test
    public void shouldReadDocumentWithoutMatcherAsSpiffeUnderItsName() {
        TrustDomainMongo legacy = new TrustDomainMongo();
        legacy.setName("example.org");

        assertEquals("example.org", MongoTrustDomainRepository.readSpiffeTrustDomain(legacy));
    }

    @Test
    public void shouldPreferTheStoredMatcherOverTheName() {
        TrustDomainMongo doc = new TrustDomainMongo();
        doc.setName("acme-corp");
        doc.setSpiffeTrustDomain("acme.org");

        assertEquals("acme.org", MongoTrustDomainRepository.readSpiffeTrustDomain(doc));
    }

    @Test
    public void shouldReadLegacyBundleSourceAsJwksUrlKeyMaterial() {
        TrustDomainMongo legacy = new TrustDomainMongo();
        legacy.setBundleSource("JWKS_URL");
        legacy.setJwksUrl("https://spire.example.org/keys");

        var keyMaterial = MongoTrustDomainRepository.readKeyMaterial(legacy);

        assertEquals(KeyMaterialSource.JWKS_URL, keyMaterial.getSource());
        assertEquals("https://spire.example.org/keys", keyMaterial.getJwksUrl());
    }

    @Test
    public void shouldPreferStoredKeyMaterialOverLegacyFields() {
        TrustDomainMongo doc = new TrustDomainMongo();
        doc.setBundleSource("JWKS_URL");
        doc.setJwksUrl("https://legacy.example.org/keys");
        TrustDomainKeyMaterialMongo keyMaterialMongo = new TrustDomainKeyMaterialMongo();
        keyMaterialMongo.setSource(KeyMaterialSource.PEM.name());
        keyMaterialMongo.setCertificate("cert");
        doc.setKeyMaterial(keyMaterialMongo);

        var keyMaterial = MongoTrustDomainRepository.readKeyMaterial(doc);

        assertEquals(KeyMaterialSource.PEM, keyMaterial.getSource());
        assertEquals("cert", keyMaterial.getCertificate());
        assertNull(keyMaterial.getJwksUrl());
    }

    @Test
    public void shouldReadNoKeyMaterial_whenDocumentHasNeither() {
        assertNull(MongoTrustDomainRepository.readKeyMaterial(new TrustDomainMongo()));
    }
}
