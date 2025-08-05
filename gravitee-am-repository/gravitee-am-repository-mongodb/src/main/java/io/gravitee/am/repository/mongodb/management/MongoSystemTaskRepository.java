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
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.SystemTaskMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoSystemTaskRepository extends AbstractManagementMongoRepository implements SystemTaskRepository {
    protected static final String FIELD_OPERATION_ID = "operationId";
    protected static final String FIELD_TYPE = "type";
    private MongoCollection<SystemTaskMongo> systemTaskCollection;

    @PostConstruct
    public void init() {
        systemTaskCollection = mongoOperations.getCollection("system_tasks", SystemTaskMongo.class);
        super.init(systemTaskCollection);
    }

    @Override
    public Maybe<SystemTask> findById(String id) {
        return Observable.fromPublisher(systemTaskCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(SystemTaskMongo::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<SystemTask> create(SystemTask item) {
        SystemTaskMongo task = SystemTaskMongo.convert(item);
        task.setId(task.getId() == null ? RandomString.generate() : task.getId());
        return Single.fromPublisher(systemTaskCollection.insertOne(task)).flatMap(success -> {
            item.setId(task.getId());
            return Single.just(item);
        })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<SystemTask> update(SystemTask item) {
        return Single.error(new IllegalStateException("SystemTask can't be updated without control on the operationId"));
    }

    @Override
    public Single<SystemTask> updateIf(SystemTask item, String operationId) {
        SystemTaskMongo task = SystemTaskMongo.convert(item);
        return Single.fromPublisher(systemTaskCollection.replaceOne(and(eq(FIELD_ID, task.getId()), eq(FIELD_OPERATION_ID, operationId)), task))
                .flatMap(updateResult -> updateResult.getModifiedCount() == 1 ? Single.just(item) : findById(task.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(systemTaskCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<SystemTask> findByType(String type) {
        return Flowable.fromPublisher(systemTaskCollection.find(eq(FIELD_TYPE, type))).map(SystemTaskMongo::convert)
                .observeOn(Schedulers.computation());
    }
}
