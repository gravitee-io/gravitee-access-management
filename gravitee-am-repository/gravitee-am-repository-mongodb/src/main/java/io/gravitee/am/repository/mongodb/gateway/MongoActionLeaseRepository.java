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

import com.mongodb.client.model.IndexOptions;
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

        // First, try to find an existing lease for this action
        return Observable.fromPublisher(
            actionLeaseCollection.find(eq(FIELD_ACTION, action)).first()
        )
        .firstElement()
        .flatMap(existingLease -> {
            // Check if we can acquire the lease
            boolean isExpired = existingLease.getExpiryDate().before(now);
            boolean isSameNode = existingLease.getNodeId().equals(nodeId);

            if (isExpired || isSameNode) {
                // Update the existing lease
                existingLease.setNodeId(nodeId);
                existingLease.setExpiryDate(expiryDate);

                return Observable.fromPublisher(
                    actionLeaseCollection.replaceOne(
                        eq(FIELD_ID, existingLease.getId()),
                        existingLease
                    )
                )
                .firstElement()
                .flatMap(result -> {
                    if (result.getModifiedCount() > 0 || result.getMatchedCount() > 0) {
                        return Maybe.just(convert(existingLease));
                    } else {
                        return Maybe.empty();
                    }
                });
            } else {
                // Cannot acquire, lease is held by another node
                return Maybe.empty();
            }
        })
        .switchIfEmpty(Maybe.defer(() -> {
            // No existing lease, create a new one
            ActionLeaseMongo newLease = new ActionLeaseMongo();
            newLease.setId(RandomString.generate());
            newLease.setAction(action);
            newLease.setNodeId(nodeId);
            newLease.setExpiryDate(expiryDate);

            return Observable.fromPublisher(actionLeaseCollection.insertOne(newLease))
                .firstElement()
                .flatMap(success -> Maybe.just(convert(newLease)))
                .onErrorResumeNext(error -> {
                    // If insert fails due to duplicate key (race condition), return empty
                    return Maybe.empty();
                });
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
