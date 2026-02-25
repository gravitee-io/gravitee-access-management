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

import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.AuthorizationSchema;
import io.gravitee.am.repository.management.api.AuthorizationSchemaRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AuthorizationSchemaMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoAuthorizationSchemaRepository extends AbstractManagementMongoRepository implements AuthorizationSchemaRepository {

    private static final String FIELD_DOMAIN_ID = "domainId";

    private MongoCollection<AuthorizationSchemaMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("authorization_schemas", AuthorizationSchemaMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(new Document(FIELD_DOMAIN_ID, 1), new IndexOptions().name("d1").unique(true)));
    }

    @Override
    public Maybe<AuthorizationSchema> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationSchema> findByDomain(String domainId) {
        return Observable.fromPublisher(collection.find(eq(FIELD_DOMAIN_ID, domainId)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationSchema> create(AuthorizationSchema item) {
        AuthorizationSchemaMongo schema = convert(item);
        schema.setId(schema.getId() == null ? RandomString.generate() : schema.getId());
        return Single.fromPublisher(collection.insertOne(schema))
                .flatMap(success -> {
                    item.setId(schema.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationSchema> update(AuthorizationSchema item) {
        AuthorizationSchemaMongo schema = convert(item);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, schema.getId()), schema))
                .flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        return Completable.fromPublisher(collection.deleteMany(eq(FIELD_DOMAIN_ID, domainId)))
                .observeOn(Schedulers.computation());
    }

    private AuthorizationSchema convert(AuthorizationSchemaMongo mongo) {
        if (mongo == null) {
            return null;
        }

        AuthorizationSchema schema = new AuthorizationSchema();
        schema.setId(mongo.getId());
        schema.setDomainId(mongo.getDomainId());
        schema.setEngineType(mongo.getEngineType());
        schema.setContent(mongo.getContent());
        schema.setVersion(mongo.getVersion());
        schema.setCreatedAt(mongo.getCreatedAt());
        schema.setUpdatedAt(mongo.getUpdatedAt());

        return schema;
    }

    private AuthorizationSchemaMongo convert(AuthorizationSchema schema) {
        if (schema == null) {
            return null;
        }

        AuthorizationSchemaMongo mongo = new AuthorizationSchemaMongo();
        mongo.setId(schema.getId());
        mongo.setDomainId(schema.getDomainId());
        mongo.setEngineType(schema.getEngineType());
        mongo.setContent(schema.getContent());
        mongo.setVersion(schema.getVersion());
        mongo.setCreatedAt(schema.getCreatedAt());
        mongo.setUpdatedAt(schema.getUpdatedAt());

        return mongo;
    }
}
