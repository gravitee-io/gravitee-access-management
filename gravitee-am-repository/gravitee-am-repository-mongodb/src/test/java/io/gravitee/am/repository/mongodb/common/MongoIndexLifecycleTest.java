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
package io.gravitee.am.repository.mongodb.common;

import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.mongodb.MongodbProvider;
import io.gravitee.am.repository.mongodb.common.MongoIndexReport.Status;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Covers what happens to indexes on the paths a clean start never exercises: restarting against a
 * collection that is already indexed, removing indexes that a previous version left behind, and
 * reconciling a collection whose indexes have drifted away from what the repositories declare.
 * <p>
 * These are the paths an upgrade actually takes. {@link MongoUtils#dropIndexes} in particular is
 * destructive, carries its own retry and already-removed handling, and is used to sweep obsolete
 * indexes on startup - so its behavior is worth pinning.
 *
 * @author GraviteeSource Team
 */
public class MongoIndexLifecycleTest {

    private static final Duration INDEX_CREATION_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_ID_INDEX = "_id_";

    private static MongodbProvider provider;
    private static MongoDatabase database;

    @BeforeClass
    public static void startMongo() throws Exception {
        provider = new MongodbProvider("test-am-index-lifecycle");
        provider.afterPropertiesSet();
        database = provider.mongoDatabase();
    }

    @AfterClass
    public static void stopMongo() throws Exception {
        if (provider != null) {
            provider.destroy();
        }
    }

    @Before
    public void resetReport() {
        MongoIndexReport.clear();
    }

    /**
     * Every restart re-runs the repositories' {@code @PostConstruct} index declarations against a
     * collection that is already indexed. That must leave the collection correctly indexed and must
     * not accumulate duplicates.
     */
    @Test
    public void reDeclaringTheSameIndexesLeavesTheCollectionUnchanged() {
        MongoCollection<Document> collection = collection("restart");
        Map<Document, IndexOptions> declared = new LinkedHashMap<>();
        declared.put(new Document("token", 1), new IndexOptions().name("t1"));
        declared.put(new Document("expire_at", 1), new IndexOptions().name("e1"));

        MongoUtils.createIndex(collection, declared, true);
        awaitIndexes(collection, DEFAULT_ID_INDEX, "t1", "e1");

        // simulate the next start-up of the same version
        MongoUtils.createIndex(collection, declared, true);
        awaitIndexes(collection, DEFAULT_ID_INDEX, "t1", "e1");

        assertThat(indexNames(collection))
                .as("re-declaring the same indexes must not create duplicates")
                .containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "t1", "e1");
    }

    @Test
    public void createIndexDoesNothingWhenEnsureIsDisabled() {
        MongoCollection<Document> collection = collection("disabled");
        seedIndex(collection, "keep_me", "kept");

        MongoUtils.createIndex(collection, Map.of(new Document("token", 1), new IndexOptions().name("t1")), false);

        assertThat(indexNames(collection))
                .as("no index should be created when ensureIndexOnStart is off")
                .containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "keep_me");
    }

    @Test
    public void dropIndexesRemovesOnlyTheMatchingIndexes() {
        MongoCollection<Document> collection = collection("selective_drop");
        seedIndex(collection, "obsolete1", "old_a");
        seedIndex(collection, "obsolete2", "old_b");
        seedIndex(collection, "current", "still_used");

        MongoUtils.dropIndexes(collection, name -> name.startsWith("obsolete")).blockingAwait();

        assertThat(indexNames(collection))
                .as("only the matching indexes should be dropped")
                .containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "current");
    }

    @Test
    public void dropIndexesLeavesTheCollectionAloneWhenNothingMatches() {
        MongoCollection<Document> collection = collection("no_match");
        seedIndex(collection, "current", "still_used");

        MongoUtils.dropIndexes(collection, name -> false).blockingAwait();

        assertThat(indexNames(collection)).containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "current");
    }

    /**
     * A collection that does not exist yet lists no indexes at all. The sweep still has to complete
     * - a start-up that swept a not-yet-created collection must not hang or fail.
     */
    @Test
    public void dropIndexesCompletesOnACollectionThatDoesNotExistYet() {
        MongoCollection<Document> collection = collection("never_created");

        MongoUtils.dropIndexes(collection, name -> true).blockingAwait();

        assertThat(indexNames(collection)).isEmpty();
    }

    /**
     * Two nodes starting together sweep the same obsolete indexes concurrently, so an index can
     * disappear between being listed and being dropped. MongoDB reports that as IndexNotFound
     * (code 27) and the sweep must treat it as success rather than failing the start-up.
     * <p>
     * The race is made deterministic here by removing the index from inside the name matcher -
     * that is, after it has been listed but before the sweep tries to drop it.
     */
    @Test
    public void dropIndexesToleratesAnIndexAlreadyRemovedByAnotherNode() {
        MongoCollection<Document> collection = collection("concurrent_drop");
        seedIndex(collection, "obsolete", "old_a");
        seedIndex(collection, "current", "still_used");

        MongoUtils.dropIndexes(collection, name -> {
            if ("obsolete".equals(name)) {
                dropIndexDirectly(collection, "obsolete");
                return true;
            }
            return false;
        }).blockingAwait();

        assertThat(indexNames(collection))
                .as("a concurrent removal must not fail the sweep")
                .containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "current");
    }

    @Test
    public void anIndexThatDriftedFromItsDeclarationIsRebuilt() {
        MongoCollection<Document> collection = collection("stale_name");
        seedIndex(collection, "e1", "old_expiry_field");

        MongoIndexManager.ensureIndexes(collection, List.of(
                index("t1", new Document("token", 1)),
                ttlIndex("e1", "expire_at"),
                index("s1", new Document("subject", 1)))).blockingAwait();

        assertThat(indexNames(collection))
                .as("one conflicting index must not suppress the rest of the collection")
                .containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "t1", "e1", "s1");

        Document rebuilt = indexesByName(collection).get("e1");
        assertThat(rebuilt.get("key")).isEqualTo(new Document("expire_at", 1));
        assertThat(rebuilt.get("expireAfterSeconds"))
                .as("the rebuilt index must expire records, or nothing ever removes them")
                .isNotNull();
        assertThat(statusOf(collection, "e1")).isEqualTo(Status.REBUILT);
        assertThat(MongoIndexReport.failures()).isEmpty();
    }

    @Test
    public void aForeignIndexHoldingADeclaredKeyIsLeftInPlace() {
        MongoCollection<Document> collection = collection("foreign_key");
        seedIndex(collection, "legacy_token_idx", "token");

        MongoIndexManager.ensureIndexes(collection, List.of(
                index("t1", new Document("token", 1)),
                index("s1", new Document("subject", 1)))).blockingAwait();

        assertThat(indexNames(collection))
                .containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "legacy_token_idx", "s1");
        assertThat(statusOf(collection, "t1")).isEqualTo(Status.SHADOWED);
        assertThat(statusOf(collection, "s1")).isEqualTo(Status.ENSURED);
        assertThat(MongoIndexReport.failures())
                .as("the key is covered, so nothing is actually lost")
                .isEmpty();
    }

    @Test
    public void aForeignIndexThatCannotExpireRecordsIsReportedAsAFailure() {
        MongoCollection<Document> collection = collection("foreign_ttl");
        seedIndex(collection, "operator_expiry", "expire_at");

        MongoIndexManager.ensureIndexes(collection, List.of(ttlIndex("e1", "expire_at"))).blockingAwait();

        assertThat(indexNames(collection)).containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "operator_expiry");
        assertThat(statusOf(collection, "e1")).isEqualTo(Status.FAILED);
        assertThat(MongoIndexReport.failures()).hasSize(1);
        assertThat(MongoIndexReport.failures().get(0).detail())
                .contains("expired records will never be removed");
    }

    @Test
    public void anIndexMongoRefusesToBuildDoesNotSuppressTheOthers() {
        MongoCollection<Document> collection = collection("unique_conflict");
        insert(collection, new Document("username", "duplicate"), new Document("username", "duplicate"));

        MongoIndexManager.ensureIndexes(collection, List.of(
                uniqueIndex("u1", new Document("username", 1)),
                index("t1", new Document("token", 1)),
                index("s1", new Document("subject", 1)))).blockingAwait();

        assertThat(indexNames(collection))
                .as("an index the server will not build must cost only itself")
                .containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "t1", "s1");
        assertThat(statusOf(collection, "u1")).isEqualTo(Status.FAILED);
        assertThat(MongoIndexReport.failures()).hasSize(1);
    }

    /** The restart after the one that repaired the collection must be an ordinary no-op. */
    @Test
    public void reconciliationIsIdempotent() {
        MongoCollection<Document> collection = collection("idempotent_reconcile");
        seedIndex(collection, "e1", "old_expiry_field");
        List<IndexModel> declared = List.of(index("t1", new Document("token", 1)), ttlIndex("e1", "expire_at"));

        MongoIndexManager.ensureIndexes(collection, declared).blockingAwait();
        List<String> afterRepair = indexNames(collection);

        MongoIndexReport.clear();
        MongoIndexManager.ensureIndexes(collection, declared).blockingAwait();

        assertThat(indexNames(collection)).containsExactlyInAnyOrderElementsOf(afterRepair);
        assertThat(MongoIndexReport.outcomes(collection.getNamespace().getFullName()))
                .as("a settled collection must need no further repair")
                .allMatch(outcome -> outcome.status() == Status.ENSURED);
    }

    /** Two nodes starting together reconcile the same collection; both must settle on the declaration. */
    @Test
    public void concurrentReconciliationsBothSettle() {
        MongoCollection<Document> collection = collection("concurrent_reconcile");
        seedIndex(collection, "e1", "old_expiry_field");
        List<IndexModel> declared = List.of(index("t1", new Document("token", 1)), ttlIndex("e1", "expire_at"));

        Completable.mergeArray(
                MongoIndexManager.ensureIndexes(collection, declared).subscribeOn(Schedulers.io()),
                MongoIndexManager.ensureIndexes(collection, declared).subscribeOn(Schedulers.io()))
                .blockingAwait();

        assertThat(indexNames(collection)).containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "t1", "e1");
    }

    @Test
    public void twoDeclarationsOverOneKeyCostOnlyTheDuplicate() {
        MongoCollection<Document> collection = collection("duplicate_declaration");

        MongoIndexManager.ensureIndexes(collection, List.of(
                index("r1a1t_1", new Document("referenceType", 1).append("actor", 1)),
                index("r1ai1t_1", new Document("referenceType", 1).append("actor", 1)),
                index("t1", new Document("token", 1)))).blockingAwait();

        assertThat(indexNames(collection))
                .containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "r1a1t_1", "t1");
        assertThat(statusOf(collection, "r1ai1t_1"))
                .as("the key is covered by the first declaration, so nothing is lost by dropping the second")
                .isEqualTo(Status.SHADOWED);
    }

    @Test
    public void aUniqueDeclarationIsCreatedEvenWhereAPlainIndexCoversItsKey() {
        MongoCollection<Document> collection = collection("unique_alongside_foreign");
        seedIndex(collection, "operator_username", "username");
        seedIndex(collection, "s1", "a_previous_field");

        MongoIndexManager.ensureIndexes(collection, List.of(
                uniqueIndex("u1", new Document("username", 1)),
                index("s1", new Document("subject", 1)))).blockingAwait();

        assertThat(indexNames(collection))
                .containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "operator_username", "u1", "s1");
        assertThat(statusOf(collection, "u1")).isEqualTo(Status.ENSURED);
        assertThat(MongoIndexReport.failures()).isEmpty();
    }

    @Test
    public void anIndexThatStillMatchesItsDeclarationIsNotRebuilt() {
        MongoCollection<Document> collection = collection("already_matching");
        seedIndex(collection, ttlIndex("e1", "expire_at"));
        seedIndex(collection, "s1", "a_previous_field");

        MongoIndexManager.ensureIndexes(collection, List.of(
                ttlIndex("e1", "expire_at"),
                index("s1", new Document("subject", 1)))).blockingAwait();

        assertThat(statusOf(collection, "e1"))
                .as("an index that already matches its declaration must be left alone")
                .isEqualTo(Status.ENSURED);
        assertThat(statusOf(collection, "s1")).isEqualTo(Status.REBUILT);
        assertThat(indexNames(collection)).containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "e1", "s1");
    }

    @Test
    public void anIndexThatCannotBeRebuiltIsReportedAndTakesTheOldOneWithIt() {
        MongoCollection<Document> collection = collection("rebuild_failure");
        insert(collection, new Document("username", "duplicate"), new Document("username", "duplicate"));
        seedIndex(collection, "u1", "username");

        MongoIndexManager.ensureIndexes(collection, List.of(
                uniqueIndex("u1", new Document("username", 1)),
                index("t1", new Document("token", 1)))).blockingAwait();

        assertThat(statusOf(collection, "u1")).isEqualTo(Status.FAILED);
        assertThat(indexNames(collection))
                .as("the failed rebuild costs its own index and nothing else")
                .containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "t1");
    }

    @Test
    public void aRebuildBlockedByAnEquivalentForeignIndexIsShadowedRatherThanFailed() {
        MongoCollection<Document> collection = collection("rebuild_shadowed");
        seedIndex(collection, "e1", "a_previous_field");
        seedIndex(collection, ttlIndex("operator_expiry", "expire_at"));

        MongoIndexManager.ensureIndexes(collection, List.of(ttlIndex("e1", "expire_at"))).blockingAwait();

        assertThat(statusOf(collection, "e1"))
                .as("retention is intact under another name, so nothing was lost")
                .isEqualTo(Status.SHADOWED);
        assertThat(MongoIndexReport.failures()).isEmpty();
        assertThat(indexNames(collection)).containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "operator_expiry");
    }

    @Test
    public void aTtlBlockedByAnIndexCreatedInTheSamePassIsNotReportedAsBenign() {
        MongoCollection<Document> collection = collection("ttl_blocked_in_pass");

        MongoIndexManager.ensureIndexes(collection, List.of(
                index("x1", new Document("expire_at", 1)),
                ttlIndex("e1", "expire_at"))).blockingAwait();

        assertThat(indexNames(collection)).containsExactlyInAnyOrder(DEFAULT_ID_INDEX, "x1");
        assertThat(statusOf(collection, "e1")).isEqualTo(Status.FAILED);
        assertThat(MongoIndexReport.failures()).hasSize(1);
    }

    // --- helpers ----------------------------------------------------------------------------

    private static MongoCollection<Document> collection(String name) {
        return database.getCollection(name, Document.class);
    }

    private static void seedIndex(MongoCollection<Document> collection, IndexModel model) {
        Completable.fromPublisher(collection.createIndex(model.getKeys(), model.getOptions())).blockingAwait();
    }

    private static void seedIndex(MongoCollection<Document> collection, String indexName, String field) {
        Completable.fromPublisher(collection.createIndex(new Document(field, 1), new IndexOptions().name(indexName)))
                .blockingAwait();
    }

    private static void dropIndexDirectly(MongoCollection<Document> collection, String indexName) {
        Completable.fromPublisher(collection.dropIndex(indexName)).blockingAwait();
    }

    private static void awaitIndexes(MongoCollection<Document> collection, String... expected) {
        await().atMost(INDEX_CREATION_TIMEOUT)
                .untilAsserted(() -> assertThat(indexNames(collection)).contains(expected));
    }

    private static List<String> indexNames(MongoCollection<Document> collection) {
        return Flowable.fromPublisher(collection.listIndexes())
                .map(index -> index.getString("name"))
                .toList()
                .blockingGet();
    }

    private static IndexModel index(String name, Document key) {
        return new IndexModel(key, new IndexOptions().name(name));
    }

    private static IndexModel uniqueIndex(String name, Document key) {
        return new IndexModel(key, new IndexOptions().name(name).unique(true));
    }

    private static IndexModel ttlIndex(String name, String field) {
        return new IndexModel(new Document(field, 1), new IndexOptions().name(name).expireAfter(0L, TimeUnit.SECONDS));
    }

    private static void insert(MongoCollection<Document> collection, Document... documents) {
        Flowable.fromPublisher(collection.insertMany(List.of(documents))).blockingSubscribe();
    }

    private static Map<String, Document> indexesByName(MongoCollection<Document> collection) {
        return Flowable.fromPublisher(collection.listIndexes())
                .toMap(index -> index.getString("name"))
                .blockingGet();
    }

    private static Status statusOf(MongoCollection<Document> collection, String indexName) {
        return MongoIndexReport.outcomes(collection.getNamespace().getFullName()).stream()
                .filter(outcome -> outcome.index().equals(indexName))
                .map(MongoIndexReport.IndexOutcome::status)
                .findFirst()
                .orElse(null);
    }
}
