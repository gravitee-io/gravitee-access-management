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
import io.reactivex.Single;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    protected void init(MongoCollection<?> collection) {
        this.createIndex(collection, new Document(FIELD_ID, 1), new IndexOptions(), true);
    }

    protected void createIndex(MongoCollection<?> collection, Document document, boolean ensure) {
       this.createIndex(collection, document, new IndexOptions(), ensure);
    }

    protected void createIndex(MongoCollection<?> collection, Document document, IndexOptions indexOptions, boolean ensure) {
        if (ensure) {
            Single.fromPublisher(collection.createIndex(document, indexOptions))
                    .doOnSuccess(s -> logger.debug("Created an index named: {}", s))
                    .doOnError(throwable -> logger.error("Error occurs during creation of index", throwable)).blockingGet();
        }
    }
}
