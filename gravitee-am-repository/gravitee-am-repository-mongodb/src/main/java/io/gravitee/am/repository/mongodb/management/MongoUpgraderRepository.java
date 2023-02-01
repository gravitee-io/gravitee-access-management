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
import io.gravitee.am.repository.mongodb.management.internal.model.UpgraderMongo;
import io.gravitee.node.api.upgrader.UpgradeRecord;
import io.gravitee.node.api.upgrader.UpgraderRepository;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author Kamiel Ahmadpour (kamiel.ahmadpour at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoUpgraderRepository extends AbstractManagementMongoRepository implements UpgraderRepository {

    private MongoCollection<UpgraderMongo> upgradeRecordCollection;

    @PostConstruct
    public void init() {
        upgradeRecordCollection = mongoOperations.getCollection("upgraders", UpgraderMongo.class);
        super.init(upgradeRecordCollection);
        super.createIndex(upgradeRecordCollection, new Document(FIELD_ID, 1));
    }

    @Override
    public Maybe<UpgradeRecord> findById(String id) {
        return Observable.fromPublisher(upgradeRecordCollection.find(eq(FIELD_ID, id))
                .first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Single<UpgradeRecord> create(UpgradeRecord item) {
        UpgraderMongo upgraderMongo = convert(item);
        return Single.fromPublisher(upgradeRecordCollection.insertOne(upgraderMongo)).flatMap(success -> Single.just(item));
    }


    private UpgradeRecord convert(UpgraderMongo upgraderMongo) {
        if (upgraderMongo == null) {
            return null;
        }

        UpgradeRecord upgradeRecord = new UpgradeRecord();
        upgradeRecord.setId(upgraderMongo.getId());
        upgradeRecord.setAppliedAt(upgraderMongo.getAppliedAt());

        return upgradeRecord;
    }

    private UpgraderMongo convert(UpgradeRecord upgradeRecord) {
        if (upgradeRecord == null) {
            return null;
        }

        UpgraderMongo upgraderMongo = new UpgraderMongo();
        upgraderMongo.setId(upgradeRecord.getId());
        upgraderMongo.setAppliedAt(upgradeRecord.getAppliedAt());

        return upgraderMongo;
    }
}
