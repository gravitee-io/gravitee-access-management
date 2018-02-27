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
import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.mongodb.common.IdGenerator;
import io.gravitee.am.repository.mongodb.management.internal.model.IdentityProviderMongo;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subscribers.DefaultSubscriber;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoIdentityProviderRepository extends AbstractManagementMongoRepository implements IdentityProviderRepository {

    private static final Logger logger = LoggerFactory.getLogger(MongoIdentityProviderRepository.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private MongoCollection<IdentityProviderMongo> identitiesCollection;

    @Autowired
    private IdGenerator idGenerator;

    @PostConstruct
    public void init() {
        identitiesCollection = mongoOperations.getCollection("identities", IdentityProviderMongo.class);
        identitiesCollection.createIndex(new Document(FIELD_DOMAIN, 1)).subscribe(new IndexSubscriber());
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
    public Single<Irrelevant> delete(String id) {
        return Single.fromPublisher(identitiesCollection.deleteOne(eq(FIELD_ID, id))).map(deleteResult -> Irrelevant.IDENTITY_PROVIDER);
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
        identityProvider.setRoleMapper((Map) identityProviderMongo.getRoleMapper());
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
        identityProviderMongo.setRoleMapper(identityProvider.getMappers() != null ? convert(identityProvider.getRoleMapper()) : new Document());
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

    private class IndexSubscriber extends DefaultSubscriber<String> {
        @Override
        public void onNext(String value) {
            logger.debug("Created an index named : " + value);
        }

        @Override
        public void onError(Throwable throwable) {
            logger.error("Error occurs during indexing", throwable);
        }

        @Override
        public void onComplete() {
            logger.debug("Index creation complete");
        }
    }

}
