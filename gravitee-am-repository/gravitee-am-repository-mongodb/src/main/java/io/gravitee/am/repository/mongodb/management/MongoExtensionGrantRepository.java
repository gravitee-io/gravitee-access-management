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
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.am.repository.mongodb.common.IdGenerator;
import io.gravitee.am.repository.mongodb.management.internal.model.ExtensionGrantMongo;
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
import java.util.HashSet;
import java.util.Set;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoExtensionGrantRepository extends AbstractManagementMongoRepository implements ExtensionGrantRepository {

    private static final Logger logger = LoggerFactory.getLogger(MongoClientRepository.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_GRANT_TYPE = "grantType";
    private MongoCollection<ExtensionGrantMongo> extensionGrantsCollection;

    @Autowired
    private IdGenerator idGenerator;

    @PostConstruct
    public void init() {
        extensionGrantsCollection = mongoOperations.getCollection("extension_grants", ExtensionGrantMongo.class);
        extensionGrantsCollection.createIndex(new Document(FIELD_DOMAIN, 1)).subscribe(new IndexSubscriber());
        extensionGrantsCollection.createIndex(new Document(FIELD_DOMAIN, 1).append(FIELD_GRANT_TYPE, 1)).subscribe(new IndexSubscriber());
    }

    @Override
    public Single<Set<ExtensionGrant>> findByDomain(String domain) {
        return Observable.fromPublisher(extensionGrantsCollection.find(eq(FIELD_DOMAIN, domain))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Maybe<ExtensionGrant> findByDomainAndGrantType(String domain, String grantType) throws TechnicalException {
        return Single.fromPublisher(extensionGrantsCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_GRANT_TYPE, grantType))).first()).map(this::convert).toMaybe();
    }

    @Override
    public Maybe<ExtensionGrant> findById(String tokenGranterId) {
        return _findById(tokenGranterId).toMaybe();
    }

    @Override
    public Single<ExtensionGrant> create(ExtensionGrant item) {
        ExtensionGrantMongo extensionGrant = convert(item);
        extensionGrant.setId(extensionGrant.getId() == null ? (String) idGenerator.generate() : extensionGrant.getId());
        return Single.fromPublisher(extensionGrantsCollection.insertOne(extensionGrant)).flatMap(success -> _findById(extensionGrant.getId()));
    }

    @Override
    public Single<ExtensionGrant> update(ExtensionGrant item) {
        ExtensionGrantMongo extensionGrant = convert(item);
        return Single.fromPublisher(extensionGrantsCollection.replaceOne(eq(FIELD_ID, extensionGrant.getId()), extensionGrant)).flatMap(updateResult -> _findById(extensionGrant.getId()));
    }

    @Override
    public Single<Irrelevant> delete(String id) {
        return Single.fromPublisher(extensionGrantsCollection.deleteOne(eq(FIELD_ID, id))).map(deleteResult -> Irrelevant.EXTENSION_GRANT);
    }

    private Single<ExtensionGrant> _findById(String id) {
        return Single.fromPublisher(extensionGrantsCollection.find(eq(FIELD_ID, id)).first()).map(this::convert);
    }

    private ExtensionGrant convert(ExtensionGrantMongo extensionGrantMongo) {
        if (extensionGrantMongo == null) {
            return null;
        }

        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setId(extensionGrantMongo.getId());
        extensionGrant.setName(extensionGrantMongo.getName());
        extensionGrant.setType(extensionGrantMongo.getType());
        extensionGrant.setConfiguration(extensionGrantMongo.getConfiguration());
        extensionGrant.setDomain(extensionGrantMongo.getDomain());
        extensionGrant.setGrantType(extensionGrantMongo.getGrantType());
        extensionGrant.setIdentityProvider(extensionGrantMongo.getIdentityProvider());
        extensionGrant.setCreateUser(extensionGrantMongo.isCreateUser());
        extensionGrant.setCreatedAt(extensionGrantMongo.getCreatedAt());
        extensionGrant.setUpdatedAt(extensionGrantMongo.getUpdatedAt());
        return extensionGrant;
    }

    private ExtensionGrantMongo convert(ExtensionGrant extensionGrant) {
        if (extensionGrant == null) {
            return null;
        }

        ExtensionGrantMongo extensionGrantMongo = new ExtensionGrantMongo();
        extensionGrantMongo.setId(extensionGrant.getId());
        extensionGrantMongo.setName(extensionGrant.getName());
        extensionGrantMongo.setType(extensionGrant.getType());
        extensionGrantMongo.setConfiguration(extensionGrant.getConfiguration());
        extensionGrantMongo.setDomain(extensionGrant.getDomain());
        extensionGrantMongo.setGrantType(extensionGrant.getGrantType());
        extensionGrantMongo.setIdentityProvider(extensionGrant.getIdentityProvider());
        extensionGrantMongo.setCreateUser(extensionGrant.isCreateUser());
        extensionGrantMongo.setCreatedAt(extensionGrant.getCreatedAt());
        extensionGrantMongo.setUpdatedAt(extensionGrant.getUpdatedAt());
        return extensionGrantMongo;
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
