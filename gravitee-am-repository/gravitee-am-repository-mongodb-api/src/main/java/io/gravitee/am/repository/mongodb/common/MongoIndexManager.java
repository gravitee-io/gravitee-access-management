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

import com.mongodb.ErrorCategory;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import lombok.experimental.UtilityClass;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Applies a collection's declared indexes to MongoDB, and reconciles the collection with the
 * declaration when they have drifted apart.
 * <p>
 * The rule this applies is that <em>AM owns its index names, not its index keys</em>. An index
 * sitting on a name AM declares is AM's to replace, so it is dropped and rebuilt to match the
 * declaration - this is what makes an upgrade that changes a definition converge. An index sitting
 * on a key AM declares but under somebody else's name is left alone: its coverage is already there,
 * and it may well have been created deliberately by an operator. What AM does not do is stay quiet
 * about either case; see {@link MongoIndexReport}.
 *
 * @author GraviteeSource Team
 */
@UtilityClass
@CustomLog
public final class MongoIndexManager {

    private static final int MAX_ATTEMPTS = 3;

    /** An index with this name already exists, holding a different definition. */
    private static final int INDEX_KEY_SPECS_CONFLICT = 86;
    /** An index over this key already exists, under a different name or with different options. */
    private static final int INDEX_OPTIONS_CONFLICT = 85;
    /** The index was already gone - another node dropped it first. */
    private static final int INDEX_NOT_FOUND = 27;
    /** MongoDB will not build this index at all - a malformed partial filter, too many indexes. */
    private static final int CANNOT_CREATE_INDEX = 67;

    private static final String ID_INDEX = "_id_";

    /**
     * Ensures {@code declared} is what {@code collection} carries.
     * <p>
     * The returned Completable always completes: a collection that could not be fully indexed is a
     * degraded node, not a failed one. Callers that need to know what happened read {@link MongoIndexReport}.
     */
    public static Completable ensureIndexes(MongoCollection<?> collection, List<IndexModel> declared) {
        final String collectionName = reportKey(collection);
        if (declared.isEmpty()) {
            return Completable.complete();
        }
        return createAll(collection, declared)
                .doOnComplete(() -> {
                    log.debug("{} indexes ensured for collection {}", declared.size(), collectionName);
                    declared.forEach(model -> MongoIndexReport.ensured(collectionName, indexName(model)));
                })
                .onErrorResumeNext(error -> reconcile(collection, declared, error));
    }

    /**
     * Attempt to create indexes the fast way: one round trip, no reads.
     * A transient failure is retried, but a settled one is not.
     */
    private static Completable createAll(MongoCollection<?> collection, List<IndexModel> models) {
        final String collectionName = reportKey(collection);
        return Completable.fromPublisher(collection.createIndexes(models))
                .retryWhen(errors -> errors
                        .zipWith(Flowable.range(1, MAX_ATTEMPTS + 1), Attempt::new)
                        .concatMap(attempt -> {
                            if (attempt.spent() || isPermanent(attempt.error())) {
                                return Flowable.<Long>error(attempt.error());
                            }
                            log.debug("Retrying index creation for collection {}, attempt={}/{}",
                                    collectionName, attempt.number(), MAX_ATTEMPTS);
                            return Flowable.timer(attempt.number(), TimeUnit.SECONDS);
                        }));
    }

    /**
     * Reads back what the collection actually carries and settles each declared index against it.
     */
    private static Completable reconcile(MongoCollection<?> collection, List<IndexModel> models, Throwable batchError) {
        final String collectionName = reportKey(collection);
        log.warn("Index creation failed for collection {} ({}). Reconciling its {} declared indexes one by one.",
                collectionName, describe(batchError), models.size());
        return listIndexes(collection)
                .flatMapCompletable(existing -> Flowable.fromIterable(models)
                        .concatMapCompletable(model -> reconcileOne(collection, model, existing)))
                .onErrorResumeNext(error -> {
                    log.error("Unable to reconcile the indexes of collection {}. Every query against it may fall back to a collection scan, and any TTL index it declares may be missing.",
                            collectionName, error);
                    models.forEach(model -> MongoIndexReport.failed(collectionName, indexName(model),
                            "reconciliation could not run: " + describe(error)));
                    return Completable.complete();
                });
    }

    private static Completable reconcileOne(MongoCollection<?> collection, IndexModel model, List<Document> existing) {
        final String collectionName = reportKey(collection);
        final String name = indexName(model);

        final Document underOurName = firstMatching(existing, index -> name.equals(index.getString("name")));
        if (underOurName != null) {
            return matches(underOurName, model)
                    ? Completable.fromAction(() -> MongoIndexReport.ensured(collectionName, name))
                    : rebuild(collection, model, existing, underOurName);
        }

        return createOne(collection, model)
                .doOnComplete(() -> MongoIndexReport.ensured(collectionName, name))
                .onErrorResumeNext(error -> errorCode(error) == INDEX_OPTIONS_CONFLICT
                        ? Completable.fromAction(() -> reportShadowed(collectionName, model, existing))
                        : reportFailure(collectionName, model, error));
    }

    /** AM owns this name, so the index under it is AM's to replace. */
    private static Completable rebuild(MongoCollection<?> collection, IndexModel model, List<Document> existing, Document ours) {
        final String collectionName = reportKey(collection);
        final String name = indexName(model);
        if (ID_INDEX.equals(name)) {
            log.error("Index {} of collection {} does not match its declaration, but the id index cannot be replaced.", name, collectionName);
            MongoIndexReport.failed(collectionName, name, "the id index cannot be replaced");
            return Completable.complete();
        }
        final String detail = "replaced " + describe(ours) + " with " + describe(model);
        log.warn("Index {} of collection {} no longer matches its declaration - dropping and rebuilding it ({}).",
                name, collectionName, detail);
        return dropIndex(collection, name)
                .andThen(createOne(collection, model))
                .doOnComplete(() -> MongoIndexReport.rebuilt(collectionName, name, detail))
                .onErrorResumeNext(error -> errorCode(error) == INDEX_OPTIONS_CONFLICT
                        ? Completable.fromAction(() -> reportShadowed(collectionName, model, existing))
                        : reportFailure(collectionName, model, error));
    }

    /**
     * MongoDB refused our index because another one already covers its key. Keep the incumbent -
     * but if it cannot stand in for what the declaration asked for, something really is lost and
     * this is a failure, not a note.
     */
    private static void reportShadowed(String collectionName, IndexModel model, List<Document> existing) {
        final String name = indexName(model);
        final String key = keyOf(model).toJson();
        final Document incumbent = firstMatching(existing, index -> sameKey(index.get("key", Document.class), model));
        final String incumbentName = incumbent != null ? incumbent.getString("name") : "another index";
        final String missing = ttlLostTo(incumbent, model);

        if (missing == null) {
            log.warn("Index {} of collection {} was not created: {} already covers {}. Leaving the existing index in place.",
                    name, collectionName, incumbentName, key);
            MongoIndexReport.shadowed(collectionName, name, "covered by " + incumbentName);
        } else {
            log.error("Index {} of collection {} was not created: {} already covers {}, but {}. Leaving the existing index in place - drop {} to let AM create {}.",
                    name, collectionName, incumbentName, key, missing, incumbentName, name);
            MongoIndexReport.failed(collectionName, name, "shadowed by " + incumbentName + ": " + missing);
        }
    }

    private static Completable reportFailure(String collectionName, IndexModel model, Throwable error) {
        final String name = indexName(model);
        log.error("Index {} of collection {} could not be created ({}). {}",
                name, collectionName, describe(error), consequenceOf(model), error);
        MongoIndexReport.failed(collectionName, name, describe(error));
        return Completable.complete();
    }

    /** What the operator loses by this index being absent, in the terms they care about. */
    private static String consequenceOf(IndexModel model) {
        if (expireAfterSeconds(model.getOptions()) != null) {
            return "Expired records of this collection will never be removed.";
        }
        if (model.getOptions().isUnique()) {
            return "Duplicate records can now be stored in this collection.";
        }
        return "Queries on " + keyOf(model).toJson() + " will fall back to a collection scan.";
    }

    /** What the incumbent index does not cover of the declaration. */
    private static String ttlLostTo(Document incumbent, IndexModel model) {
        final Long declaredTtl = expireAfterSeconds(model.getOptions());
        if (declaredTtl == null) {
            return null;
        }
        if (incumbent == null) {
            // created earlier in this same pass, so it is not in the snapshot we read has to read as lost
            return "it cannot be confirmed to expire records, so expired records may never be removed";
        }
        return declaredTtl.equals(expireAfterSeconds(incumbent))
                ? null
                : "it does not expire records after " + declaredTtl + "s, so expired records will never be removed";
    }

    // --- declarations ---------------------------------------------------------------------------

    private static String indexName(IndexModel model) {
        final String declared = model.getOptions().getName();
        return declared != null ? declared : defaultIndexName(keyOf(model));
    }

    /** The name MongoDB would generate for this key, so an unnamed declaration reconciles too. */
    private static String defaultIndexName(BsonDocument key) {
        return key.entrySet().stream()
                .map(entry -> entry.getKey() + "_" + direction(entry.getValue()))
                .collect(Collectors.joining("_"));
    }

    // --- comparison -----------------------------------------------------------------------------

    private static boolean matches(Document existing, IndexModel model) {
        final IndexOptions options = model.getOptions();
        return sameKey(existing.get("key", Document.class), model)
                && existing.getBoolean("unique", false) == options.isUnique()
                && existing.getBoolean("sparse", false) == options.isSparse()
                && Objects.equals(expireAfterSeconds(existing), expireAfterSeconds(options))
                && Objects.equals(normalise(existing.get("partialFilterExpression", Document.class)),
                                  normalise(options.getPartialFilterExpression()));
    }

    private static boolean sameKey(Document existingKey, IndexModel model) {
        return existingKey != null && normalisedKey(normalise(existingKey)).equals(normalisedKey(keyOf(model)));
    }

    /** Renders a key so that {@code 1}, {@code 1L} and {@code 1.0} do not read as different indexes. */
    private static List<String> normalisedKey(BsonDocument key) {
        return key.entrySet().stream()
                .map(entry -> entry.getKey() + '=' + direction(entry.getValue()))
                .toList();
    }

    private static String direction(org.bson.BsonValue value) {
        if (value.isNumber()) {
            return String.valueOf(value.asNumber().longValue());
        }
        return value.isString() ? value.asString().getValue() : value.toString();
    }

    private static Long expireAfterSeconds(Document index) {
        final Object value = index.get("expireAfterSeconds");
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Long expireAfterSeconds(IndexOptions options) {
        return options.getExpireAfter(TimeUnit.SECONDS);
    }

    private static BsonDocument keyOf(IndexModel model) {
        return normalise(model.getKeys());
    }

    private static BsonDocument normalise(Bson bson) {
        return bson == null ? null : bson.toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    // --- mongo --------------------------------------------------------------------------------

    private static String reportKey(MongoCollection<?> collection) {
        return collection.getNamespace().getFullName();
    }

    private static Single<List<Document>> listIndexes(MongoCollection<?> collection) {
        return Flowable.fromPublisher(collection.listIndexes()).toList();
    }

    private static Completable createOne(MongoCollection<?> collection, IndexModel model) {
        return Completable.fromPublisher(collection.createIndex(model.getKeys(), model.getOptions()));
    }

    private static Completable dropIndex(MongoCollection<?> collection, String name) {
        return Completable.fromPublisher(collection.dropIndex(name))
                .onErrorResumeNext(error -> errorCode(error) == INDEX_NOT_FOUND
                        // another node got there first, which is the outcome we wanted anyway
                        ? Completable.complete()
                        : Completable.error(error));
    }

    // --- plumbing -----------------------------------------------------------------------------

    private static Document firstMatching(List<Document> indexes, Predicate<Document> predicate) {
        return indexes.stream().filter(predicate).findFirst().orElse(null);
    }

    /**
     * Whether waiting and asking again could possibly help. A conflict, a uniqueness violation in
     * the data and an index the server will not build are all settled answers.
     */
    private static boolean isPermanent(Throwable error) {
        final int code = errorCode(error);
        return code == INDEX_KEY_SPECS_CONFLICT
                || code == INDEX_OPTIONS_CONFLICT
                || code == CANNOT_CREATE_INDEX
                // the data already in the collection violates the uniqueness the declaration asks for
                || ErrorCategory.fromErrorCode(code) == ErrorCategory.DUPLICATE_KEY;
    }

    private static int errorCode(Throwable error) {
        for (Throwable candidate = error; candidate != null && candidate != candidate.getCause(); candidate = candidate.getCause()) {
            if (candidate instanceof MongoCommandException mongoError) {
                return mongoError.getErrorCode();
            }
        }
        return -1;
    }

    private static String describe(Throwable error) {
        for (Throwable candidate = error; candidate != null && candidate != candidate.getCause(); candidate = candidate.getCause()) {
            if (candidate instanceof MongoCommandException mongoError) {
                return mongoError.getErrorCodeName() + '/' + mongoError.getErrorCode();
            }
        }
        return String.valueOf(error);
    }

    private static String describe(Document index) {
        return "key=" + index.get("key") + optionSuffix(expireAfterSeconds(index), index.getBoolean("unique", false));
    }

    private static String describe(IndexModel model) {
        return "key=" + keyOf(model).toJson()
                + optionSuffix(expireAfterSeconds(model.getOptions()), model.getOptions().isUnique());
    }

    private static String optionSuffix(Long expireAfterSeconds, boolean unique) {
        final StringBuilder suffix = new StringBuilder();
        if (expireAfterSeconds != null) {
            suffix.append(" expireAfterSeconds=").append(expireAfterSeconds);
        }
        if (unique) {
            suffix.append(" unique=true");
        }
        return suffix.toString();
    }

    private record Attempt(Throwable error, int number) {
        boolean spent() {
            return number > MAX_ATTEMPTS;
        }
    }
}
