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
import io.gravitee.am.repository.jdbc.gateway.api.spring.SpringActionLeaseRepository;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.maybeToMono;
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

        // Atomic UPDATE: the database applies the WHERE predicate and the SET in a single
        // statement, so two nodes racing on an expired lease cannot both succeed.  The one
        // whose UPDATE lands second will see 0 rows updated (the lease is no longer expired)
        // and will fall through to the INSERT path, which correctly fails with a
        // duplicate-key error because the unique index on `action` is still in place.
        Criteria acquireCriteria = where("action").is(action)
            .and(where("expiry_date").lessThan(now)
                .or(where("node_id").is(nodeId)));

        Mono<JdbcActionLease> atomicAcquire = getTemplate()
            .update(JdbcActionLease.class)
            .matching(Query.query(acquireCriteria))
            .apply(Update.update("node_id", nodeId).set("expiry_date", expiryDate))
            .flatMap(updatedCount -> {
                if (updatedCount > 0) {
                    LOGGER.debug("Updated existing lease for action '{}'", action);
                    // Re-fetch to get the full entity (including its id).
                    return maybeToMono(actionLeaseRepository.findByAction(action));
                }
                // 0 rows updated: either no row exists yet, or a valid lease is held by
                // another node.  Try to insert; a duplicate-key violation means we lost
                // the race and the caller receives an empty Maybe.
                LOGGER.debug("Creating new lease for action '{}'", action);
                JdbcActionLease newLease = new JdbcActionLease();
                newLease.setId(RandomString.generate());
                newLease.setAction(action);
                newLease.setNodeId(nodeId);
                newLease.setExpiryDate(expiryDate);

                return getTemplate().insert(newLease)
                    .onErrorResume(error -> {
                        LOGGER.debug("Failed to create lease due to race condition", error);
                        return Mono.empty();
                    });
            });

        return monoToMaybe(atomicAcquire)
            .map(this::toEntity)
            .observeOn(Schedulers.computation());
    }

    @Override
    public Completable releaseLease(String action, String nodeId) {
        LOGGER.debug("Releasing lease for action '{}' by node '{}'", action, nodeId);

        Criteria whereClause = Criteria.where("action").is(action).and(where("node_id").is(nodeId));

        return monoToCompletable(getTemplate().delete(JdbcActionLease.class).matching(Query.query(whereClause)).all())
                .observeOn(Schedulers.computation());
    }
}
