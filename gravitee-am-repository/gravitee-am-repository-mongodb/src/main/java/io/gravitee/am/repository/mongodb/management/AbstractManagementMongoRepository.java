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

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.mongodb.common.AbstractMongoRepository;
import io.gravitee.am.repository.mongodb.common.MongoUtils;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.util.regex.Pattern;

import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_NAME;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractManagementMongoRepository extends AbstractMongoRepository {

    /**
     * Case-insensitive collation for search queries.
     * Uses strength SECONDARY (level 2) which ignores case but respects diacritics.
     *
     * Note on locale: MongoDB's "simple" locale does not support case-insensitivity,
     * so a language locale is required. We use "en" as it provides standard Unicode
     * case folding which works correctly for all languages. The locale primarily
     * affects sort order (not case comparison), and since we use this collation
     * for search matching rather than sorting, the choice of locale does not
     * impact non-English characters.
     *
     * @see <a href="https://www.mongodb.com/docs/manual/reference/collation/">MongoDB Collation</a>
     */
    protected static final Collation CASE_INSENSITIVE_COLLATION = Collation.builder()
            .locale("en")
            .collationStrength(CollationStrength.SECONDARY)
            .build();

    @Autowired
    @Qualifier("managementMongoTemplate")
    protected MongoDatabase mongoOperations;

    @Value("${repositories.management.mongodb.ensureIndexOnStart:${management.mongodb.ensureIndexOnStart:true}}")
    protected boolean ensureIndexOnStart;

    @Value("${repositories.management.mongodb.cursorMaxTime:${management.mongodb.cursorMaxTime:60000}}")
    protected int cursorMaxTimeInMs;

    protected void createIndex(MongoCollection<?> collection, Map<Document, IndexOptions> indexes) {
        super.createIndex(collection, indexes, ensureIndexOnStart);
    }

    protected final <TResult> AggregatePublisher<TResult> withMaxTime(AggregatePublisher<TResult> query) {
        return query.maxTime(this.cursorMaxTimeInMs, TimeUnit.MILLISECONDS);
    }

    protected final <TResult> FindPublisher<TResult> withMaxTime(FindPublisher<TResult> query) {
        return query.maxTime(this.cursorMaxTimeInMs, TimeUnit.MILLISECONDS);
    }

    protected final CountOptions countOptions() {
        return new CountOptions().maxTime(cursorMaxTimeInMs, TimeUnit.MILLISECONDS);
    }

    protected final CountOptions countOptionsWithCollation() {
        return new CountOptions()
                .maxTime(cursorMaxTimeInMs, TimeUnit.MILLISECONDS)
                .collation(CASE_INSENSITIVE_COLLATION);
    }

    /**
     * Creates index options with case-insensitive collation.
     * Indexes created with this collation can be used for case-insensitive equality queries.
     */
    protected static IndexOptions indexOptionsWithCollation(String name) {
        return new IndexOptions()
                .name(name)
                .collation(CASE_INSENSITIVE_COLLATION);
    }

    protected Bson toBsonFilter(String name, Optional<?> optional) {
        return MongoUtils.toBsonFilter(name, optional);
    }

    protected Maybe<Bson> toBsonFilter(boolean logicalOr, Bson... filter) {
        return MongoUtils.toBsonFilter(logicalOr, filter);
    }

    /**
     * Find paginated results with optional case-insensitive collation.
     * When useCollation is true, queries will use case-insensitive matching and can leverage collation indexes.
     */
    protected <T, R> Single<Page<R>> findPage(MongoCollection<T> collection, Bson query, int page, int size,
                                               io.reactivex.rxjava3.functions.Function<T, R> mapper, boolean useCollation) {
        CountOptions countOpts = useCollation ? countOptionsWithCollation() : countOptions();
        Single<Long> countOperation = countItems(collection, query, countOpts);

        FindPublisher<T> findPublisher = withMaxTime(collection.find(query))
                .sort(new BasicDBObject(MongoUtils.FIELD_UPDATED_AT, -1))
                .skip(size * page)
                .limit(size);

        if (useCollation) {
            findPublisher = findPublisher.collation(CASE_INSENSITIVE_COLLATION);
        }

        Single<Set<R>> contentOperation = Observable.fromPublisher(findPublisher)
                .map(mapper)
                .collect(HashSet::new, Set::add);
        return Single.zip(countOperation, contentOperation, (count, content) -> new Page<>(content, page, count))
                .observeOn(Schedulers.computation());
    }
    protected Bson buildSearchQuery(String query, String domain, String domainFieldName, String fieldClientId) {
        return and(
                eq(domainFieldName, domain),
                buildTextQuery(query, fieldClientId));
    }

    protected Bson buildTextQuery(String query, String fieldClientId) {
        if (query.contains("*")) {
            // Wildcard search: replace * with .* for regex matching
            // Note: Regex queries cannot efficiently use indexes regardless of collation
            String compactQuery = query.replaceAll("\\*+", ".*");
            String regex = "^" + compactQuery;
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            return or(new BasicDBObject(fieldClientId, pattern), new BasicDBObject(FIELD_NAME, pattern));
        } else {
            // Exact match: use simple equality filter
            // Case-insensitivity is handled by collation at query execution time,
            // which allows MongoDB to use collation-enabled indexes
            return or(eq(fieldClientId, query), eq(FIELD_NAME, query));
        }
    }

    /**
     * Checks if a search query contains wildcards.
     * Wildcard queries use regex and cannot leverage collation indexes.
     */
    protected boolean isWildcardQuery(String query) {
        return query != null && query.contains("*");
    }

    protected Single<Long> countItems(MongoCollection collection, Bson query, CountOptions options) {
        return Observable.fromPublisher(collection.countDocuments(query, options)).first(0L);
    }
}
