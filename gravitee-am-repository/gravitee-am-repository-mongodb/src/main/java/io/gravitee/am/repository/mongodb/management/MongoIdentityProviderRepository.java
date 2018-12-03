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
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.mongodb.common.IdGenerator;
import io.gravitee.am.repository.mongodb.common.LoggableIndexSubscriber;
import io.gravitee.am.repository.mongodb.management.internal.model.IdentityProviderMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoIdentityProviderRepository extends AbstractManagementMongoRepository implements IdentityProviderRepository {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private MongoCollection<IdentityProviderMongo> identitiesCollection;

    @Autowired
    private IdGenerator idGenerator;

    @PostConstruct
    public void init() {
        identitiesCollection = mongoOperations.getCollection("identities", IdentityProviderMongo.class);
        identitiesCollection.createIndex(new Document(FIELD_DOMAIN, 1)).subscribe(new LoggableIndexSubscriber());
    }

    @Override
    public Single<Set<IdentityProvider>> findAll() {
        return Observable.fromPublisher(identitiesCollection.find()).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Set<IdentityProvider>> findByDomain(String domain) {
        return Observable.fromPublisher(identitiesCollection.find(eq(FIELD_DOMAIN, domain))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Maybe<IdentityProvider> findById(String identityProviderId) {
        return Observable.fromPublisher(identitiesCollection.find(eq(FIELD_ID, identityProviderId)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<IdentityProvider> create(IdentityProvider item) {
        IdentityProviderMongo identityProvider = convert(item);
        identityProvider.setId(identityProvider.getId() == null ? (String) idGenerator.generate() : identityProvider.getId());
        return Single.fromPublisher(identitiesCollection.insertOne(identityProvider)).flatMap(success -> findById(identityProvider.getId()).toSingle());
    }

    @Override
    public Single<IdentityProvider> update(IdentityProvider item) {
        IdentityProviderMongo identityProvider = convert(item);
        return Single.fromPublisher(identitiesCollection.replaceOne(eq(FIELD_ID, identityProvider.getId()), identityProvider)).flatMap(updateResult -> findById(identityProvider.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(identitiesCollection.deleteOne(eq(FIELD_ID, id)));
    }

    private IdentityProvider convert(IdentityProviderMongo identityProviderMongo) {
        if (identityProviderMongo == null) {
            return null;
        }

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId(identityProviderMongo.getId());
        identityProvider.setName(identityProviderMongo.getName());
        identityProvider.setType(identityProviderMongo.getType());
        identityProvider.setConfiguration(identityProviderMongo.getConfiguration());
        identityProvider.setMappers((Map) identityProviderMongo.getMappers());

        if (identityProviderMongo.getRoleMapper() != null) {
            Map<String, String[]> roleMapper = new HashMap<>(identityProviderMongo.getRoleMapper().size());
            identityProviderMongo.getRoleMapper().forEach((key, value) -> {
                List lstValue = (List) value;
                String[] arr = new String[lstValue.size()];
                lstValue.toArray(arr);
                roleMapper.put(key, arr);
            });
            identityProvider.setRoleMapper(roleMapper);
        }

        identityProvider.setDomain(identityProviderMongo.getDomain());
        identityProvider.setExternal(identityProviderMongo.isExternal());
        identityProvider.setCreatedAt(identityProviderMongo.getCreatedAt());
        identityProvider.setUpdatedAt(identityProviderMongo.getUpdatedAt());
        return identityProvider;
    }

    private IdentityProviderMongo convert(IdentityProvider identityProvider) {
        if (identityProvider == null) {
            return null;
        }

        IdentityProviderMongo identityProviderMongo = new IdentityProviderMongo();
        identityProviderMongo.setId(identityProvider.getId());
        identityProviderMongo.setName(identityProvider.getName());
        identityProviderMongo.setType(identityProvider.getType());
        identityProviderMongo.setConfiguration(identityProvider.getConfiguration());
        identityProviderMongo.setMappers(identityProvider.getMappers() != null ? new Document((Map) identityProvider.getMappers()) : new Document());
        identityProviderMongo.setRoleMapper(identityProvider.getRoleMapper() != null ? convert(identityProvider.getRoleMapper()) : new Document());
        identityProviderMongo.setDomain(identityProvider.getDomain());
        identityProviderMongo.setExternal(identityProvider.isExternal());
        identityProviderMongo.setCreatedAt(identityProvider.getCreatedAt());
        identityProviderMongo.setUpdatedAt(identityProvider.getUpdatedAt());
        return identityProviderMongo;
    }

    private Document convert(Map<String, String[]> map) {
        Document document = new Document();
        map.forEach((k, v) -> document.append(k, Arrays.asList(v)));
        return document;
    }
}
