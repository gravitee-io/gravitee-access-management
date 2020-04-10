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

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.repository.management.api.EntrypointRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.EntrypointMongo;
import io.reactivex.*;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoEntrypointRepository extends AbstractManagementMongoRepository implements EntrypointRepository {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANIZATION_ID = "organizationId";
    private MongoCollection<EntrypointMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("entrypoints", EntrypointMongo.class);
    }

    @Override
    public Maybe<Entrypoint> findById(String id, String organizationId) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_ORGANIZATION_ID, organizationId), eq(FIELD_ID, id))).first()).firstElement().map(this::convert);
    }

    @Override
    public Maybe<Entrypoint> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Flowable<Entrypoint> findAll(String organizationId) {
        return Flowable.fromPublisher(collection.find(eq("organizationId", organizationId))).map(this::convert);
    }

    @Override
    public Single<Entrypoint> create(Entrypoint item) {
        EntrypointMongo entrypoint = convert(item);
        entrypoint.setId(entrypoint.getId() == null ? RandomString.generate() : entrypoint.getId());
        return Single.fromPublisher(collection.insertOne(entrypoint)).flatMap(success -> findById(entrypoint.getId()).toSingle());
    }

    @Override
    public Single<Entrypoint> update(Entrypoint item) {
        EntrypointMongo entrypoint = convert(item);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, entrypoint.getId()), entrypoint)).flatMap(updateResult -> findById(entrypoint.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)));
    }

    private Entrypoint convert(EntrypointMongo entrypointMongo) {
        if (entrypointMongo == null) {
            return null;
        }

        Entrypoint entrypoint = new Entrypoint();
        entrypoint.setId(entrypointMongo.getId());
        entrypoint.setName(entrypointMongo.getName());
        entrypoint.setDescription(entrypointMongo.getDescription());
        entrypoint.setUrl(entrypointMongo.getUrl());
        entrypoint.setTags(entrypointMongo.getTags());
        entrypoint.setOrganizationId(entrypointMongo.getOrganizationId());
        entrypoint.setDefaultEntrypoint(entrypointMongo.isDefaultEntrypoint());
        entrypoint.setCreatedAt(entrypointMongo.getCreatedAt());
        entrypoint.setUpdatedAt(entrypointMongo.getUpdatedAt());

        return entrypoint;
    }

    private EntrypointMongo convert(Entrypoint entrypoint) {
        if (entrypoint == null) {
            return null;
        }

        EntrypointMongo entrypointMongo = new EntrypointMongo();
        entrypointMongo.setId(entrypoint.getId());
        entrypointMongo.setName(entrypoint.getName());
        entrypointMongo.setDescription(entrypoint.getDescription());
        entrypointMongo.setUrl(entrypoint.getUrl());
        entrypointMongo.setTags(entrypoint.getTags());
        entrypointMongo.setOrganizationId(entrypoint.getOrganizationId());
        entrypointMongo.setDefaultEntrypoint(entrypoint.isDefaultEntrypoint());
        entrypointMongo.setCreatedAt(entrypoint.getCreatedAt());
        entrypointMongo.setUpdatedAt(entrypoint.getUpdatedAt());

        return entrypointMongo;
    }
}