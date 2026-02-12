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
package io.gravitee.am.repository.jdbc.gateway.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.ActionLease;
import io.gravitee.am.repository.gateway.api.ActionLeaseRepository;
import io.gravitee.am.repository.jdbc.gateway.api.model.JdbcActionLease;
import io.gravitee.am.repository.jdbc.gateway.api.model.JdbcRateLimit;
import io.gravitee.am.repository.jdbc.gateway.api.spring.SpringActionLeaseRepository;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcActionLeaseRepository extends AbstractJdbcRepository implements ActionLeaseRepository {

    @Autowired
    protected SpringActionLeaseRepository actionLeaseRepository;

    protected ActionLease toEntity(JdbcActionLease entity) {
        return mapper.map(entity, ActionLease.class);
    }

    protected JdbcActionLease toJdbcEntity(ActionLease entity) {
        return mapper.map(entity, JdbcActionLease.class);
    }

    @Override
    public Maybe<ActionLease> acquireLease(String action, String nodeId, Duration duration) {
        LOGGER.debug("Attempting to acquire lease for action '{}' by node '{}'", action, nodeId);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime expiryDate = now.plus(duration);

        return actionLeaseRepository.findByAction(action)
            .flatMap(existingLease -> {
                // Check if we can acquire the lease
                boolean canAcquire = existingLease.getExpiryDate().isBefore(now) ||
                                    existingLease.getNodeId().equals(nodeId);

                if (canAcquire) {
                    LOGGER.debug("Updating existing lease for action '{}'", action);
                    // Update the existing lease
                    existingLease.setNodeId(nodeId);
                    existingLease.setExpiryDate(expiryDate);

                    return actionLeaseRepository.save(existingLease)
                        .map(this::toEntity).toMaybe()
                        .observeOn(Schedulers.computation());
                } else {
                    LOGGER.debug("Cannot acquire lease for action '{}', held by another node", action);
                    // Cannot acquire, lease is held by another node
                    return Maybe.empty();
                }
            })
            .switchIfEmpty(Maybe.defer(() -> {
                LOGGER.debug("Creating new lease for action '{}'", action);
                // No existing lease, create a new one
                JdbcActionLease newLease = new JdbcActionLease();
                newLease.setId(RandomString.generate());
                newLease.setAction(action);
                newLease.setNodeId(nodeId);
                newLease.setExpiryDate(expiryDate);

                return monoToMaybe(getTemplate().insert(newLease))
                    .map(this::toEntity)
                    .observeOn(Schedulers.computation())
                    .onErrorResumeNext(error -> {
                        // If insert fails due to duplicate key (race condition), return empty
                        LOGGER.debug("Failed to create lease due to race condition", error);
                        return Maybe.empty();
                    });
            }));
    }

    @Override
    public Completable releaseLease(String action, String nodeId) {
        LOGGER.debug("Releasing lease for action '{}' by node '{}'", action, nodeId);

        Criteria whereClause = Criteria.where("action").is(action).and(where("node_id").is(nodeId));

        return monoToCompletable(getTemplate().delete(JdbcActionLease.class).matching(Query.query(whereClause)).all())
                .observeOn(Schedulers.computation());
    }
}
