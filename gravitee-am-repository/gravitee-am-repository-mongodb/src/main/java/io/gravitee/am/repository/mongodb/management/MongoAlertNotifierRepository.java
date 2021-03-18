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
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.repository.management.api.AlertNotifierRepository;
import io.gravitee.am.repository.management.api.search.AlertNotifierCriteria;
import io.gravitee.am.repository.mongodb.management.internal.model.AlertNotifierMongo;
import io.reactivex.*;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static com.mongodb.client.model.Filters.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoAlertNotifierRepository extends AbstractManagementMongoRepository implements AlertNotifierRepository {

    private MongoCollection<AlertNotifierMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection("alert_notifiers", AlertNotifierMongo.class);
        super.init(collection);
    }

    @Override
    public Maybe<AlertNotifier> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert);
    }


    @Override
    public Flowable<AlertNotifier> findByDomain(String domainId) {
        return findAll(ReferenceType.DOMAIN, domainId);
    }

    @Override
    public Flowable<AlertNotifier> findAll(ReferenceType referenceType, String referenceId) {
        Bson eqReference = and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId));

        return Flowable.fromPublisher(collection.find(eqReference))
                .map(this::convert);
    }

    @Override
    public Flowable<AlertNotifier> findByCriteria(ReferenceType referenceType, String referenceId, AlertNotifierCriteria criteria) {

        Bson eqReference = and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId));
        Bson eqEnabled = toBsonFilter("enabled", criteria.isEnabled());
        Bson inIds = toBsonFilter("_id", criteria.getIds());

        return toBsonFilter(criteria.isLogicalOR(), eqEnabled, inIds)
                .switchIfEmpty(Single.just(new BsonDocument()))
                .flatMapPublisher(filter -> Flowable.fromPublisher(collection.find(and(eqReference, filter)))).map(this::convert);
    }

    @Override
    public Single<AlertNotifier> create(AlertNotifier alertNotifier) {
        alertNotifier.setId(alertNotifier.getId() == null ? RandomString.generate() : alertNotifier.getId());
        return Single.fromPublisher(collection.insertOne(convert(alertNotifier)))
                .flatMap(success -> findById(alertNotifier.getId()).toSingle());
    }

    @Override
    public Single<AlertNotifier> update(AlertNotifier alertNotifier) {
        AlertNotifierMongo alertNotifierMongo = convert(alertNotifier);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, alertNotifierMongo.getId()), alertNotifierMongo))
                .flatMap(updateResult -> findById(alertNotifierMongo.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)));
    }

    private AlertNotifier convert(AlertNotifierMongo alertNotifierMongo) {

        AlertNotifier alertNotifier = new AlertNotifier();
        alertNotifier.setId(alertNotifierMongo.getId());
        alertNotifier.setName(alertNotifierMongo.getName());
        alertNotifier.setEnabled(alertNotifierMongo.isEnabled());
        alertNotifier.setReferenceType(alertNotifierMongo.getReferenceType() == null ? null : ReferenceType.valueOf(alertNotifierMongo.getReferenceType()));
        alertNotifier.setReferenceId(alertNotifierMongo.getReferenceId());
        alertNotifier.setType(alertNotifierMongo.getType());
        alertNotifier.setConfiguration(alertNotifierMongo.getConfiguration());
        alertNotifier.setCreatedAt(alertNotifierMongo.getCreatedAt());
        alertNotifier.setUpdatedAt(alertNotifierMongo.getUpdatedAt());

        return alertNotifier;
    }

    private AlertNotifierMongo convert(AlertNotifier alertNotifier) {

        AlertNotifierMongo alertNotifierMongo = new AlertNotifierMongo();
        alertNotifierMongo.setId(alertNotifier.getId());
        alertNotifierMongo.setName(alertNotifier.getName());
        alertNotifierMongo.setEnabled(alertNotifier.isEnabled());
        alertNotifierMongo.setReferenceType(alertNotifier.getReferenceType() == null ? null : alertNotifier.getReferenceType().name());
        alertNotifierMongo.setReferenceId(alertNotifier.getReferenceId());
        alertNotifierMongo.setType(alertNotifier.getType());
        alertNotifierMongo.setConfiguration(alertNotifier.getConfiguration());
        alertNotifierMongo.setCreatedAt(alertNotifier.getCreatedAt());
        alertNotifierMongo.setUpdatedAt(alertNotifier.getUpdatedAt());

        return alertNotifierMongo;
    }
}
