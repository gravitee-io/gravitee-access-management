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
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.stereotype.Component;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

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

        return monoToSingle(dbClient.insert()
                .into(JdbcAlertNotifier.class)
                .using(toJdbcAlertNotifier(alertNotifier))
                .then()
                .then(maybeToMono(findById(alertNotifier.getId()))));
    }

    @Override
    public Single<AlertNotifier> update(AlertNotifier alertNotifier) {
        LOGGER.debug("update alert notifier with id {}", alertNotifier.getId());

        return monoToSingle(dbClient.update()
                .table(JdbcAlertNotifier.class)
                .using(toJdbcAlertNotifier(alertNotifier))
                .matching(from(where("id").is(alertNotifier.getId()))).then()
                .then(maybeToMono(findById(alertNotifier.getId()))));
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

        return fluxToFlowable(dbClient.select()
                .from(JdbcAlertNotifier.class)
                .matching(from(whereClause))
                .as(JdbcAlertNotifier.class)
                .all())
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve AlertNotifier with referenceId {}, referenceType {} and criteria {}",
                        referenceId, referenceType, criteria, error));
    }
}
