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
package io.gravitee.am.repository.mongodb.oauth2;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.repository.mongodb.common.AbstractMongoRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractOAuth2MongoRepository extends AbstractMongoRepository {

    @Autowired
    @Qualifier("oauth2MongoTemplate")
    protected MongoDatabase mongoOperations;

    @Value("${oauth2.mongodb.ensureIndexOnStart:true}")
    private boolean ensureIndexOnStart;

<<<<<<< HEAD
    protected void createIndex(MongoCollection<?> collection, Document document) {
        super.createIndex(collection, document, new IndexOptions(), ensureIndexOnStart);
    }

    protected void createIndex(MongoCollection<?> collection, Document document, IndexOptions indexOptions) {
        super.createIndex(collection, document, indexOptions, ensureIndexOnStart);
=======
    protected void createIndex(MongoCollection<?> collection, Map<Document, IndexOptions> indexes) {
        super.createIndex(collection, indexes, ensureIndexOnStart);
>>>>>>> 5b1bc9bc07 (fix: avoid infinite blocking call durint Indexes creation)
    }
}
