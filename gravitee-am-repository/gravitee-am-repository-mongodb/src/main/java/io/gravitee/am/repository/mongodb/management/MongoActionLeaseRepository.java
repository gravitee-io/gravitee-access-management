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
import io.gravitee.am.model.ActionLease;
import io.gravitee.am.repository.management.api.ActionLeaseRepository;
import io.gravitee.am.repository.mongodb.common.ActionLeaseCommand;
import io.gravitee.am.repository.mongodb.common.model.ActionLeaseMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("managementActionLeaseRepository")
public class MongoActionLeaseRepository extends AbstractManagementMongoRepository implements ActionLeaseRepository {
    private static final String ACTION_LEASE = "cp_action_lease";

    private ActionLeaseCommand actionLeaseCommand;

    @PostConstruct
    public void init() {
        MongoCollection<ActionLeaseMongo> actionLeaseCollection = mongoOperations.getCollection(ACTION_LEASE, ActionLeaseMongo.class);
        this.actionLeaseCommand = new ActionLeaseCommand(actionLeaseCollection);

        super.init(actionLeaseCollection);

        // Create unique index on action field to ensure only one lease per action
        super.createIndex(actionLeaseCollection,
            Map.of(new Document(ActionLeaseCommand.FIELD_ACTION, 1), new IndexOptions().name("action_unique").unique(true)), ensureIndexOnStart);

        // Create TTL index on expiryDate for automatic cleanup
        super.createIndex(actionLeaseCollection,
            Map.of(new Document(ActionLeaseCommand.FIELD_EXPIRY_DATE, 1), new IndexOptions().name("expiry_ttl").expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS)),
            ensureIndexOnStart);
    }

    @Override
    public Maybe<ActionLease> acquireLease(String action, String nodeId, Duration duration) {
        return actionLeaseCommand.acquireLease(action, nodeId, duration);
    }

    @Override
    public Completable releaseLease(String action, String nodeId) {
        return actionLeaseCommand.releaseLease(action, nodeId);
    }
}
