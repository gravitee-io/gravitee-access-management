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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractMongoRepository {

    private final Logger logger = LoggerFactory.getLogger(AbstractMongoRepository.class);
    protected static final String FIELD_ID = "_id";
    protected static final String FIELD_DOMAIN = "domain";
    protected static final String FIELD_CLIENT = "client";
    protected static final String FIELD_UPDATED_AT = "updatedAt";
    protected static final String FIELD_REFERENCE_TYPE = "referenceType";
    protected static final String FIELD_REFERENCE_ID = "referenceId";
    protected static final String FIELD_ORGANIZATION_ID = "organizationId";
    protected static final String FIELD_NAME = "name";
    protected static final String FIELD_USER_ID = "userId";

    protected void init(MongoCollection<?> collection) {
        Single.fromPublisher(collection.createIndex(new Document(FIELD_ID, 1), new IndexOptions()))
                .subscribe(
                        ignore -> logger.debug("Index {} created", FIELD_ID),
                        throwable -> logger.error("Error occurs during creation of index {}", FIELD_ID, throwable)
                );
    }

    protected void createIndex(MongoCollection<?> collection, Map<Document, IndexOptions> indexes, boolean ensure) {
        if (ensure) {
            var indexesModel = indexes.entrySet().stream().map(entry -> new IndexModel(entry.getKey(), entry.getValue().background(true))).toList();
            Completable.fromPublisher(collection.createIndexes(indexesModel))
                    .subscribe(() -> logger.debug("{} indexes created", indexes.size()),
                            throwable -> logger.error("An error has occurred during creation of indexes", throwable));
        }
    }
}
