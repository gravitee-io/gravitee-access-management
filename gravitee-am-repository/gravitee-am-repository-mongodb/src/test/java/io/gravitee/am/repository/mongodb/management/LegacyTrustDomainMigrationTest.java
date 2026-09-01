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

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.SpiffeTrustSettings;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.model.oidc.TrustedDomain;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.core.Completable;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Date;
import java.util.List;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author GraviteeSource Team
 */
public class LegacyTrustDomainMigrationTest extends AbstractManagementTest {

    private static final String LEGACY_COLLECTION = "trust_domains";
    private static final String COLLECTION = "trusted_domains";

    @Autowired
    private MongoTrustedDomainRepository repository;

    @Autowired
    @Qualifier("managementMongoTemplate")
    private MongoDatabase mongoDatabase;

    @Before
    public void emptyCollections() {
        Completable.fromPublisher(mongoDatabase.getCollection(LEGACY_COLLECTION).drop()).blockingAwait();
        Completable.fromPublisher(mongoDatabase.getCollection(COLLECTION).deleteMany(new Document())).blockingAwait();
    }

    @Test
    public void shouldMigrateAJwksUrlTrustDomainAsSpiffe() {
        insertLegacy(legacyDocument("legacy-1", "acme.org")
                .append("bundleSource", "JWKS_URL")
                .append("jwksUrl", "https://spire.acme.org/keys")
                .append("refreshIntervalSeconds", 120)
                .append("allowedAlgorithms", List.of("RS256")));

        migrate();

        TrustedDomain migrated = repository.findById("legacy-1").blockingGet();
        assertEquals("acme.org", migrated.getName());
        assertEquals("desc", migrated.getDescription());
        SpiffeTrustSettings spiffe = migrated.getSpiffe();
        assertEquals("acme.org", spiffe.getSpiffeTrustDomain());
        assertEquals(List.of("RS256"), spiffe.getAllowedAlgorithms());
        TrustDomainKeyMaterial keyMaterial = migrated.getKeyMaterial();
        assertEquals(KeyMaterialSource.JWKS_URL, keyMaterial.getSource());
        assertEquals("https://spire.acme.org/keys", keyMaterial.getJwksUrl());
        assertEquals(Integer.valueOf(120), keyMaterial.getRefreshIntervalSeconds());
        assertNull(migrated.getTokenExchange());
        assertNull(migrated.getCrossAppAccess());
    }

    @Test
    public void shouldMigrateAStaticJwksTrustDomainAsAJwkSetSource() {
        insertLegacy(legacyDocument("legacy-2", "static.org")
                .append("bundleSource", "STATIC_JWKS")
                .append("refreshIntervalSeconds", 300));

        migrate();

        TrustDomainKeyMaterial keyMaterial = repository.findById("legacy-2").blockingGet().getKeyMaterial();
        assertEquals(KeyMaterialSource.JWK_SET, keyMaterial.getSource());
        assertNull(keyMaterial.getJwksUrl());
    }

    @Test
    public void shouldMigrateATrustDomainWithoutKeyMaterial() {
        insertLegacy(legacyDocument("legacy-3", "bare.org"));

        migrate();

        TrustedDomain migrated = repository.findById("legacy-3").blockingGet();
        assertNull(migrated.getKeyMaterial());
        assertEquals("bare.org", migrated.getSpiffe().getSpiffeTrustDomain());
    }

    @Test
    public void shouldMakeTheMigratedTrustDomainFindableBySpiffeTrustDomain() {
        insertLegacy(legacyDocument("legacy-4", "findable.org"));

        migrate();

        TrustedDomain found = repository.findBySpiffeTrustDomain(DOMAIN, "domain-1", "findable.org").blockingGet();
        assertNotNull(found);
        assertEquals("legacy-4", found.getId());
    }

    @Test
    public void shouldNotMigrateADocumentDeclaringAnIssuer() {
        insertLegacy(legacyDocument("legacy-5", "issuer.example").append("issuer", "https://issuer.example"));

        migrate();

        assertNull(repository.findById("legacy-5").blockingGet());
    }

    @Test
    public void shouldNotOverwriteATrustedDomainEditedSinceTheMigration() {
        insertLegacy(legacyDocument("legacy-6", "edited.org").append("bundleSource", "JWKS_URL"));
        migrate();

        TrustedDomain migrated = repository.findById("legacy-6").blockingGet();
        migrated.setDescription("edited after the migration");
        migrated.setSpiffe(SpiffeTrustSettings.builder().spiffeTrustDomain("renamed.org").build());
        repository.update(migrated).blockingGet();

        migrate();

        TrustedDomain reread = repository.findById("legacy-6").blockingGet();
        assertEquals("edited after the migration", reread.getDescription());
        assertEquals("renamed.org", reread.getSpiffe().getSpiffeTrustDomain());
    }

    private void migrate() {
        new LegacyTrustDomainMigration(mongoDatabase, COLLECTION).run().blockingAwait();
    }

    private Document legacyDocument(String id, String name) {
        return new Document("_id", id)
                .append("referenceId", "domain-1")
                .append("referenceType", DOMAIN.name())
                .append("name", name)
                .append("description", "desc")
                .append("createdAt", new Date())
                .append("updatedAt", new Date());
    }

    private void insertLegacy(Document document) {
        Completable.fromPublisher(mongoDatabase.getCollection(LEGACY_COLLECTION).insertOne(document)).blockingAwait();
    }
}
