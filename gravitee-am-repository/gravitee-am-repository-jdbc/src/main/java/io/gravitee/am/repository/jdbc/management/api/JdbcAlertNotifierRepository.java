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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAlertNotifier;
import io.gravitee.am.repository.jdbc.management.api.spring.alert.SpringAlertNotifierRepository;
import io.gravitee.am.repository.management.api.AlertNotifierRepository;
import io.gravitee.am.repository.management.api.search.AlertNotifierCriteria;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class JdbcAlertNotifierRepository extends AbstractJdbcRepository implements AlertNotifierRepository {

    @Autowired
    private SpringAlertNotifierRepository alertNotifierRepository;

    protected AlertNotifier toEntity(JdbcAlertNotifier alertNotifier) {
        return mapper.map(alertNotifier, AlertNotifier.class);
    }

    protected JdbcAlertNotifier toJdbcAlertNotifier(AlertNotifier entity) {
        return mapper.map(entity, JdbcAlertNotifier.class);
    }

    @Override
    public Maybe<AlertNotifier> findById(String id) {
        LOGGER.debug("findById({})", id);

        return this.alertNotifierRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Single<AlertNotifier> create(AlertNotifier alertNotifier) {
        alertNotifier.setId(alertNotifier.getId() == null ? RandomString.generate() : alertNotifier.getId());
        LOGGER.debug("create alert notifier with id {}", alertNotifier.getId());

        return monoToSingle(getTemplate().insert(toJdbcAlertNotifier(alertNotifier))).map(this::toEntity);
    }

    @Override
    public Single<AlertNotifier> update(AlertNotifier alertNotifier) {
        LOGGER.debug("update alert notifier with id {}", alertNotifier.getId());

        return monoToSingle(getTemplate().update(toJdbcAlertNotifier(alertNotifier))).map(this::toEntity);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return this.alertNotifierRepository.deleteById(id);
    }

    @Override
    public Flowable<AlertNotifier> findAll(ReferenceType referenceType, String referenceId) {
        return findByCriteria(referenceType, referenceId, new AlertNotifierCriteria());
    }

    @Override
    public Flowable<AlertNotifier> findByCriteria(ReferenceType referenceType, String referenceId, AlertNotifierCriteria criteria) {

        Criteria whereClause = Criteria.empty();
        Criteria enableClause = Criteria.empty();
        Criteria idsClause = Criteria.empty();

        Criteria referenceClause = where("reference_id").is(referenceId).and(where("reference_type").is(referenceType.name()));

        if (criteria.isEnabled().isPresent()) {
            enableClause = where("enabled").is(criteria.isEnabled().get());
        }

        if (criteria.getIds().isPresent() && !criteria.getIds().get().isEmpty()) {
            idsClause = where("id").in(criteria.getIds().get());
        }

        whereClause = whereClause.and(referenceClause.and(criteria.isLogicalOR() ? idsClause.or(enableClause) : idsClause.and(enableClause)));

        return fluxToFlowable(getTemplate().select(Query.query(whereClause), JdbcAlertNotifier.class)).map(this::toEntity);
    }
}
