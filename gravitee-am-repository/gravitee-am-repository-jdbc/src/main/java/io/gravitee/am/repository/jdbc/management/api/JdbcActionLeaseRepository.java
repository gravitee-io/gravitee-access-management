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
package io.gravitee.am.repository.jdbc.management.api;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.ActionLease;
import io.gravitee.am.repository.jdbc.common.AbstractActionLeaseRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcActionLease;
import io.gravitee.am.repository.management.api.ActionLeaseRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository("managementActionLeaseRepository")
public class JdbcActionLeaseRepository extends AbstractActionLeaseRepository<JdbcActionLease> implements ActionLeaseRepository {

    protected ActionLease toEntity(JdbcActionLease entity) {
        return mapper.map(entity, ActionLease.class);
    }

    protected JdbcActionLease toJdbcEntity(ActionLease entity) {
        return mapper.map(entity, JdbcActionLease.class);
    }

    @Override
    protected Class<JdbcActionLease> getJdbcEntityClass() {
        return JdbcActionLease.class;
    }

    @Override
    public Maybe<ActionLease> acquireLease(String action, String nodeId, Duration duration) {
        return doAcquireLease(action, nodeId, duration);
    }

    @Override
    public Completable releaseLease(String action, String nodeId) {
        return doReleaseLease(action, nodeId);
    }

    protected JdbcActionLease buildJdbcActionLease(String action, String nodeId, LocalDateTime expiryDate) {
        JdbcActionLease newLease = new JdbcActionLease();
        newLease.setId(RandomString.generate());
        newLease.setAction(action);
        newLease.setNodeId(nodeId);
        newLease.setExpiryDate(expiryDate);
        return newLease;
    }
}
