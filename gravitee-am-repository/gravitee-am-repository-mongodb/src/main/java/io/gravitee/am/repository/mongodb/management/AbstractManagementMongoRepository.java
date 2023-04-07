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
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mongodb.client.model.Filters.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractManagementMongoRepository extends AbstractMongoRepository {

    @Autowired
    @Qualifier("managementMongoTemplate")
    protected MongoDatabase mongoOperations;

    @Value("${management.mongodb.ensureIndexOnStart:true}")
    private boolean ensureIndexOnStart;

    @Value("${management.mongodb.cursorMaxTime:60000}")
    private int cursorMaxTimeInMs;

    protected void createIndex(MongoCollection<?> collection, Document document) {
        super.createIndex(collection, document, new IndexOptions(), ensureIndexOnStart);
    }

    protected void createIndex(MongoCollection<?> collection, Document document, IndexOptions indexOptions) {
        // if we set an index options it means that we want to force the index creation
        super.createIndex(collection, document, indexOptions, true);
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

        return optional.map(value -> {
            if (value instanceof Enum) {
                return eq(name, ((Enum<?>) value).name());
            }

            if (value instanceof Collection) {
                if (((Collection<?>) value).isEmpty()) {
                    return null;
                }
                return in(name, (Collection<?>) value);
            }

            return eq(name, value);
        }).orElse(null);
    }

    protected Maybe<Bson> toBsonFilter(boolean logicalOr, Bson... filter) {

        List<Bson> filterCriteria = Stream.of(filter).filter(Objects::nonNull).collect(Collectors.toList());

        if (filterCriteria.isEmpty()) {
            return Maybe.empty();
        }

        if (logicalOr) {
            return Maybe.just(or(filterCriteria));
        } else {
            return Maybe.just(and(filterCriteria));
        }
    }
}
