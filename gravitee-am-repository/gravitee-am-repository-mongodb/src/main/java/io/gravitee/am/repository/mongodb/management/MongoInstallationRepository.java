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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Installation;
import io.gravitee.am.repository.management.api.InstallationRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.InstallationMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoInstallationRepository extends AbstractManagementMongoRepository implements InstallationRepository {

    private MongoCollection<InstallationMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("installation", InstallationMongo.class);
        super.init(collection);
    }

    @Override
    public Maybe<Installation> findById(String id) {

        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Installation> find() {

        return Observable.fromPublisher(collection.find().first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Installation> create(Installation item) {
        var installation = convert(item);
        installation.setId(item.getId() == null ? RandomString.generate() : item.getId());
        return Single.fromPublisher(collection.insertOne(installation))
                .flatMap(success -> { item.setId(item.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Installation> update(Installation item) {
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, item.getId()), convert(item)))
                .flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private Installation convert(InstallationMongo installationMongo) {

        Installation installation = new Installation();
        installation.setId(installationMongo.getId());
        installation.setAdditionalInformation(installationMongo.getAdditionalInformation());
        installation.setCreatedAt(installationMongo.getCreatedAt());
        installation.setUpdatedAt(installationMongo.getUpdatedAt());

        return installation;
    }

    private InstallationMongo convert(Installation installation) {

        InstallationMongo installationMongo = new InstallationMongo();
        installationMongo.setId(installation.getId());
        installationMongo.setAdditionalInformation(installation.getAdditionalInformation());
        installationMongo.setCreatedAt(installation.getCreatedAt());
        installationMongo.setUpdatedAt(installation.getUpdatedAt());

        return installationMongo;
    }
}
