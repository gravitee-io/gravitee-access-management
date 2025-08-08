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
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.repository.management.api.ResourceRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.uma.ResourceMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Component
public class MongoResourceRepository extends AbstractManagementMongoRepository implements ResourceRepository {

    private static final String FIELD_CLIENT_ID = "clientId";
    private static final String FIELD_USER_ID = "userId";
    private static final String COLLECTION_NAME = "uma_resource_set";
    private MongoCollection<ResourceMongo> resourceCollection;

    @PostConstruct
    public void init() {
        resourceCollection = mongoOperations.getCollection(COLLECTION_NAME, ResourceMongo.class);
        super.init(resourceCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_DOMAIN, 1), new IndexOptions().name("d1"));
        indexes.put(new Document(FIELD_DOMAIN, 1).append(FIELD_CLIENT_ID, 1), new IndexOptions().name("d1c1"));
        indexes.put(new Document(FIELD_DOMAIN, 1).append(FIELD_CLIENT_ID, 1).append(FIELD_USER_ID, 1), new IndexOptions().name("d1c1u1"));

        super.createIndex(resourceCollection, indexes);
    }

    @Override
    public Single<Page<Resource>> findByDomainAndClient(String domain, String client, int page, int size) {
        Single<Long> countOperation = Observable.fromPublisher(resourceCollection.countDocuments(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT_ID, client)), countOptions())).first(0L);
        Single<List<Resource>> resourcesOperation = Observable.fromPublisher(withMaxTime(resourceCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT_ID, client)))).sort(new BasicDBObject(FIELD_UPDATED_AT, -1)).skip(size * page).limit(size)).map(this::convert).toList();
        return Single.zip(countOperation, resourcesOperation, (count, resourceSets) -> new Page<>(resourceSets, page, count))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Resource> findById(String id) {
        return Observable.fromPublisher(resourceCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Resource> create(Resource item) {
        ResourceMongo resource = convert(item);
        resource.setId(resource.getId() == null ? RandomString.generate() : resource.getId());
        return Single.fromPublisher(resourceCollection.insertOne(resource)).flatMap(success -> { item.setId(resource.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Resource> update(Resource item) {
        ResourceMongo resourceMongo = convert(item);
        return Single.fromPublisher(resourceCollection.replaceOne(eq(FIELD_ID, resourceMongo.getId()), resourceMongo)).flatMap(success -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(resourceCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Resource> findByDomainAndClientAndUser(String domain, String client, String user) {
        return Flowable.fromPublisher(resourceCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT_ID, client), eq(FIELD_USER_ID, user)))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<Resource>> findByDomain(String domain, int page, int size) {
        Single<Long> countOperation = Observable.fromPublisher(resourceCollection.countDocuments(eq(FIELD_DOMAIN, domain))).first(0l);
        Single<Set<Resource>> resourceSetOperation = Observable.fromPublisher(resourceCollection.find(eq(FIELD_DOMAIN, domain)).sort(new BasicDBObject(FIELD_UPDATED_AT, -1)).skip(size * page).limit(size)).map(this::convert).collect(HashSet::new, Set::add);
        return Single.zip(countOperation, resourceSetOperation, (count, resourceSet) -> new Page<>(resourceSet, page, count))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Resource> findByResources(List<String> resources) {
        return Flowable.fromPublisher(resourceCollection.find(in(FIELD_ID, resources))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Resource> findByDomainAndClientAndResources(String domain, String client, List<String> resources) {
        return Flowable.fromPublisher(resourceCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT_ID, client), in(FIELD_ID, resources)))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Resource> findByDomainAndClientAndUserAndResource(String domain, String client, String user, String resource) {
        return Observable.fromPublisher(resourceCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_CLIENT_ID, client), eq(FIELD_USER_ID, user), eq(FIELD_ID, resource))).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    private Resource convert(ResourceMongo resourceMongo) {
        return new Resource()
                .setId(resourceMongo.getId())
                .setResourceScopes(resourceMongo.getResourceScopes())
                .setDescription(resourceMongo.getDescription())
                .setIconUri(resourceMongo.getIconUri())
                .setName(resourceMongo.getName())
                .setType(resourceMongo.getType())
                .setDomain(resourceMongo.getDomain())
                .setUserId(resourceMongo.getUserId())
                .setClientId(resourceMongo.getClientId())
                .setUpdatedAt(resourceMongo.getUpdatedAt())
                .setCreatedAt(resourceMongo.getCreatedAt());
    }

    private ResourceMongo convert(Resource resource) {
        ResourceMongo resourceMongo = new ResourceMongo()
                .setId(resource.getId())
                .setResourceScopes(resource.getResourceScopes())
                .setDescription(resource.getDescription())
                .setIconUri(resource.getIconUri())
                .setName(resource.getName())
                .setType(resource.getType())
                .setDomain(resource.getDomain())
                .setUserId(resource.getUserId())
                .setClientId(resource.getClientId());
        resourceMongo.setUpdatedAt(resource.getUpdatedAt());
        resourceMongo.setCreatedAt(resource.getCreatedAt());

        return resourceMongo;
    }
}
