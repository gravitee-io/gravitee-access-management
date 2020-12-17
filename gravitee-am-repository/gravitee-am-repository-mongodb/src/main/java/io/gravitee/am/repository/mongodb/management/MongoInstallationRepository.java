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
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.management.api.InstallationRepository;
import io.gravitee.am.repository.management.api.InstallationRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.InstallationMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoInstallationRepository extends AbstractManagementMongoRepository implements InstallationRepository {

    public static final String FIELD_ID = "_id";
    private MongoCollection<InstallationMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("installation", InstallationMongo.class);
        super.createIndex(collection, new Document(FIELD_ID, 1));
    }

    @Override
    public Maybe<Installation> findById(String id) {

        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Maybe<Installation> find() {

        return Observable.fromPublisher(collection.find().first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Single<Installation> create(Installation installation) {

        installation.setId(installation.getId() == null ? RandomString.generate() : installation.getId());

        return Single.fromPublisher(collection.insertOne(convert(installation)))
                .flatMap(success -> findById(installation.getId()).toSingle());
    }

    @Override
    public Single<Installation> update(Installation installation) {

        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, installation.getId()), convert(installation)))
                .flatMap(updateResult -> findById(installation.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)));
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