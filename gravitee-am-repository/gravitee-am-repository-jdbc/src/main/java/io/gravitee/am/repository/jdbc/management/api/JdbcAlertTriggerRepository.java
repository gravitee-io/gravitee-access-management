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
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAlertTrigger;
import io.gravitee.am.repository.jdbc.management.api.spring.alert.SpringAlertTriggerAlertNotifierRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.alert.SpringAlertTriggerRepository;
import io.gravitee.am.repository.management.api.AlertTriggerRepository;
import io.gravitee.am.repository.management.api.search.AlertTriggerCriteria;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class JdbcAlertTriggerRepository extends AbstractJdbcRepository implements AlertTriggerRepository {

    @Autowired
    private SpringAlertTriggerRepository alertTriggerRepository;

    @Autowired
    private SpringAlertTriggerAlertNotifierRepository alertTriggerAlertNotifierRepository;

    public static final String ALERT_TRIGGER_ID = "alert_trigger_id";
    public static final String ALERT_NOTIFIER_ID = "alert_notifier_id";
    private final static String INSERT_ALERT_NOTIFIER_STMT = "INSERT INTO alert_triggers_alert_notifiers(" + ALERT_TRIGGER_ID + ", " + ALERT_NOTIFIER_ID + ") VALUES (:"+ALERT_TRIGGER_ID+", :"+ALERT_NOTIFIER_ID+")";

    protected AlertTrigger toEntity(JdbcAlertTrigger alertTrigger) {
        AlertTrigger mapped = mapper.map(alertTrigger, AlertTrigger.class);
        if (mapped.getAlertNotifiers() == null) {
            mapped.setAlertNotifiers(new ArrayList<>());
        }
        return mapped;
    }

    protected JdbcAlertTrigger toJdbcAlertTrigger(AlertTrigger entity) {
        return mapper.map(entity, JdbcAlertTrigger.class);
    }

    @Override
    public Maybe<AlertTrigger> findById(String id) {
        LOGGER.debug("findById({})", id);

        Maybe<List<String>> alertNotifierIds = alertTriggerAlertNotifierRepository.findByAlertTriggerId(id)
                .map(JdbcAlertTrigger.AlertNotifier::getAlertNotifierId)
                .toList()
                .toMaybe();

        return this.alertTriggerRepository.findById(id)
                .map(this::toEntity)
                .zipWith(alertNotifierIds, (alertTrigger, ids) -> {
                    LOGGER.debug("findById({}) fetch {} alert triggers", alertTrigger.getId(), ids.size());
                    alertTrigger.setAlertNotifiers(ids);
                    return alertTrigger;
                });
    }

    @Override
    public Single<AlertTrigger> create(AlertTrigger alertTrigger) {
        alertTrigger.setId(alertTrigger.getId() == null ? RandomString.generate() : alertTrigger.getId());
        LOGGER.debug("create alert trigger with id {}", alertTrigger.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Void> insert = getTemplate().insert(toJdbcAlertTrigger(alertTrigger))
                .then();

        final Mono<Void> storeAlertNotifiers = storeAlertNotifiers(alertTrigger, false);

        return monoToSingle(insert
                .then(storeAlertNotifiers)
                .as(trx::transactional)
                .then(maybeToMono(findById(alertTrigger.getId()))));
    }

    @Override
    public Single<AlertTrigger> update(AlertTrigger alertTrigger) {
        LOGGER.debug("update alert trigger with id {}", alertTrigger.getId());
        TransactionalOperator trx = TransactionalOperator.create(tm);

        Mono<Void> update = getTemplate().update(toJdbcAlertTrigger(alertTrigger)).then();

        final Mono<Void> storeAlertNotifiers = storeAlertNotifiers(alertTrigger, true);

        return monoToSingle(update
                .then(storeAlertNotifiers)
                .as(trx::transactional)
                .then(maybeToMono(findById(alertTrigger.getId()))));
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return this.alertTriggerRepository.deleteById(id);
    }

    @Override
    public Flowable<AlertTrigger> findAll(ReferenceType referenceType, String referenceId) {
        return findByCriteria(referenceType, referenceId, new AlertTriggerCriteria());
    }

    @Override
    public Flowable<AlertTrigger> findByCriteria(ReferenceType referenceType, String referenceId, AlertTriggerCriteria criteria) {

        Map<String, Object> params = new HashMap<>();

        final StringBuilder queryBuilder = new StringBuilder("SELECT DISTINCT t.id FROM alert_triggers t ");

        List<String> queryCriteria = new ArrayList<>();

        if (criteria.isEnabled().isPresent()) {
            queryCriteria.add("t.enabled = :enabled");
            params.put("enabled", criteria.isEnabled().get());
        }

        if (criteria.getType().isPresent()) {
            queryCriteria.add("t.type = :type");
            params.put("type", criteria.getType().get().name());
        }

        if (criteria.getAlertNotifierIds().isPresent() && !criteria.getAlertNotifierIds().get().isEmpty()) {
            // Add join when alert notifier ids are provided.
            queryBuilder.append("INNER JOIN alert_triggers_alert_notifiers n ON t.id = n." + ALERT_TRIGGER_ID + " ");

            queryCriteria.add("n." + ALERT_NOTIFIER_ID + " IN(:alertNotifierIds)");
            params.put("alertNotifierIds", criteria.getAlertNotifierIds().get());
        }

        // Always add constraint on reference.
        queryBuilder.append("WHERE t.reference_id = :reference_id AND t.reference_type = :reference_type");

        if (!queryCriteria.isEmpty()) {
            queryBuilder.append(" AND (");
            queryBuilder.append(queryCriteria.stream().collect(Collectors.joining(criteria.isLogicalOR() ? " OR " : " AND ")));
            queryBuilder.append(")");
        }

        params.put("reference_id", referenceId);
        params.put("reference_type", referenceType.name());

        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec execute = getTemplate().getDatabaseClient().sql(queryBuilder.toString());

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            execute = execute.bind(entry.getKey(), entry.getValue());
        }

        return fluxToFlowable(execute
                .map((row, rowMetadata) -> row.get(0, String.class)).all())
                .flatMapMaybe(this::findById)
                .doOnError(error -> LOGGER.error("Unable to retrieve AlertTrigger with referenceId {}, referenceType {} and criteria {}",
                        referenceId, referenceType, criteria, error));
    }

    private Mono<Void> storeAlertNotifiers(AlertTrigger alertTrigger, boolean deleteFirst) {

        Mono<Void> delete = Mono.empty();

        if (deleteFirst) {
            delete = deleteAlertNotifiers(alertTrigger.getId());
        }

        final List<String> alertNotifiers = alertTrigger.getAlertNotifiers();
        if (alertNotifiers != null && !alertNotifiers.isEmpty()) {
            return delete.thenMany(Flux.fromIterable(alertNotifiers)
                    .map(alertNotifierId -> {
                        JdbcAlertTrigger.AlertNotifier dbAlertNotifier = new JdbcAlertTrigger.AlertNotifier();
                        dbAlertNotifier.setAlertNotifierId(alertNotifierId);
                        dbAlertNotifier.setAlertTriggerId(alertTrigger.getId());
                        return dbAlertNotifier;
                    })
                    .concatMap(dbAlertNotifier -> {
                        final DatabaseClient.GenericExecuteSpec sql = getTemplate().getDatabaseClient()
                                .sql(INSERT_ALERT_NOTIFIER_STMT)
                                .bind(ALERT_TRIGGER_ID, dbAlertNotifier.getAlertTriggerId())
                                .bind(ALERT_NOTIFIER_ID, dbAlertNotifier.getAlertNotifierId());
                        return sql.then();
                    }))
                    .ignoreElements();
        }

        return Mono.empty();
    }

    private Mono<Void> deleteAlertNotifiers(String alertTriggerId) {
        return getTemplate().delete(Query.query(where(ALERT_TRIGGER_ID).is(alertTriggerId)),JdbcAlertTrigger.AlertNotifier.class).then();
    }

}
