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
import io.gravitee.am.model.Factor;
import io.gravitee.am.repository.management.api.FactorRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.FactorMongo;
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

import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoFactorRepository extends AbstractManagementMongoRepository implements FactorRepository {

    private static final String FIELD_FACTOR_TYPE = "factorType";
    private MongoCollection<FactorMongo> factorsCollection;

    @PostConstruct
    public void init() {
        factorsCollection = mongoOperations.getCollection("factors", FactorMongo.class);
        super.init(factorsCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_DOMAIN, 1), new IndexOptions().name("d1"));
        indexes.put(new Document(FIELD_DOMAIN, 1).append(FIELD_FACTOR_TYPE, 1), new IndexOptions().name("d1f1"));

        super.createIndex(factorsCollection, indexes);
    }

    @Override
    public Flowable<Factor> findAll() {
        return Flowable.fromPublisher(withMaxTime(factorsCollection.find())).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Factor> findByDomain(String domain) {
        return Flowable.fromPublisher(factorsCollection.find(eq(FIELD_DOMAIN, domain))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Factor> findById(String factorId) {
        return Observable.fromPublisher(factorsCollection.find(eq(FIELD_ID, factorId)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Factor> create(Factor item) {
        FactorMongo authenticator = convert(item);
        authenticator.setId(authenticator.getId() == null ? RandomString.generate() : authenticator.getId());
        return Single.fromPublisher(factorsCollection.insertOne(authenticator)).flatMap(success -> { item.setId(authenticator.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Factor> update(Factor item) {
        FactorMongo authenticator = convert(item);
        return Single.fromPublisher(factorsCollection.replaceOne(eq(FIELD_ID, authenticator.getId()), authenticator)).flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(factorsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private Factor convert(FactorMongo factorMongo) {
        if (factorMongo == null) {
            return null;
        }

        Factor factor = new Factor();
        factor.setId(factorMongo.getId());
        factor.setName(factorMongo.getName());
        factor.setType(factorMongo.getType());
        factor.setFactorType(factorMongo.getFactorType());
        factor.setConfiguration(factorMongo.getConfiguration());
        factor.setDomain(factorMongo.getDomain());
        factor.setCreatedAt(factorMongo.getCreatedAt());
        factor.setUpdatedAt(factorMongo.getUpdatedAt());
        return factor;
    }

    private FactorMongo convert(Factor factor) {
        if (factor == null) {
            return null;
        }

        FactorMongo factorMongo = new FactorMongo();
        factorMongo.setId(factor.getId());
        factorMongo.setName(factor.getName());
        factorMongo.setType(factor.getType());
        factorMongo.setFactorType(factor.getFactorType().getType());
        factorMongo.setConfiguration(factor.getConfiguration());
        factorMongo.setDomain(factor.getDomain());
        factorMongo.setCreatedAt(factor.getCreatedAt());
        factorMongo.setUpdatedAt(factor.getUpdatedAt());
        return factorMongo;
    }
}
