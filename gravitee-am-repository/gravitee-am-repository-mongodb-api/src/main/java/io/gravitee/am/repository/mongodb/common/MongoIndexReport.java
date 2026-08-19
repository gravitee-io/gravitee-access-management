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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What became of every index the MongoDB repositories declared.
 *
 * @author GraviteeSource Team
 */
public final class MongoIndexReport {

    /**
     * ENSURED - the index is in place.
     * REBUILT - an index of the same name held a different definition and was replaced.
     * SHADOWED - not created, because another index already covers its key with the same
     * guarantees. Coverage is intact; only the name differs.
     * FAILED - not created, and something is actually lost: a missing TTL, a missing uniqueness
     * constraint, or an index MongoDB refused to build.
     */
    public enum Status {
        ENSURED, REBUILT, SHADOWED, FAILED
    }

    public record IndexOutcome(String collection, String index, Status status, String detail) {
        @Override
        public String toString() {
            return collection + '.' + index + ' ' + status + " (" + detail + ')';
        }
    }

    private static final Map<String, IndexOutcome> OUTCOMES = new ConcurrentHashMap<>();

    private MongoIndexReport() {
    }

    static void ensured(String collection, String index) {
        record(new IndexOutcome(collection, index, Status.ENSURED, "in place"));
    }

    static void rebuilt(String collection, String index, String detail) {
        record(new IndexOutcome(collection, index, Status.REBUILT, detail));
    }

    static void shadowed(String collection, String index, String detail) {
        record(new IndexOutcome(collection, index, Status.SHADOWED, detail));
    }

    static void failed(String collection, String index, String detail) {
        record(new IndexOutcome(collection, index, Status.FAILED, detail));
    }

    private static void record(IndexOutcome outcome) {
        OUTCOMES.put(outcome.collection() + '.' + outcome.index(), outcome);
    }

    /** Every outcome recorded so far, ordered by collection then index. */
    public static List<IndexOutcome> outcomes() {
        return OUTCOMES.values().stream()
                .sorted(Comparator.comparing(IndexOutcome::collection).thenComparing(IndexOutcome::index))
                .toList();
    }

    public static List<IndexOutcome> outcomes(String collection) {
        return outcomes().stream().filter(outcome -> outcome.collection().equals(collection)).toList();
    }

    /**
     * The indexes that are missing something the declaration asked for. An empty list is the only
     * healthy state; anything in here degrades queries, retention or uniqueness on that collection.
     */
    public static List<IndexOutcome> failures() {
        return outcomes().stream().filter(outcome -> outcome.status() == Status.FAILED).toList();
    }

    public static void clear() {
        OUTCOMES.clear();
    }
}
