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
import io.gravitee.am.model.I18nDictionary;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.I18nDictionaryRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.I18nDictionaryMongo;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.util.Objects;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.common.utils.RandomString.generate;
import static io.gravitee.am.model.ReferenceType.valueOf;

@Component
public class MongoI18nDictionaryRepository extends AbstractManagementMongoRepository implements I18nDictionaryRepository {

    private MongoCollection<I18nDictionaryMongo> mongoCollection;

    @PostConstruct
    public void init() {
        mongoCollection = mongoOperations.getCollection("i18n_dictionaries", I18nDictionaryMongo.class);
        super.init(mongoCollection);
        super.createIndex(mongoCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1));
        super.createIndex(mongoCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1)
                                                                                .append(FIELD_NAME, 1));
    }

    @Override
    public Flowable<I18nDictionary> findAll(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(
                mongoCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId))))
                .map(this::convert);
    }

    @Override
    public Maybe<I18nDictionary> findById(String id) {
        return Observable.fromPublisher(mongoCollection.find(eq(FIELD_ID, id)).first()).firstElement()
                         .map(this::convert);
    }

    @Override
    public Single<I18nDictionary> create(I18nDictionary item) {
        Objects.requireNonNull(item);
        var dictionaryMongo = convert(item);
        dictionaryMongo.setId(dictionaryMongo.getId() == null ? generate() : dictionaryMongo.getId());
        return Single.fromPublisher(mongoCollection.insertOne(dictionaryMongo)).flatMap(success -> {
            item.setId(dictionaryMongo.getId());
            return Single.just(item);
        });
    }

    @Override
    public Single<I18nDictionary> update(I18nDictionary item) {
        Objects.requireNonNull(item);
        var dictionaryMongo = convert(item);
        return Single.fromPublisher(mongoCollection.replaceOne(eq(FIELD_ID, dictionaryMongo.getId()), dictionaryMongo))
                     .flatMap(success -> Single.just(item));
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(mongoCollection.deleteOne(eq(FIELD_ID, id)));
    }

    @Override
    public Maybe<I18nDictionary> findByName(ReferenceType referenceType, String referenceId, String name) {
        return Observable.fromPublisher(
                                 mongoCollection
                                         .find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_NAME, name)))
                                         .limit(1)
                                         .first())
                         .firstElement()
                         .map(this::convert);

    }

    @Override
    public Maybe<I18nDictionary> findById(ReferenceType referenceType, String referenceId, String id) {
        return Observable.fromPublisher(mongoCollection
                                                .find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_ID, id)))
                                                .first()).firstElement().map(this::convert);
    }

    private I18nDictionary convert(I18nDictionaryMongo dictionaryMongo) {
        if (dictionaryMongo == null) {
            return null;
        }
        var dictionary = new I18nDictionary();
        dictionary.setId(dictionaryMongo.getId());
        dictionary.setReferenceId(dictionaryMongo.getReferenceId());
        dictionary.setReferenceType(valueOf(dictionaryMongo.getReferenceType()));
        dictionary.setName(dictionaryMongo.getName());
        dictionary.setLocale(dictionaryMongo.getLocale());
        dictionary.setCreatedAt(dictionaryMongo.getCreatedAt());
        dictionary.setUpdatedAt(dictionaryMongo.getUpdatedAt());
        dictionary.setEntries(dictionaryMongo.getEntries());
        return dictionary;
    }

    private I18nDictionaryMongo convert(I18nDictionary dictionary) {
        if (dictionary == null) {
            return null;
        }
        var dictionaryMongo = new I18nDictionaryMongo();
        dictionaryMongo.setId(dictionary.getId());
        dictionaryMongo.setReferenceId(dictionary.getReferenceId());
        dictionaryMongo.setReferenceType(dictionary.getReferenceType().toString());
        dictionaryMongo.setName(dictionary.getName());
        dictionaryMongo.setLocale(dictionary.getLocale());
        dictionaryMongo.setCreatedAt(dictionary.getCreatedAt());
        dictionaryMongo.setUpdatedAt(dictionary.getUpdatedAt());
        dictionaryMongo.setEntries(dictionary.getEntries());
        return dictionaryMongo;
    }
}
