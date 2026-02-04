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

import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.mongodb.common.AbstractMongoRepository;
import io.gravitee.am.repository.mongodb.common.MongoUtils;
import io.reactivex.rxjava3.core.Maybe;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractManagementMongoRepository extends AbstractMongoRepository {

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

    protected Bson toBsonFilter(String name, Optional<?> optional) {
        return MongoUtils.toBsonFilter(name, optional);
    }

    protected Maybe<Bson> toBsonFilter(boolean logicalOr, Bson... filter) {
        return MongoUtils.toBsonFilter(logicalOr, filter);
    }
<<<<<<< HEAD
=======

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
            // First escape regex metacharacters (except *) to prevent PatternSyntaxException
            String escapedQuery = escapeRegexMetacharacters(query);
            String compactQuery = escapedQuery.replaceAll("\\*+", ".*");
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
     * Escapes regex metacharacters in a query string, except for the asterisk (*) which is
     * used as a wildcard. This prevents PatternSyntaxException when users search for
     * special characters like [ ] { } etc.
     *
     * @param query the search query that may contain regex metacharacters
     * @return the query with metacharacters escaped
     */
    protected static String escapeRegexMetacharacters(String query) {
        // Escape all regex metacharacters except * (which we use as wildcard)
        // Metacharacters: . ^ $ | ? + \ [ ] { } ( )
        return query.replaceAll("([\\[\\]{}()^$.|?+\\\\])", "\\\\$1");
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
>>>>>>> 75f081ac2 (fix(search): escape regex metacharacters in search queries)
}
