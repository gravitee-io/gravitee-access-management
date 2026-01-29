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

    protected <T, R> Single<Page<R>> findPage(MongoCollection<T> collection, Bson query, int page, int size, io.reactivex.rxjava3.functions.Function<T, R> mapper) {
        Single<Long> countOperation = countItems(collection, query, countOptions());
        Single<Set<R>> contentOperation = Observable.fromPublisher(withMaxTime(collection.find(query))
                        .sort(new BasicDBObject(MongoUtils.FIELD_UPDATED_AT, -1))
                        .skip(size * page)
                        .limit(size))
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
        // currently search on client_id field
        Bson searchQuery = or(eq(fieldClientId, query), eq("name", query));
        // if query contains wildcard, use the regex query
        if (query.contains("*")) {
            String compactQuery = query.replaceAll("\\*+", ".*");
            String regex = "^" + compactQuery;
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            searchQuery = or(new BasicDBObject(fieldClientId, pattern), new BasicDBObject("name", pattern));
        }
        return searchQuery;
    }

    protected Single<Long> countItems(MongoCollection collection, Bson query, CountOptions options) {
        return Observable.fromPublisher(collection.countDocuments(query, options)).first(0L);
    }
}
