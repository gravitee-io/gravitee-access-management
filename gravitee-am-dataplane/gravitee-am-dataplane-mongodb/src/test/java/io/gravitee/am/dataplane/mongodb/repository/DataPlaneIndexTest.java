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
package io.gravitee.am.dataplane.mongodb.repository;

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.dataplane.api.repository.test.DataPlaneTestConfigurationLoader;
import io.reactivex.rxjava3.core.Flowable;
import org.bson.Document;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Index lifecycle of the Mongo data plane repositories.
 *
 * Covers the TTL index on the five expiring collections and the lookup indexes on devices.
 * Driven by AM-5379 (Zendesk 12927), where a SaaS AM instance became unresponsive through long
 * queries on the devices collection and the fix was to add indexes — none of which are asserted
 * anywhere. AM-7524 excludes these five collections on the grounds that no TTL declaration had
 * changed in twelve months; that rationale does not cover the 12927 failure mode, which was
 * missing lookup indexes rather than a TTL problem.
 *
 * ensureIndexOnStart resolution for this scope is covered by DataPlaneEnsureIndexOnStartResolutionTest
 * and is not repeated here.
 *
 * Field names below are written as literals rather than taken from the repositories' FIELD_*
 * constants. They are all private, but literals are the better choice regardless: the field name is
 * the on-disk contract, so a test that followed the constant would silently accept a rename that
 * stops existing deployments expiring their data.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataPlaneTestConfigurationLoader.class}, loader = AnnotationConfigContextLoader.class)
public class DataPlaneIndexTest {

    /** Collection -> the field its TTL index is keyed on. */
    private static final Map<String, String> TTL_FIELD_BY_COLLECTION = new LinkedHashMap<>() {{
        put("devices", "expires_at");
        put("scope_approvals", "expiresAt");
        put("user_activities", "expireAt");
        put("login_attempts", "expireAt");
        put("uma_permission_ticket", "expireAt");
    }};

    private static final String TTL_INDEX = "e1";
    private static final long AWAIT_TIMEOUT_MS = 20_000;

    @Autowired
    private MongoDatabase mongoDatabase;

    @Test
    public void everyExpiringCollectionSelfCleans() throws Exception {
        for (String collection : TTL_FIELD_BY_COLLECTION.keySet()) {
            awaitIndex(collection, TTL_INDEX);

            Document index = indexesByName(collection).get(TTL_INDEX);
            assertNotNull(collection + " carries no TTL index named " + TTL_INDEX, index.get("expireAfterSeconds"));
            assertEquals("wrong expireAfterSeconds on " + collection,
                    0L, ((Number) index.get("expireAfterSeconds")).longValue());
        }
    }

    @Test
    public void eachTtlIndexIsKeyedOnTheFieldItsQueriesFilterOn() throws Exception {
        for (Map.Entry<String, String> entry : TTL_FIELD_BY_COLLECTION.entrySet()) {
            String collection = entry.getKey();
            awaitIndex(collection, TTL_INDEX);

            assertKeyEquals(collection + "." + TTL_INDEX,
                    new Document(entry.getValue(), 1),
                    indexesByName(collection).get(TTL_INDEX).get("key", Document.class));
        }
    }

    @Test
    public void theRemainingExpiringCollectionsCarryTheirLookupIndexes() throws Exception {
        Map<String, Map<String, Document>> expected = new LinkedHashMap<>();

        Map<String, Document> userActivities = new LinkedHashMap<>();
        userActivities.put("rt1ri1", referenceTypeFirst());
        userActivities.put("rt1ri1uat1uak1", referenceTypeFirst()
                .append("userActivityType", 1).append("userActivityKey", 1));
        userActivities.put("c1", new Document("createdAt", 1));
        expected.put("user_activities", userActivities);

        Map<String, Document> loginAttempts = new LinkedHashMap<>();
        loginAttempts.put("d1c1u1", new Document("domain", 1).append("client", 1).append("username", 1));
        expected.put("login_attempts", loginAttempts);

        Map<String, Document> scopeApprovals = new LinkedHashMap<>();
        scopeApprovals.put("t1", new Document("transactionId", 1));
        scopeApprovals.put("d1u1", new Document("domain", 1).append("userId", 1));
        scopeApprovals.put("d1c1u1s1", new Document("domain", 1)
                .append("clientId", 1).append("userId", 1).append("scope", 1));
        scopeApprovals.put("d1ue1us1", new Document("domain", 1)
                .append("userExternalId", 1).append("userSource", 1));
        scopeApprovals.put("d1c1ue1us1s1", new Document("domain", 1)
                .append("clientId", 1).append("userExternalId", 1).append("userSource", 1).append("scope", 1));
        expected.put("scope_approvals", scopeApprovals);

        for (Map.Entry<String, Map<String, Document>> collection : expected.entrySet()) {
            for (String name : collection.getValue().keySet()) {
                awaitIndex(collection.getKey(), name);
            }
            Map<String, Document> actual = indexesByName(collection.getKey());
            collection.getValue().forEach((name, key) ->
                    assertKeyEquals(collection.getKey() + "." + name, key,
                            actual.get(name).get("key", Document.class)));
        }
    }

    @Test
    public void theDeviceStoreCarriesItsLookupIndexes() throws Exception {
        Map<String, Document> expected = new HashMap<>();
        expected.put("ri1rt1", reference());
        expected.put("ri1rt1u1", reference().append("userId", 1));
        expected.put("ri1rt1c1d1", reference().append("client", 1).append("deviceId", 1));
        expected.put("ri1rt1c1u1di1d1", reference()
                .append("client", 1).append("userId", 1).append("deviceIdentifierId", 1).append("deviceId", 1));

        for (String name : expected.keySet()) {
            awaitIndex("devices", name);
        }

        Map<String, Document> actual = indexesByName("devices");
        expected.forEach((name, key) ->
                assertKeyEquals("devices." + name, key, actual.get(name).get("key", Document.class)));
    }

    // ---------------------------------------------------------------- helpers

    /** devices keys referenceId first. */
    private Document reference() {
        return new Document("referenceId", 1).append("referenceType", 1);
    }

    /** user_activities keys referenceType first — the opposite order to devices. */
    private Document referenceTypeFirst() {
        return new Document("referenceType", 1).append("referenceId", 1);
    }

    /**
     * Compares index keys by ordered entries. Document.equals() delegates to Map.equals() and is
     * order-insensitive, but for a compound index the field order is the semantics — a swapped
     * prefix is a different index.
     */
    private static void assertKeyEquals(String indexName, Document expected, Document actual) {
        assertEquals("wrong key for index " + indexName,
                new ArrayList<>(expected.entrySet()),
                new ArrayList<>(actual.entrySet()));
    }

    private Map<String, Document> indexesByName(String collection) {
        Map<String, Document> byName = new HashMap<>();
        Flowable.fromPublisher(mongoDatabase.getCollection(collection).listIndexes())
                .blockingForEach(index -> byName.put(index.getString("name"), index));
        return byName;
    }

    /**
     * Repositories create their indexes from @PostConstruct via MongoUtils.createIndex, which
     * subscribes without blocking, so poll rather than assume they have landed.
     */
    private void awaitIndex(String collection, String indexName) throws Exception {
        Predicate<Map<String, Document>> present = byName -> byName.containsKey(indexName);
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        Map<String, Document> byName = Map.of();
        while (System.currentTimeMillis() < deadline) {
            byName = indexesByName(collection);
            if (present.test(byName)) {
                return;
            }
            Thread.sleep(200);
        }
        fail("index " + indexName + " never appeared on " + collection + " (present: " + byName.keySet() + ")");
    }
}
