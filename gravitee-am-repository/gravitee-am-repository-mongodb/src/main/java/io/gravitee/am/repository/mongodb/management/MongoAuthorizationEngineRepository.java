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
import io.gravitee.am.model.AuthorizationEngine;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.AuthorizationEngineRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AuthorizationEngineMongo;
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

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoAuthorizationEngineRepository extends AbstractManagementMongoRepository implements AuthorizationEngineRepository {

    protected static final String FIELD_TYPE = "type";

    private MongoCollection<AuthorizationEngineMongo> authorizationEnginesCollection;

    @PostConstruct
    public void init() {
        authorizationEnginesCollection = mongoOperations.getCollection("authorization_engines", AuthorizationEngineMongo.class);
        super.init(authorizationEnginesCollection);
        super.createIndex(authorizationEnginesCollection, Map.of(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1), new IndexOptions().name("rt1ri1")));
    }

    @Override
    public Maybe<AuthorizationEngine> findById(String id) {
        return Observable.fromPublisher(authorizationEnginesCollection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AuthorizationEngine> findAll() {
        return Flowable.fromPublisher(withMaxTime(authorizationEnginesCollection.find()))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AuthorizationEngine> findByDomain(String domain) {
        return Flowable.fromPublisher(withMaxTime(authorizationEnginesCollection.find(and(eq(FIELD_REFERENCE_TYPE, ReferenceType.DOMAIN.name()), eq(FIELD_REFERENCE_ID, domain)))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationEngine> findByDomainAndId(String domainId, String id) {
        return Observable.fromPublisher(authorizationEnginesCollection.find(and(eq(FIELD_REFERENCE_TYPE, ReferenceType.DOMAIN.name()), eq(FIELD_REFERENCE_ID, domainId), eq(FIELD_ID, id))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationEngine> findByDomainAndType(String domainId, String type) {
        return Observable.fromPublisher(authorizationEnginesCollection.find(and(eq(FIELD_REFERENCE_TYPE, ReferenceType.DOMAIN.name()), eq(FIELD_REFERENCE_ID, domainId), eq(FIELD_TYPE, type))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationEngine> create(AuthorizationEngine item) {
        AuthorizationEngineMongo authorizationEngine = convert(item);
        authorizationEngine.setId(authorizationEngine.getId() == null ? RandomString.generate() : authorizationEngine.getId());
        return Single.fromPublisher(authorizationEnginesCollection.insertOne(authorizationEngine))
                .flatMap(success -> {
                    item.setId(authorizationEngine.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationEngine> update(AuthorizationEngine item) {
        AuthorizationEngineMongo authorizationEngine = convert(item);
        return Single.fromPublisher(authorizationEnginesCollection.replaceOne(eq(FIELD_ID, authorizationEngine.getId()), authorizationEngine))
                .flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(authorizationEnginesCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        return Completable.fromPublisher(authorizationEnginesCollection.deleteMany(and(eq(FIELD_REFERENCE_TYPE, ReferenceType.DOMAIN.name()), eq(FIELD_REFERENCE_ID, domainId))))
                .observeOn(Schedulers.computation());
    }

    private AuthorizationEngine convert(AuthorizationEngineMongo authorizationEngineMongo) {
        if (authorizationEngineMongo == null) {
            return null;
        }

        AuthorizationEngine authorizationEngine = new AuthorizationEngine();
        authorizationEngine.setId(authorizationEngineMongo.getId());
        authorizationEngine.setName(authorizationEngineMongo.getName());
        authorizationEngine.setType(authorizationEngineMongo.getType());
        authorizationEngine.setConfiguration(authorizationEngineMongo.getConfiguration());
        authorizationEngine.setReferenceType(authorizationEngineMongo.getReferenceType());
        authorizationEngine.setReferenceId(authorizationEngineMongo.getReferenceId());
        authorizationEngine.setCreatedAt(authorizationEngineMongo.getCreatedAt());
        authorizationEngine.setUpdatedAt(authorizationEngineMongo.getUpdatedAt());

        return authorizationEngine;
    }

    private AuthorizationEngineMongo convert(AuthorizationEngine authorizationEngine) {
        if (authorizationEngine == null) {
            return null;
        }

        AuthorizationEngineMongo authorizationEngineMongo = new AuthorizationEngineMongo();
        authorizationEngineMongo.setId(authorizationEngine.getId());
        authorizationEngineMongo.setName(authorizationEngine.getName());
        authorizationEngineMongo.setType(authorizationEngine.getType());
        authorizationEngineMongo.setConfiguration(authorizationEngine.getConfiguration());
        authorizationEngineMongo.setReferenceType(authorizationEngine.getReferenceType());
        authorizationEngineMongo.setReferenceId(authorizationEngine.getReferenceId());
        authorizationEngineMongo.setCreatedAt(authorizationEngine.getCreatedAt());
        authorizationEngineMongo.setUpdatedAt(authorizationEngine.getUpdatedAt());

        return authorizationEngineMongo;
    }
}
