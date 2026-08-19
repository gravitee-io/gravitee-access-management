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
package io.gravitee.am.reporter.mongodb.audit;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.reporter.mongodb.MongoReporterConfiguration;
import io.reactivex.rxjava3.core.Flowable;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;


import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_ACTOR;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_ID;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_REFERENCE_ID;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_REFERENCE_TYPE;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_ACTOR_ID;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_STATUS;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_TARGET;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_TARGET_ID;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_TYPE;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.FIELD_TIMESTAMP;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_ACTOR_ID_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_TARGET_ID_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_TARGET_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_TYPE_STATUS_SUCCESS_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_REFERENCE_TYPE_TIMESTAMP_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.INDEX_TIMESTAMP_ID_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME_SHORT_NAME;
import static io.gravitee.am.reporter.mongodb.audit.constants.MongoAuditReporterConstants.OLD_INDICES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Index lifecycle of the Mongo audit reporter.
 *
 * Covers the retired-index sweep, the current index set and its definitions, and the two
 * independent ensureIndexOnStart switches. Driven by AM-7454 / AM-7457, where the retired
 * index r1a1ta1t_1 survived an upgrade and left security domains failing to sync.
 *
 * The management-node gate is covered by MongoAuditReporterTest and is not repeated here.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {MongoReporterJUnitConfiguration.class}, loader = AnnotationConfigContextLoader.class)
// Matches MongoAuditReporterTest so both classes share one cached Spring context, and so the
// module starts a single Mongo testcontainer. startReporter() sets purgeEnabled and
// retentionDays explicitly, so these values never reach an assertion.
@TestPropertySource(properties = {
    "services.purge.enabled=true",
    "services.purge.audits.retention.days=90"
})
public class MongoAuditReporterIndexTest {

    /** Indexes the reporter declares unconditionally. */
    private static final List<String> CURRENT_INDEXES = List.of(
            INDEX_REFERENCE_TIMESTAMP_NAME,
            INDEX_REFERENCE_TYPE_TIMESTAMP_NAME,
            INDEX_REFERENCE_TYPE_STATUS_SUCCESS_TIMESTAMP_NAME,
            INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME,
            INDEX_REFERENCE_ACTOR_ID_TIMESTAMP_NAME,
            INDEX_REFERENCE_TARGET_TIMESTAMP_NAME,
            INDEX_REFERENCE_TARGET_ID_TIMESTAMP_NAME);

    private static final long AWAIT_TIMEOUT_MS = 20_000;
    private static final long SETTLE_MS = 3_000;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MongoClient mongoClient;

    private String collection;

    @Before
    public void setUp() {
        collection = "audits_idx_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    public void theIndexLeftBehindByTheUpgradeIsRemovedOnStartUp() throws Exception {
        seedIndex(OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME_SHORT_NAME, historicalActorTargetKey());

        startReporter(reporter -> {});

        awaitIndexes(names -> !names.contains(OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME_SHORT_NAME),
                "retired index " + OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME_SHORT_NAME + " was not removed");
    }

    @Test
    public void everyRetiredNameIsSweptNotOnlyTheOneFromTheIncident() throws Exception {
        // OLD_INDICES lists ref_1_time_-1 twice, so seed distinct names only
        List<String> retired = OLD_INDICES.stream().distinct().toList();
        for (int i = 0; i < retired.size(); i++) {
            seedIndex(retired.get(i), new Document("seeded_" + i, 1));
        }

        startReporter(reporter -> {});

        awaitIndexes(names -> OLD_INDICES.stream().noneMatch(names::contains),
                "at least one retired index survived the sweep");
    }

    @Test
    public void theCurrentIndexSetLandsAfterTheSweep() throws Exception {
        seedIndex(OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME_SHORT_NAME, historicalActorTargetKey());

        startReporter(reporter -> {});

        awaitIndexes(names -> names.containsAll(CURRENT_INDEXES),
                "the current index set did not land");

        Map<String, Document> byName = indexesByName();
        expectedKeys().forEach((name, key) ->
                assertKeyEquals(name, key, byName.get(name).get("key", Document.class)));
    }

    @Test
    public void theSuccessOnlyIndexIsRestrictedToSuccessfulAudits() throws Exception {
        startReporter(reporter -> {});

        awaitIndexes(names -> names.contains(INDEX_REFERENCE_TYPE_STATUS_SUCCESS_TIMESTAMP_NAME),
                "the success-only index was not created");

        Document index = indexesByName().get(INDEX_REFERENCE_TYPE_STATUS_SUCCESS_TIMESTAMP_NAME);
        Document filter = index.get("partialFilterExpression", Document.class);
        assertNotNull("the success-only index carries no partial filter", filter);
        assertEquals(new Document("$eq", "SUCCESS"), filter.get(FIELD_STATUS));
    }

    @Test
    public void theRetentionIndexIsCreatedWhenAuditRetentionIsConfigured() throws Exception {
        startReporter(reporter -> {
            ReflectionTestUtils.setField(reporter, "purgeEnabled", true);
            ReflectionTestUtils.setField(reporter, "retentionDays", 90);
        });

        awaitIndexes(names -> names.contains(INDEX_TIMESTAMP_ID_NAME),
                "the retention index was not created");

        Document key = indexesByName().get(INDEX_TIMESTAMP_ID_NAME).get("key", Document.class);
        assertKeyEquals(INDEX_TIMESTAMP_ID_NAME, new Document(FIELD_TIMESTAMP, 1).append(FIELD_ID, 1), key);
    }

    @Test
    public void theRetentionIndexIsAbsentWhenRetentionIsNotConfigured() throws Exception {
        startReporter(reporter -> ReflectionTestUtils.setField(reporter, "purgeEnabled", false));

        awaitIndexes(names -> names.containsAll(CURRENT_INDEXES),
                "the current index set did not land");

        assertFalse("the retention index was created without retention configured",
                indexNames().contains(INDEX_TIMESTAMP_ID_NAME));
    }

    @Test
    public void theReportersOwnSwitchSuppressesAllIndexWork() throws Exception {
        seedIndex(OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME_SHORT_NAME, historicalActorTargetKey());

        startReporter(reporter -> ReflectionTestUtils.setField(reporter, "reporterEnsureIndexOnStart", false));

        Thread.sleep(SETTLE_MS);
        Set<String> names = indexNames();
        assertTrue("the retired index was swept despite the reporter switch being off",
                names.contains(OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME_SHORT_NAME));
        assertTrue("indexes were created despite the reporter switch being off",
                CURRENT_INDEXES.stream().noneMatch(names::contains));
    }

    @Test
    public void theSharedRepositorySwitchAlsoSuppressesAllIndexWork() throws Exception {
        seedIndex(OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME_SHORT_NAME, historicalActorTargetKey());

        startReporter(reporter -> ReflectionTestUtils.setField(reporter, "globalEnsureIndexOnStart", false));

        Thread.sleep(SETTLE_MS);
        Set<String> names = indexNames();
        assertTrue("the retired index was swept despite the repository switch being off",
                names.contains(OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME_SHORT_NAME));
        assertTrue("indexes were created despite the repository switch being off",
                CURRENT_INDEXES.stream().noneMatch(names::contains));
    }

    // ---------------------------------------------------------------- helpers


    /**
     * Starts a reporter against this test's own collection. Both switches default to on and purge
     * to off; a scenario overrides only what it is about.
     */
    private MongoAuditReporter startReporter(Consumer<MongoAuditReporter> overrides) throws Exception {
        MongoReporterConfiguration configuration = new MongoReporterConfiguration();
        configuration.setDatabase(MongoReporterJUnitConfiguration.DATABASE);
        configuration.setReportableCollection(collection);
        configuration.setBulkActions(1000);
        configuration.setFlushInterval(1L);

        MongoAuditReporter reporter = new MongoAuditReporter();
        context.getAutowireCapableBeanFactory().autowireBean(reporter);
        ReflectionTestUtils.setField(reporter, "configuration", configuration);
        ReflectionTestUtils.setField(reporter, "globalEnsureIndexOnStart", true);
        ReflectionTestUtils.setField(reporter, "reporterEnsureIndexOnStart", true);
        ReflectionTestUtils.setField(reporter, "purgeEnabled", false);
        ReflectionTestUtils.setField(reporter, "retentionDays", 0);
        overrides.accept(reporter);
        reporter.afterPropertiesSet();
        // Deliberately not stopped. doStop() calls clientWrapper.releaseClient(), and
        // TestMongoConnectionProvider hands every caller the same wrapper, so the first release
        // drops the shared client's reference count to zero and shuts it down for the rest of the
        // suite. The cost is a leaked bulkProcessor subscription per reporter, which is the lesser
        // evil. A real fix belongs in TestMongoConnectionProvider (a wrapper per reporter).
        return reporter;
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

    private void seedIndex(String name, Document key) {
        Flowable.fromPublisher(rawCollection().createIndex(key, new IndexOptions().name(name)))
                .blockingSubscribe();
    }

    private MongoCollection<Document> rawCollection() {
        return mongoClient.getDatabase(MongoReporterJUnitConfiguration.DATABASE).getCollection(collection);
    }

    private Map<String, Document> indexesByName() {
        Map<String, Document> byName = new HashMap<>();
        Flowable.fromPublisher(rawCollection().listIndexes())
                .blockingForEach(index -> byName.put(index.getString("name"), index));
        return byName;
    }

    private Set<String> indexNames() {
        return new HashSet<>(indexesByName().keySet());
    }

    /**
     * Index creation is fire-and-forget inside the reporter, so poll rather than sleep for a
     * fixed period.
     */
    private void awaitIndexes(Predicate<Set<String>> condition, String message) throws Exception {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        Set<String> names = Set.of();
        while (System.currentTimeMillis() < deadline) {
            names = indexNames();
            if (condition.test(names)) {
                return;
            }
            Thread.sleep(200);
        }
        fail(message + " (indexes present: " + names + ")");
    }

    /** The definition r1a1ta1t_1 carried before it was retired. */
    private Document historicalActorTargetKey() {
        return new Document(FIELD_REFERENCE_TYPE, 1)
                .append(FIELD_REFERENCE_ID, 1)
                .append(FIELD_ACTOR, 1)
                .append(FIELD_TARGET, 1)
                .append(FIELD_TIMESTAMP, -1);
    }

    /** The key each unconditional index declares, by name. */
    private Map<String, Document> expectedKeys() {
        Map<String, Document> keys = new HashMap<>();
        keys.put(INDEX_REFERENCE_TIMESTAMP_NAME, reference().append(FIELD_TIMESTAMP, -1));
        keys.put(INDEX_REFERENCE_TYPE_TIMESTAMP_NAME, reference().append(FIELD_TYPE, 1).append(FIELD_TIMESTAMP, -1));
        keys.put(INDEX_REFERENCE_TYPE_STATUS_SUCCESS_TIMESTAMP_NAME,
                reference().append(FIELD_TYPE, 1).append(FIELD_STATUS, 1).append(FIELD_TIMESTAMP, -1));
        keys.put(INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME, reference().append(FIELD_ACTOR, 1).append(FIELD_TIMESTAMP, -1));
        keys.put(INDEX_REFERENCE_ACTOR_ID_TIMESTAMP_NAME, reference().append(FIELD_ACTOR_ID, 1).append(FIELD_TIMESTAMP, -1));
        keys.put(INDEX_REFERENCE_TARGET_TIMESTAMP_NAME, reference().append(FIELD_TARGET, 1).append(FIELD_TIMESTAMP, -1));
        keys.put(INDEX_REFERENCE_TARGET_ID_TIMESTAMP_NAME, reference().append(FIELD_TARGET_ID, 1).append(FIELD_TIMESTAMP, -1));
        return keys;
    }

    private Document reference() {
        return new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1);
    }
}
