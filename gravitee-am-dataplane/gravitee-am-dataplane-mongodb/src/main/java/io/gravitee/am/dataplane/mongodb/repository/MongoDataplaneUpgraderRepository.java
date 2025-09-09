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
package io.gravitee.am.dataplane.mongodb.repository;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.dataplane.mongodb.repository.model.UpgradeRecordMongo;
import io.gravitee.am.repository.mongodb.common.AbstractMongoRepository;
import io.gravitee.am.repository.upgrader.UpgraderTargets;
import io.gravitee.node.api.upgrader.UpgradeRecord;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import static com.mongodb.client.model.Filters.eq;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.upgrader.UpgraderTargets.DATAPLANE_UPGRADER_TARGET;

@Component
@Qualifier("dataplaneUpgraderRepository")
public class MongoDataplaneUpgraderRepository extends AbstractMongoRepository implements UpgraderRepository {

    @Autowired
    protected MongoDatabase mongoOperations;

    private MongoCollection<UpgradeRecordMongo> upgraderCollection;
    @PostConstruct
    public void init() {
        this.upgraderCollection = mongoOperations.getCollection(DATAPLANE_UPGRADER_TARGET, UpgradeRecordMongo.class);
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
