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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.repository.management.api.FlowRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.FlowMongo;
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

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoFlowRepository extends AbstractManagementMongoRepository implements FlowRepository {

    private MongoCollection<FlowMongo> flowsCollection;
    private static final String FIELD_APPLICATION = "application";

    @PostConstruct
    public void init() {
        flowsCollection = mongoOperations.getCollection("flows", FlowMongo.class);
        super.init(flowsCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1), new IndexOptions().name("rt1ri1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_APPLICATION, 1), new IndexOptions().name("rt1ri1a1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_ID, 1), new IndexOptions().name("rt1ri1id1"));

        super.createIndex(flowsCollection, indexes);
    }

    @Override
    public Flowable<Flow> findAll(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(withMaxTime(
                flowsCollection.find(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId)
                        )
                ))
        ).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Flow> findByApplication(ReferenceType referenceType, String referenceId, String application) {
        return Flowable.fromPublisher(withMaxTime(
                flowsCollection.find(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_APPLICATION, application)
                        )
                ))
        ).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Flow> findById(ReferenceType referenceType, String referenceId, String id) {
        return Observable.fromPublisher(
                flowsCollection.find(
                        and(
                                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                                eq(FIELD_REFERENCE_ID, referenceId),
                                eq(FIELD_ID, id)
                        )
                ).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<Flow> findById(String id) {
        return Observable.fromPublisher(flowsCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Flow> create(Flow item) {
        FlowMongo flow = convert(item);
        flow.setId(flow.getId() == null ? RandomString.generate() : flow.getId());
        return Single.fromPublisher(flowsCollection.insertOne(flow)).flatMap(success -> { item.setId(flow.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Flow> update(Flow item) {
        FlowMongo flow = convert(item);
        return Single.fromPublisher(flowsCollection.replaceOne(eq(FIELD_ID, flow.getId()), flow)).flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(flowsCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private FlowMongo convert(Flow flow) {
        if (flow == null) {
            return null;
        }

        FlowMongo flowMongo = new FlowMongo();
        flowMongo.setId(flow.getId());
        flowMongo.setReferenceType(flow.getReferenceType() == null ? null : flow.getReferenceType().toString());
        flowMongo.setReferenceId(flow.getReferenceId());
        flowMongo.setApplication(flow.getApplication());
        flowMongo.setName(flow.getName());
        flowMongo.setOrder(flow.getOrder());
        flowMongo.setPre(flow.getPre());
        flowMongo.setPost(flow.getPost());
        flowMongo.setEnabled(flow.isEnabled());
        flowMongo.setType(flow.getType() == null ? null : flow.getType().toString());
        flowMongo.setCondition(flow.getCondition());
        flowMongo.setCreatedAt(flow.getCreatedAt());
        flowMongo.setUpdatedAt(flow.getUpdatedAt());
        return flowMongo;
    }

    private Flow convert(FlowMongo flowMongo) {
        if (flowMongo == null) {
            return null;
        }

        Flow flow = new Flow();
        flow.setId(flowMongo.getId());
        flow.setReferenceType(flowMongo.getReferenceType() == null ? null : ReferenceType.valueOf(flowMongo.getReferenceType()));
        flow.setReferenceId(flowMongo.getReferenceId());
        flow.setApplication(flowMongo.getApplication());
        flow.setName(flowMongo.getName());
        flow.setOrder(flowMongo.getOrder());
        flow.setPre(flowMongo.getPre());
        flow.setPost(flowMongo.getPost());
        flow.setEnabled(flowMongo.isEnabled());
        flow.setType(flowMongo.getType() == null ? null : Type.valueOf(flowMongo.getType()));
        flow.setCondition(flowMongo.getCondition());
        flow.setCreatedAt(flowMongo.getCreatedAt());
        flow.setUpdatedAt(flowMongo.getUpdatedAt());

        return flow;
    }

}
