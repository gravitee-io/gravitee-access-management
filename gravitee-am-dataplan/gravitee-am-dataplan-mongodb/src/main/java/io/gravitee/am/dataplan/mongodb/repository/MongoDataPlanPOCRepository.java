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

package io.gravitee.am.dataplan.mongodb.repository;


import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.dataplan.api.repository.DataPlanPOCRepository;
import io.gravitee.am.dataplan.mongodb.repository.model.POC;
import io.reactivex.rxjava3.core.Completable;

import java.util.UUID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoDataPlanPOCRepository implements DataPlanPOCRepository {

    private MongoCollection<POC> pocCollection;

    public MongoDataPlanPOCRepository(MongoDatabase db) {
        this.pocCollection = db.getCollection("poc", POC.class);
    }

    @Override
    public Completable writeValue(String value) {
        return Completable.fromPublisher(this.pocCollection.insertOne(new POC(UUID.randomUUID().toString(), value)));
    }
}
