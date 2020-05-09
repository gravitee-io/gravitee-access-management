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
import io.gravitee.am.model.uma.ResourceSet;
import io.gravitee.am.repository.management.api.ResourceSetRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.uma.ResourceSetMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Component
public class MongoResourceSetRepository extends AbstractManagementMongoRepository implements ResourceSetRepository {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_CLIENT = "clientId";
    private static final String FIELD_USER = "userId";
    public static final String COLLECTION_NAME = "uma_resource_set";
    private MongoCollection<ResourceSetMongo> resourceSetCollection;

    @PostConstruct
    public void init() {
        resourceSetCollection = mongoOperations.getCollection(COLLECTION_NAME, ResourceSetMongo.class);
    }

    @Override
    public Maybe<ResourceSet> findById(String id) {
        return Observable.fromPublisher(resourceSetCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<ResourceSet> create(ResourceSet item) {
        ResourceSetMongo resourceSet = convert(item);
        resourceSet.setId(resourceSet.getId() == null ? RandomString.generate() : resourceSet.getId());
        return Single.fromPublisher(resourceSetCollection.insertOne(resourceSet)).flatMap(success -> findById(resourceSet.getId()).toSingle());
    }

    @Override
    public Single<ResourceSet> update(ResourceSet item) {
        ResourceSetMongo resourceSetMongo = convert(item);
        return Single.fromPublisher(resourceSetCollection.replaceOne(eq(FIELD_ID, resourceSetMongo.getId()), resourceSetMongo)).flatMap(success -> findById(resourceSetMongo.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(resourceSetCollection.deleteOne(eq(FIELD_ID, id)));
    }

    @Override
    public Single<List<ResourceSet>> findByDomainAndClientAndUser(String domain, String client, String user) {
        return Observable.fromPublisher(resourceSetCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT, client), eq(FIELD_USER, user)))).map(this::convert).toList();
    }

    @Override
    public Single<List<ResourceSet>> findByDomainAndClientAndUserAndResource(String domain, String client, String userId, List<String> resources) {
        return Observable.fromPublisher(resourceSetCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT, client), eq(FIELD_USER, userId), in(FIELD_ID, resources)))).map(this::convert).toList();
    }

    @Override
    public Maybe<ResourceSet> findByDomainAndClientAndUserAndResource(String domain, String client, String user, String resource) {
        return Observable.fromPublisher(resourceSetCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT, client), eq(FIELD_USER, user), eq(FIELD_ID, resource))).first()).firstElement().map(this::convert);
    }

    private ResourceSet convert(ResourceSetMongo resourceSetMongo) {
        return new ResourceSet()
                .setId(resourceSetMongo.getId())
                .setResourceScopes(resourceSetMongo.getResourceScopes())
                .setDescription(resourceSetMongo.getDescription())
                .setIconUri(resourceSetMongo.getIconUri())
                .setName(resourceSetMongo.getName())
                .setType(resourceSetMongo.getType())
                .setDomain(resourceSetMongo.getDomain())
                .setUserId(resourceSetMongo.getUserId())
                .setClientId(resourceSetMongo.getClientId())
                .setUpdatedAt(resourceSetMongo.getUpdatedAt())
                .setCreatedAt(resourceSetMongo.getCreatedAt());
    }

    private ResourceSetMongo convert(ResourceSet resourceSet) {
        ResourceSetMongo resourceSetMongo = new ResourceSetMongo()
                .setId(resourceSet.getId())
                .setResourceScopes(resourceSet.getResourceScopes())
                .setDescription(resourceSet.getDescription())
                .setIconUri(resourceSet.getIconUri())
                .setName(resourceSet.getName())
                .setType(resourceSet.getType())
                .setDomain(resourceSet.getDomain())
                .setUserId(resourceSet.getUserId())
                .setClientId(resourceSet.getClientId());
        resourceSetMongo.setUpdatedAt(resourceSet.getUpdatedAt());
        resourceSetMongo.setCreatedAt(resourceSet.getCreatedAt());

        return resourceSetMongo;
    }
}
