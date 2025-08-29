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
import io.gravitee.am.repository.mongodb.management.internal.model.UpgradeRecordMongo;
import io.gravitee.node.api.upgrader.UpgradeRecord;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import static com.mongodb.client.model.Filters.eq;

@Repository
public class MongoUpgraderRepository extends AbstractManagementMongoRepository implements UpgraderRepository {
    private MongoCollection<UpgradeRecordMongo> upgraderCollection;

    @PostConstruct
    public void init() {
        this.upgraderCollection = mongoOperations.getCollection("upgraders", UpgradeRecordMongo.class);
        super.init(upgraderCollection);
    }
    @Override
    public Maybe<UpgradeRecord> findById(String id) {
        return Maybe.fromPublisher(upgraderCollection.find(eq(FIELD_ID, id)).first())
                .map(UpgradeRecordMongo::from)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<UpgradeRecord> create(UpgradeRecord upgradeRecord) {
        return Single.fromPublisher(upgraderCollection.insertOne(UpgradeRecordMongo.from(upgradeRecord)))
                .flatMapMaybe(result -> findById(upgradeRecord.getId()))
                .toSingle()
                .observeOn(Schedulers.computation());
    }
}
