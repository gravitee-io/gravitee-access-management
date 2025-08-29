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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.alert.AlertTriggerType;
import io.gravitee.am.repository.management.api.AlertTriggerRepository;
import io.gravitee.am.repository.management.api.search.AlertTriggerCriteria;
import io.gravitee.am.repository.mongodb.management.internal.model.AlertTriggerMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.or;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoAlertTriggerRepository extends AbstractManagementMongoRepository implements AlertTriggerRepository {

    private MongoCollection<AlertTriggerMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("alert_triggers", AlertTriggerMongo.class);
        super.init(collection);
    }

    @Override
    public Maybe<AlertTrigger> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AlertTrigger> findAll(ReferenceType referenceType, String referenceId) {
        Bson eqReference = and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId));

        return Flowable.fromPublisher(withMaxTime(collection.find(eqReference)))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AlertTrigger> findByCriteria(ReferenceType referenceType, String referenceId, AlertTriggerCriteria criteria) {
        Bson eqReference = and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId));

        List<Bson> filters = new ArrayList<>();
        if (criteria.isEnabled().isPresent()) {
            filters.add(eq("enabled", criteria.isEnabled().get()));
        }

        if (criteria.getType().isPresent()) {
            filters.add(eq("type", criteria.getType().get().name()));
        }

        if (criteria.getAlertNotifierIds().isPresent() && !criteria.getAlertNotifierIds().get().isEmpty()) {
            filters.add(in("alertNotifiers", criteria.getAlertNotifierIds().get()));
        }

        Bson query = eqReference;
        if (!filters.isEmpty()) {
            query = and(eqReference, criteria.isLogicalOR() ? or(filters) : and(filters));
        }
        return Flowable.fromPublisher(withMaxTime(collection.find(query))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AlertTrigger> create(AlertTrigger item) {
        var alertTrigger = convert(item);
        alertTrigger.setId(item.getId() == null ? RandomString.generate() : item.getId());
        return Single.fromPublisher(collection.insertOne(alertTrigger))
                .flatMap(success -> {
                    item.setId(alertTrigger.getId());
                    return Single.just(item);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AlertTrigger> update(AlertTrigger alertTrigger) {
        AlertTriggerMongo alertTriggerMongo = convert(alertTrigger);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, alertTriggerMongo.getId()), alertTriggerMongo))
                .flatMap(updateResult -> Single.just(alertTrigger))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private AlertTrigger convert(AlertTriggerMongo alertTriggerMongo) {

        AlertTrigger alertTrigger = new AlertTrigger();
        alertTrigger.setId(alertTriggerMongo.getId());
        alertTrigger.setEnabled(alertTriggerMongo.isEnabled());
        alertTrigger.setReferenceType(alertTriggerMongo.getReferenceType() == null ? null : ReferenceType.valueOf(alertTriggerMongo.getReferenceType()));
        alertTrigger.setReferenceId(alertTriggerMongo.getReferenceId());
        alertTrigger.setType(alertTriggerMongo.getType() == null ? null : AlertTriggerType.valueOf(alertTriggerMongo.getType()));
        alertTrigger.setAlertNotifiers(alertTriggerMongo.getAlertNotifiers());
        alertTrigger.setCreatedAt(alertTriggerMongo.getCreatedAt());
        alertTrigger.setUpdatedAt(alertTriggerMongo.getUpdatedAt());

        return alertTrigger;
    }

    private AlertTriggerMongo convert(AlertTrigger alertTrigger) {

        AlertTriggerMongo alertTriggerMongo = new AlertTriggerMongo();
        alertTriggerMongo.setId(alertTrigger.getId());
        alertTriggerMongo.setEnabled(alertTrigger.isEnabled());
        alertTriggerMongo.setReferenceType(alertTrigger.getReferenceType() == null ? null : alertTrigger.getReferenceType().name());
        alertTriggerMongo.setReferenceId(alertTrigger.getReferenceId());
        alertTriggerMongo.setType(alertTrigger.getType() == null ? null : alertTrigger.getType().name());
        alertTriggerMongo.setAlertNotifiers(alertTrigger.getAlertNotifiers());
        alertTriggerMongo.setCreatedAt(alertTrigger.getCreatedAt());
        alertTriggerMongo.setUpdatedAt(alertTrigger.getUpdatedAt());

        return alertTriggerMongo;
    }
}
