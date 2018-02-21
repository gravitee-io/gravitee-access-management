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
import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.repository.management.api.ScopeRepository;
import io.gravitee.am.repository.mongodb.common.IdGenerator;
import io.gravitee.am.repository.mongodb.management.internal.model.ScopeMongo;
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
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoScopeRepository extends AbstractManagementMongoRepository implements ScopeRepository {

    private static final Logger logger = LoggerFactory.getLogger(MongoScopeRepository.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_KEY = "key";
    private MongoCollection<ScopeMongo> scopesCollection;

    @Autowired
    private IdGenerator idGenerator;

    @PostConstruct
    public void init() {
        scopesCollection = mongoOperations.getCollection("scopes", ScopeMongo.class);
        scopesCollection.createIndex(new Document(FIELD_DOMAIN, 1)).subscribe(new IndexSubscriber());
        scopesCollection.createIndex(new Document(FIELD_DOMAIN, 1).append(FIELD_KEY, 1)).subscribe(new IndexSubscriber());
    }

    @Override
    public Maybe<Scope> findById(String id) {
        return _findById(id).toMaybe();
    }

    @Override
    public Single<Scope> create(Scope item) {
        ScopeMongo scope = convert(item);
        scope.setId(scope.getId() == null ? (String) idGenerator.generate() : scope.getId());
        return Single.fromPublisher(scopesCollection.insertOne(scope)).flatMap(success -> _findById(scope.getId()));
    }

    @Override
    public Single<Scope> update(Scope item) {
        ScopeMongo scope = convert(item);
        return Single.fromPublisher(scopesCollection.replaceOne(eq(FIELD_ID, scope.getId()), scope)).flatMap(updateResult -> _findById(scope.getId()));
    }

    @Override
    public Single<Irrelevant> delete(String id) {
        return Single.fromPublisher(scopesCollection.deleteOne(eq(FIELD_ID, id))).map(deleteResult -> Irrelevant.SCOPE);
    }

    @Override
    public Single<Set<Scope>> findByDomain(String domain) {
        return Observable.fromPublisher(scopesCollection.find(eq(FIELD_DOMAIN, domain))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Maybe<Scope> findByDomainAndKey(String domain, String key) {
        return Single.fromPublisher(scopesCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_KEY, key))).first()).map(this::convert).toMaybe();
    }

    private Single<Scope> _findById(String id) {
        return Single.fromPublisher(scopesCollection.find(eq(FIELD_ID, id)).first()).map(this::convert);
    }

    private Scope convert(ScopeMongo scopeMongo) {
        if (scopeMongo == null) {
            return null;
        }

        Scope scope = new Scope();
        scope.setId(scopeMongo.getId());
        scope.setKey(scopeMongo.getKey());
        scope.setName(scopeMongo.getName());
        scope.setDescription(scopeMongo.getDescription());
        scope.setDomain(scopeMongo.getDomain());
        scope.setNames(scopeMongo.getNames());
        scope.setDescriptions(scopeMongo.getDescriptions());
        scope.setCreatedAt(scopeMongo.getCreatedAt());
        scope.setUpdatedAt(scopeMongo.getUpdatedAt());

        return scope;
    }

    private ScopeMongo convert(Scope scope) {
        if (scope == null) {
            return null;
        }

        ScopeMongo scopeMongo = new ScopeMongo();
        scopeMongo.setId(scope.getId());
        scopeMongo.setKey(scope.getKey());
        scopeMongo.setName(scope.getName());
        scopeMongo.setDescription(scope.getDescription());
        scopeMongo.setDomain(scope.getDomain());
        scopeMongo.setNames(scope.getNames());
        scopeMongo.setDescriptions(scope.getDescriptions());
        scopeMongo.setCreatedAt(scope.getCreatedAt());
        scopeMongo.setUpdatedAt(scope.getUpdatedAt());

        return scopeMongo;
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
