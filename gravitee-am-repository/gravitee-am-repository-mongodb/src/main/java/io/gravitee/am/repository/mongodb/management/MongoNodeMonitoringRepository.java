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
import io.gravitee.am.repository.mongodb.management.internal.model.MonitoringMongo;
import io.gravitee.node.api.Monitoring;
import io.gravitee.node.api.NodeMonitoringRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lte;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoNodeMonitoringRepository extends AbstractManagementMongoRepository implements NodeMonitoringRepository {

    private static final String FIELD_NODE_ID = "nodeId";
    private static final String FIELD_TYPE = "type";

    private MongoCollection<MonitoringMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("node_monitoring", MonitoringMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(new Document(FIELD_UPDATED_AT, 1), new IndexOptions().name("u1")));
        super.createIndex(collection, Map.of(new Document(FIELD_NODE_ID, 1).append(FIELD_TYPE, 1), new IndexOptions().name("n1t1")));
    }

    @Override
    public Maybe<Monitoring> findByNodeIdAndType(String nodeId, String type) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_NODE_ID, nodeId), eq(FIELD_TYPE, type))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Monitoring> create(Monitoring monitoring) {
        return Single.fromPublisher(collection.insertOne(convert(monitoring)))
                .map(success -> monitoring)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Monitoring> update(Monitoring monitoring) {
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, monitoring.getId()), convert(monitoring)))
                .map(updateResult -> monitoring)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<Monitoring> findByTypeAndTimeFrame(String type, long from, long to) {
        List<Bson> filters = new ArrayList<>();
        filters.add(eq(FIELD_TYPE, type));
        filters.add(gte(FIELD_UPDATED_AT, new Date(from)));

        if (to > from) {
            filters.add(lte(FIELD_UPDATED_AT, new Date(to)));
        }

        return Flowable.fromPublisher(withMaxTime(collection.find(and(filters)))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    private Monitoring convert(MonitoringMongo monitoringMongo) {

        final Monitoring monitoring = new Monitoring();
        monitoring.setId(monitoringMongo.getId());
        monitoring.setNodeId(monitoringMongo.getNodeId());
        monitoring.setType(monitoringMongo.getType());
        monitoring.setEvaluatedAt(monitoringMongo.getEvaluatedAt());
        monitoring.setCreatedAt(monitoringMongo.getCreatedAt());
        monitoring.setUpdatedAt(monitoringMongo.getUpdatedAt());
        monitoring.setPayload(monitoringMongo.getPayload());

        return monitoring;
    }

    private MonitoringMongo convert(Monitoring monitoring) {

        final MonitoringMongo monitoringMongo = new MonitoringMongo();
        monitoringMongo.setId(monitoring.getId());
        monitoringMongo.setNodeId(monitoring.getNodeId());
        monitoringMongo.setType(monitoring.getType());
        monitoringMongo.setEvaluatedAt(monitoring.getEvaluatedAt());
        monitoringMongo.setCreatedAt(monitoring.getCreatedAt());
        monitoringMongo.setUpdatedAt(monitoring.getUpdatedAt());
        monitoringMongo.setPayload(monitoring.getPayload());

        return monitoringMongo;
    }
}
