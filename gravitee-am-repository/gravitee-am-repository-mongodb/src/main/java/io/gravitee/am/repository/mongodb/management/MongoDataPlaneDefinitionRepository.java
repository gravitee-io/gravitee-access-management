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
import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.repository.management.api.DataPlaneDefinitionRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.DataPlaneDefinitionMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoDataPlaneDefinitionRepository extends AbstractManagementMongoRepository implements DataPlaneDefinitionRepository {

    private static final String COLLECTION_NAME = "dataplanes";
    private static final String FIELD_ENVIRONMENT_ID = "environmentId";

    private MongoCollection<DataPlaneDefinitionMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection(COLLECTION_NAME, DataPlaneDefinitionMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(new Document(FIELD_ENVIRONMENT_ID, 1), new IndexOptions().name("e1")));
    }

    @Override
    public Flowable<DataPlaneDefinition> findAll() {
        return Flowable.fromPublisher(withMaxTime(collection.find()))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<DataPlaneDefinition> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<DataPlaneDefinition> findByEnvironmentId(String environmentId) {
        return Flowable.fromPublisher(withMaxTime(collection.find(eq(FIELD_ENVIRONMENT_ID, environmentId))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<DataPlaneDefinition> create(DataPlaneDefinition item) {
        return Single.fromPublisher(collection.insertOne(convert(item)))
                .map(success -> item)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<DataPlaneDefinition> update(DataPlaneDefinition item) {
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, item.getId()), convert(item)))
                .map(updateResult -> item)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private DataPlaneDefinition convert(DataPlaneDefinitionMongo entity) {
        if (entity == null) {
            return null;
        }
        DataPlaneDefinition definition = new DataPlaneDefinition();
        definition.setId(entity.getId());
        definition.setName(entity.getName());
        definition.setType(entity.getType());
        definition.setGatewayUrl(entity.getGatewayUrl());
        definition.setOrganizationId(entity.getOrganizationId());
        definition.setEnvironmentId(entity.getEnvironmentId());
        definition.setConfiguration(entity.getConfiguration());
        definition.setCreatedAt(entity.getCreatedAt());
        definition.setUpdatedAt(entity.getUpdatedAt());
        return definition;
    }

    private DataPlaneDefinitionMongo convert(DataPlaneDefinition definition) {
        if (definition == null) {
            return null;
        }
        DataPlaneDefinitionMongo entity = new DataPlaneDefinitionMongo();
        entity.setId(definition.getId());
        entity.setName(definition.getName());
        entity.setType(definition.getType());
        entity.setGatewayUrl(definition.getGatewayUrl());
        entity.setOrganizationId(definition.getOrganizationId());
        entity.setEnvironmentId(definition.getEnvironmentId());
        entity.setConfiguration(definition.getConfiguration());
        entity.setCreatedAt(definition.getCreatedAt());
        entity.setUpdatedAt(definition.getUpdatedAt());
        return entity;
    }
}
