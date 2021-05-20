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
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.repository.management.api.ScopeRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.ScopeMongo;
import io.reactivex.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoScopeRepository extends AbstractManagementMongoRepository implements ScopeRepository {

    private static final String FIELD_KEY = "key";
    private MongoCollection<ScopeMongo> scopesCollection;

    @PostConstruct
    public void init() {
        scopesCollection = mongoOperations.getCollection("scopes", ScopeMongo.class);
        super.init(scopesCollection);
        super.createIndex(scopesCollection, new Document(FIELD_DOMAIN, 1));
        super.createIndex(scopesCollection, new Document(FIELD_DOMAIN, 1).append(FIELD_KEY, 1));
    }

    @Override
    public Maybe<Scope> findById(String id) {
        return Observable.fromPublisher(scopesCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Scope> create(Scope item) {
        ScopeMongo scope = convert(item);
        scope.setId(scope.getId() == null ? RandomString.generate() : scope.getId());
        return Single.fromPublisher(scopesCollection.insertOne(scope)).flatMap(success -> findById(scope.getId()).toSingle());
    }

    @Override
    public Single<Scope> update(Scope item) {
        ScopeMongo scope = convert(item);
        return Single.fromPublisher(scopesCollection.replaceOne(eq(FIELD_ID, scope.getId()), scope)).flatMap(updateResult -> findById(scope.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(scopesCollection.deleteOne(eq(FIELD_ID, id)));
    }

    @Override
    public Single<Page<Scope>> findByDomain(String domain, int page, int size) {
        Bson mongoQuery = eq(FIELD_DOMAIN, domain);
        Single<Long> countOperation = Observable.fromPublisher(scopesCollection.countDocuments(mongoQuery)).first(0l);
        Single<List<Scope>> scopesOperation = Observable.fromPublisher(scopesCollection.find(mongoQuery).skip(size * page).limit(size)).map(this::convert).toList();
        return Single.zip(countOperation, scopesOperation, (count, scope) -> new Page<Scope>(scope, page, count));
    }

    @Override
    public Single<Page<Scope>> search(String domain, String query, int page, int size) {
        Bson searchQuery = eq(FIELD_KEY, query);

        // if query contains wildcard, use the regex query
        if (query.contains("*")) {
            String compactQuery = query.replaceAll("\\*+", ".*");
            String regex = "^" + compactQuery;
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            searchQuery = new BasicDBObject(FIELD_KEY, pattern);
        }

        Bson mongoQuery = and(
                eq(FIELD_DOMAIN, domain), searchQuery);

        Single<Long> countOperation = Observable.fromPublisher(scopesCollection.countDocuments(mongoQuery)).first(0l);
        Single<List<Scope>> scopesOperation = Observable.fromPublisher(scopesCollection.find(mongoQuery).sort(new BasicDBObject(FIELD_KEY, 1)).skip(size * page).limit(size)).map(this::convert).toList();
        return Single.zip(countOperation, scopesOperation, (count, scopes) -> new Page<>(scopes, page, count));
    }

    @Override
    public Maybe<Scope> findByDomainAndKey(String domain, String key) {
        return Observable.fromPublisher(scopesCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_KEY, key))).first()).firstElement().map(this::convert);
    }

    @Override
    public Flowable<Scope> findByDomainAndKeys(String domain, List<String> keys) {
        return Flowable.fromPublisher(scopesCollection.find(and(eq(FIELD_DOMAIN, domain), in(FIELD_KEY, keys)))).map(this::convert);
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
        scope.setIconUri(scopeMongo.getIconUri());
        scope.setDomain(scopeMongo.getDomain());
        scope.setSystem(scopeMongo.isSystem());
        scope.setClaims(scopeMongo.getClaims());
        scope.setExpiresIn(scopeMongo.getExpiresIn());
        scope.setCreatedAt(scopeMongo.getCreatedAt());
        scope.setUpdatedAt(scopeMongo.getUpdatedAt());
        scope.setDiscovery(scopeMongo.isDiscovery());

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
        scopeMongo.setIconUri(scope.getIconUri());
        scopeMongo.setDomain(scope.getDomain());
        scopeMongo.setSystem(scope.isSystem());
        scopeMongo.setClaims(scope.getClaims());
        scopeMongo.setExpiresIn(scope.getExpiresIn());
        scopeMongo.setCreatedAt(scope.getCreatedAt());
        scopeMongo.setUpdatedAt(scope.getUpdatedAt());
        scopeMongo.setDiscovery(scope.isDiscovery());

        return scopeMongo;
    }
}
