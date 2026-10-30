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

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.ActionLease;
import io.gravitee.am.repository.gateway.api.ActionLeaseRepository;
import io.gravitee.am.repository.mongodb.common.ActionLeaseCommand;
import io.gravitee.am.repository.mongodb.common.model.ActionLeaseMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("gatewayActionLeaseRepository")
public class MongoActionLeaseRepository extends AbstractGatewayMongoRepository implements ActionLeaseRepository {
    private static final String ACTION_LEASE = "dp_action_lease";

    private ActionLeaseCommand actionLeaseCommand;

    @PostConstruct
    public void init() {
        MongoCollection<ActionLeaseMongo> actionLeaseCollection = mongoOperations.getCollection(ACTION_LEASE, ActionLeaseMongo.class);
        this.actionLeaseCommand = new ActionLeaseCommand(actionLeaseCollection);

        super.init(actionLeaseCollection);
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
