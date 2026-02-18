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
package io.gravitee.am.repository.mongodb.gateway;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.ActionLease;
import io.gravitee.am.repository.gateway.api.ActionLeaseRepository;
import io.gravitee.am.repository.mongodb.gateway.internal.model.ActionLeaseMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.or;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoActionLeaseRepository extends AbstractGatewayMongoRepository implements ActionLeaseRepository {
    private static final String ACTION_LEASE = "dp_action_lease";
    private static final String FIELD_ACTION = "action";
    private static final String FIELD_NODE_ID = "nodeId";
    private static final String FIELD_EXPIRY_DATE = "expiryDate";

    private MongoCollection<ActionLeaseMongo> actionLeaseCollection;

    @PostConstruct
    public void init() {
        actionLeaseCollection = mongoOperations.getCollection(ACTION_LEASE, ActionLeaseMongo.class);
        super.init(actionLeaseCollection);

        // Create unique index on action field to ensure only one lease per action
        super.createIndex(actionLeaseCollection,
            Map.of(new Document(FIELD_ACTION, 1), new IndexOptions().name("action_unique").unique(true)),
            getEnsureIndexOnStart());

        // Create TTL index on expiryDate for automatic cleanup
        super.createIndex(actionLeaseCollection,
            Map.of(new Document(FIELD_EXPIRY_DATE, 1), new IndexOptions().name("expiry_ttl").expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS)),
            getEnsureIndexOnStart());
    }

    @Override
    public Maybe<ActionLease> acquireLease(String action, String nodeId, Duration duration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + duration.toMillis());

        // Atomic findOneAndUpdate: only succeeds if the lease is expired OR we already own it.
        // Two concurrent nodes racing on an expired lease will both attempt this operation, but
        // MongoDB executes findOneAndUpdate atomically at the document level – the second caller
        // will no longer match the filter (expiry is now in the future with the first node's id)
        // and will receive null, falling through to the insert path which correctly fails with a
        // duplicate-key error.
        var filter = and(
            eq(FIELD_ACTION, action),
            or(lt(FIELD_EXPIRY_DATE, now), eq(FIELD_NODE_ID, nodeId))
        );
        var update = new Document("$set", new Document()
            .append(FIELD_NODE_ID, nodeId)
            .append(FIELD_EXPIRY_DATE, expiryDate));
        var options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);

        return Observable.fromPublisher(
                actionLeaseCollection.findOneAndUpdate(filter, update, options)
            )
            .firstElement()
            .map(this::convert)
            .switchIfEmpty(Maybe.defer(() -> {
                // No document matched (none exists, or a valid lease is held by another node).
                // Try to insert; a duplicate-key error means another node won the race.
                ActionLeaseMongo newLease = new ActionLeaseMongo();
                newLease.setId(RandomString.generate());
                newLease.setAction(action);
                newLease.setNodeId(nodeId);
                newLease.setExpiryDate(expiryDate);

                return Observable.fromPublisher(actionLeaseCollection.insertOne(newLease))
                    .firstElement()
                    .flatMap(success -> Maybe.just(convert(newLease)))
                    .onErrorResumeNext(error -> Maybe.empty());
            }))
            .observeOn(Schedulers.computation());
    }

    @Override
    public Completable releaseLease(String action, String nodeId) {
        return Completable.fromPublisher(
            actionLeaseCollection.deleteOne(
                and(eq(FIELD_ACTION, action), eq(FIELD_NODE_ID, nodeId))
            )
        ).observeOn(Schedulers.computation());
    }

    private ActionLease convert(ActionLeaseMongo actionLeaseMongo) {
        if (actionLeaseMongo == null) {
            return null;
        }

        ActionLease actionLease = new ActionLease();
        actionLease.setId(actionLeaseMongo.getId());
        actionLease.setAction(actionLeaseMongo.getAction());
        actionLease.setNodeId(actionLeaseMongo.getNodeId());
        actionLease.setExpiryDate(actionLeaseMongo.getExpiryDate());

        return actionLease;
    }
}
