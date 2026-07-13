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
import io.gravitee.am.model.License;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.LicenseRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.LicenseMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.LicensePkMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoLicenseRepository extends AbstractManagementMongoRepository implements LicenseRepository {

    private static final String COLLECTION_NAME = "licenses";
    private static final String FIELD_ID_REFERENCE_TYPE = "_id.referenceType";
    private static final String FIELD_ID_REFERENCE_ID = "_id.referenceId";

    private MongoCollection<LicenseMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection(COLLECTION_NAME, LicenseMongo.class);
        super.init(collection);
    }

    @Override
    public Flowable<License> findAll() {
        return Flowable.fromPublisher(withMaxTime(collection.find()))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<License> findById(String referenceId, ReferenceType referenceType) {
        return Observable.fromPublisher(collection.find(referenceFilter(referenceId, referenceType)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<License> create(License item) {
        return Single.fromPublisher(collection.insertOne(convert(item)))
                .map(success -> item)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<License> update(License item) {
        return Single.fromPublisher(collection.replaceOne(referenceFilter(item.getReferenceId(), item.getReferenceType()), convert(item)))
                .map(updateResult -> item)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String referenceId, ReferenceType referenceType) {
        return Completable.fromPublisher(collection.deleteOne(referenceFilter(referenceId, referenceType)))
                .observeOn(Schedulers.computation());
    }

    private Bson referenceFilter(String referenceId, ReferenceType referenceType) {
        return and(
                eq(FIELD_ID_REFERENCE_TYPE, referenceType == null ? null : referenceType.name()),
                eq(FIELD_ID_REFERENCE_ID, referenceId));
    }

    private License convert(LicenseMongo licenseMongo) {
        if (licenseMongo == null) {
            return null;
        }
        License license = new License();
        LicensePkMongo pk = licenseMongo.getId();
        if (pk != null) {
            license.setReferenceId(pk.getReferenceId());
            license.setReferenceType(pk.getReferenceType() == null ? null : ReferenceType.valueOf(pk.getReferenceType()));
        }
        license.setLicense(licenseMongo.getLicense());
        license.setCreatedAt(licenseMongo.getCreatedAt());
        license.setUpdatedAt(licenseMongo.getUpdatedAt());
        return license;
    }

    private LicenseMongo convert(License license) {
        if (license == null) {
            return null;
        }
        LicenseMongo licenseMongo = new LicenseMongo();
        licenseMongo.setId(new LicensePkMongo(
                license.getReferenceType() == null ? null : license.getReferenceType().name(),
                license.getReferenceId()));
        licenseMongo.setLicense(license.getLicense());
        licenseMongo.setCreatedAt(license.getCreatedAt());
        licenseMongo.setUpdatedAt(license.getUpdatedAt());
        return licenseMongo;
    }
}
