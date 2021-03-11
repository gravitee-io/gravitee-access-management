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
import io.reactivex.*;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

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
                .map(this::convert);
    }

    @Override
    public Flowable<AlertTrigger> findByDomain(String domainId) {
        return findAll(ReferenceType.DOMAIN, domainId);
    }

    @Override
    public Flowable<AlertTrigger> findAll(ReferenceType referenceType, String referenceId) {
        Bson eqReference = and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId));

        return Flowable.fromPublisher(collection.find(eqReference))
                .map(this::convert);
    }

    @Override
    public Flowable<AlertTrigger> findByCriteria(ReferenceType referenceType, String referenceId, AlertTriggerCriteria criteria) {

        Bson eqReference = and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId));
        Bson eqEnabled = toBsonFilter("enabled", criteria.isEnabled());
        Bson eqType = toBsonFilter("type", criteria.getType());
        Bson eqAlertNotifierIdsType = toBsonFilter("alertNotifiers", criteria.getAlertNotifierIds());

        return toBsonFilter(criteria.isLogicalOR(), eqEnabled, eqType, eqAlertNotifierIdsType)
                .switchIfEmpty(Single.just(new BsonDocument()))
                .flatMapPublisher(filter -> Flowable.fromPublisher(collection.find(and(eqReference, filter)))).map(this::convert);
    }

    @Override
    public Single<AlertTrigger> create(AlertTrigger alertTrigger) {
        alertTrigger.setId(alertTrigger.getId() == null ? RandomString.generate() : alertTrigger.getId());
        return Single.fromPublisher(collection.insertOne(convert(alertTrigger)))
                .flatMap(success -> findById(alertTrigger.getId()).toSingle());
    }

    @Override
    public Single<AlertTrigger> update(AlertTrigger alertTrigger) {
        AlertTriggerMongo alertTriggerMongo = convert(alertTrigger);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, alertTriggerMongo.getId()), alertTriggerMongo))
                .flatMap(updateResult -> findById(alertTriggerMongo.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)));
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
