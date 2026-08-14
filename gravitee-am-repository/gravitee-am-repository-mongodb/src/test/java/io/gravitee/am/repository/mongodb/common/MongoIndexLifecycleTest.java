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

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.mongodb.MongodbProvider;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Covers what happens to indexes on the paths a clean start never exercises: restarting against a
 * collection that is already indexed, and removing indexes that a previous version left behind.
 * <p>
 * These are the paths an upgrade actually takes. {@link MongoUtils#dropIndexes} in particular is
 * destructive, carries its own retry and already-removed handling, and is used to sweep obsolete
 * indexes on startup - so its behaviour is worth pinning.
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

    // --- helpers ----------------------------------------------------------------------------

    private static MongoCollection<Document> collection(String name) {
        return database.getCollection(name, Document.class);
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
}
