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

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.reactivex.rxjava3.core.Flowable;
import org.bson.Document;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Assertions over the indexes a repository actually created in MongoDB.
 * <p>
 * Repositories declare their indexes in {@code @PostConstruct} hooks that delegate to
 * {@link MongoUtils#createIndex}, which subscribes without blocking and only logs failures. A node
 * whose index creation failed still starts and reports itself healthy, so reading the index
 * catalogue back out of the database is the only way to know the declaration took effect.
 *
 * @author GraviteeSource Team
 */
public final class MongoIndexAssertions {

    /** Index creation is asynchronous and internally retried, so poll rather than assert immediately. */
    private static final Duration INDEX_CREATION_TIMEOUT = Duration.ofSeconds(30);

    private MongoIndexAssertions() {
    }

    /**
     * Asserts that {@code collection} carries a TTL index of the given name, built on {@code field},
     * expiring records after {@code ttl}.
     * <p>
     * A TTL index is frequently the only mechanism removing expired records from a collection on
     * MongoDB - the scheduled purge service is enabled for JDBC deployments only - so losing one
     * means unbounded growth with nothing failing anywhere.
     */
    public static void assertTtlIndex(MongoDatabase database, String collection, String indexName, String field, Duration ttl) {
        Document index = awaitIndex(database, collection, indexName,
                "expired records will never be removed");

        assertThat(index.get("key"))
                .as("TTL index '%s' of '%s' is built on unexpected fields", indexName, collection)
                .isEqualTo(new Document(field, 1));
        assertThat(expireAfterSeconds(index))
                .as("TTL index '%s' of '%s' expires records after the wrong delay", indexName, collection)
                .isEqualTo(ttl.toSeconds());
    }

    /** Asserts that {@code collection} carries an index of the given name over exactly {@code key}. */
    public static void assertIndex(MongoDatabase database, String collection, String indexName, Document key) {
        Document index = awaitIndex(database, collection, indexName, "queries on it will fall back to a collection scan");

        assertThat(index.get("key"))
                .as("index '%s' of '%s' is built on unexpected fields", indexName, collection)
                .isEqualTo(key);
    }

    /**
     * Asserts that {@code collection} carries a unique index of the given name over exactly
     * {@code key}. Uniqueness is asserted explicitly because losing it degrades silently into
     * duplicate records rather than into an error.
     */
    public static void assertUniqueIndex(MongoDatabase database, String collection, String indexName, Document key) {
        assertIndex(database, collection, indexName, key);

        Document index = findIndex(database, collection, indexName);
        assertThat(index.getBoolean("unique", false))
                .as("index '%s' of '%s' is no longer unique - duplicates can now be stored", indexName, collection)
                .isTrue();
    }

    private static Document awaitIndex(MongoDatabase database, String collection, String indexName, String consequence) {
        AtomicReference<Document> found = new AtomicReference<>();
        await().atMost(INDEX_CREATION_TIMEOUT).untilAsserted(() -> {
            Document index = findIndex(database, collection, indexName);
            assertThat(index)
                    .as("collection '%s' has no index '%s' - %s", collection, indexName, consequence)
                    .isNotNull();
            found.set(index);
        });
        return found.get();
    }

    private static Document findIndex(MongoDatabase database, String collection, String indexName) {
        return listIndexes(database, collection).stream()
                .filter(index -> indexName.equals(index.getString("name")))
                .findFirst()
                .orElse(null);
    }

    private static List<Document> listIndexes(MongoDatabase database, String collection) {
        return Flowable.fromPublisher(database.getCollection(collection).listIndexes())
                .toList()
                .blockingGet();
    }

    private static long expireAfterSeconds(Document index) {
        Object value = index.get("expireAfterSeconds");
        assertThat(value)
                .as("index '%s' is not a TTL index - it has no expireAfterSeconds", index.getString("name"))
                .isInstanceOf(Number.class);
        return ((Number) value).longValue();
    }
}
