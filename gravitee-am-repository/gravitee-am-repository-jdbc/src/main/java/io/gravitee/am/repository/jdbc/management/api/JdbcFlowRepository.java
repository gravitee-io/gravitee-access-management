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
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcFlow;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringFlowRepository;
import io.gravitee.am.repository.management.api.FlowRepository;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcFlowRepository extends AbstractJdbcRepository implements FlowRepository {

    @Autowired
    protected SpringFlowRepository flowRepository;

    protected Flow toEntity(JdbcFlow entity) {
        return mapper.map(entity, Flow.class);
    }

    protected JdbcFlow toJdbcEntity(Flow entity) {
        return mapper.map(entity, JdbcFlow.class);
    }

    @Override
    public Maybe<Flow> findById(String id) {
        LOGGER.debug("findById({})", id);
        return flowRepository.findById(id)
                .map(this::toEntity)
                .flatMapSingleElement(flow ->  completeFlow(flow));
    }

    @Override
    public Single<Flow> create(Flow item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create Flow with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = dbClient.insert().into("flows");

        insertSpec = addQuotedField(insertSpec,"id", item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec,"name", item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec,"condition", item.getCondition(), String.class);
        insertSpec = addQuotedField(insertSpec,"reference_id", item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec,"reference_type", item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec,"application_id", item.getApplication(), String.class);
        insertSpec = addQuotedField(insertSpec,"type", item.getType() == null ? null : item.getType().name(), String.class);
        insertSpec = addQuotedField(insertSpec,"enabled", item.isEnabled(), Boolean.class);
        insertSpec = addQuotedField(insertSpec,"created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec,"updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec,"flow_order", item.getOrder(), Integer.class);

        Mono<Integer> insertAction = insertSpec.fetch().rowsUpdated();

        insertAction = persistChildEntities(insertAction, item);

        return monoToSingle(insertAction.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    private Mono<Integer> persistChildEntities(Mono<Integer> actionFlow, Flow item) {
        final List<Step> preStep = item.getPre();
        final List<Step> postStep = item.getPost();

        if (preStep != null && !preStep.isEmpty()) {
            List<JdbcFlow.JdbcStep> jdbcPreSteps = new ArrayList<>();
            for( int i = 0; i < preStep.size(); ++i) {
                JdbcFlow.JdbcStep bean = convertToJdbcStep(item, preStep.get(i), JdbcFlow.StepType.pre);
                bean.setOrder(i + 1);
                jdbcPreSteps.add(bean);
            }
            actionFlow = actionFlow.then(Flux.fromIterable(jdbcPreSteps).concatMap(step ->
                    dbClient.insert().into(JdbcFlow.JdbcStep.class).using(step).fetch().rowsUpdated())
                    .reduce(Integer::sum));
        }

        if (postStep != null && !postStep.isEmpty()) {
            List<JdbcFlow.JdbcStep> jdbcPostSteps = new ArrayList<>();
            for( int i = 0; i < postStep.size(); ++i) {
                JdbcFlow.JdbcStep bean = convertToJdbcStep(item, postStep.get(i), JdbcFlow.StepType.post);
                bean.setOrder(i + 1);
                jdbcPostSteps.add(bean);
            }
            actionFlow = actionFlow.then(Flux.fromIterable(jdbcPostSteps).concatMap(step ->
                    dbClient.insert().into(JdbcFlow.JdbcStep.class).using(step).fetch().rowsUpdated())
                    .reduce(Integer::sum));
        }

        return actionFlow;
    }

    private JdbcFlow.JdbcStep convertToJdbcStep(Flow item, Step step, JdbcFlow.StepType post) {
        JdbcFlow.JdbcStep bean = new JdbcFlow.JdbcStep();
        bean.setStage(post.name());
        bean.setFlowId(item.getId());
        bean.setConfiguration(step.getConfiguration());
        bean.setDescription(step.getDescription());
        bean.setEnabled(step.isEnabled());
        bean.setName(step.getName());
        bean.setPolicy(step.getPolicy());
        return bean;
    }

    private Step convertToStep(JdbcFlow.JdbcStep entity) {
        Step bean = new Step();
        bean.setConfiguration(entity.getConfiguration());
        bean.setDescription(entity.getDescription());
        bean.setEnabled(entity.isEnabled());
        bean.setName(entity.getName());
        bean.setPolicy(entity.getPolicy());
        return bean;
    }

    @Override
    public Single<Flow> update(Flow item) {
        LOGGER.debug("Update Flow with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        final DatabaseClient.GenericUpdateSpec updateSpec = dbClient.update().table("flows");

        Map<SqlIdentifier, Object> updateFields = new HashMap<>();
        updateFields = addQuotedField(updateFields,"id", item.getId(), String.class);
        updateFields = addQuotedField(updateFields,"name", item.getName(), String.class);
        updateFields = addQuotedField(updateFields,"condition", item.getCondition(), String.class);
        updateFields = addQuotedField(updateFields,"reference_id", item.getReferenceId(), String.class);
        updateFields = addQuotedField(updateFields,"reference_type", item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        updateFields = addQuotedField(updateFields,"application_id", item.getApplication(), String.class);
        updateFields = addQuotedField(updateFields,"type", item.getType() == null ? null : item.getType().name(), String.class);
        updateFields = addQuotedField(updateFields,"enabled", item.isEnabled(), Boolean.class);
        updateFields = addQuotedField(updateFields,"created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields,"updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields,"flow_order", item.getOrder(), Integer.class);

        Mono<Integer> updateAction = updateSpec.using(Update.from(updateFields)).matching(from(where("id").is(item.getId()))).fetch().rowsUpdated();

        updateAction = updateAction.then(deleteChildEntities(item.getId()));
        updateAction = persistChildEntities(updateAction, item);

        return monoToSingle(updateAction.as(trx::transactional)).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    private Mono<Integer> deleteChildEntities(String flowId) {
        return dbClient.delete().from(JdbcFlow.JdbcStep.class).matching(from(where("flow_id").is(flowId))).fetch().rowsUpdated();
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete Flow with id {}", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        return monoToCompletable(dbClient.delete()
                .from(JdbcFlow.class)
                .matching(from(where("id").is(id)))
                .fetch().rowsUpdated()
                .then(deleteChildEntities(id))
                .as(trx::transactional));
    }

    @Override
    public Maybe<Flow> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("findById({}, {}, {})", referenceType, referenceId, id);
        return flowRepository.findById(referenceType.name(), referenceId, id)
                .map(this::toEntity)
                .flatMap(flow -> completeFlow(flow).toMaybe());
    }

    @Override
    public Flowable<Flow> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findAll({}, {})", referenceType, referenceId);
        return flowRepository.findAll(referenceType.name(), referenceId)
                .map(this::toEntity)
                .flatMap(flow -> completeFlow(flow).toFlowable());
    }

    @Override
    public Flowable<Flow> findByApplication(ReferenceType referenceType, String referenceId, String application) {
        LOGGER.debug("findByApplication({}, {}, {})", referenceType, referenceId, application);
        return flowRepository.findByApplication(referenceType.name(), referenceId, application)
                .map(this::toEntity)
                .flatMap(flow -> completeFlow(flow).toFlowable());
    }

    protected Single<Flow> completeFlow(Flow flow) {
        return fluxToFlowable(dbClient.select()
                .from(JdbcFlow.JdbcStep.class)
                .matching(from(where("flow_id").is(flow.getId())))
                .orderBy(Sort.Order.asc("stage_order"))
                .as(JdbcFlow.JdbcStep.class).all()).toList().map(steps -> {
            if (steps != null && !steps.isEmpty()) {
                List<Step> preSteps = new ArrayList<>();
                List<Step> postSteps = new ArrayList<>();
                for (JdbcFlow.JdbcStep jStep : steps) {
                    if (jStep.getStage().equals(JdbcFlow.StepType.pre.name())) {
                        preSteps.add(convertToStep(jStep));
                    } else if (jStep.getStage().equals(JdbcFlow.StepType.post.name())) {
                        postSteps.add(convertToStep(jStep));
                    } else {
                        LOGGER.debug("Unknown step type '{}', ignore it!", jStep.getStage());
                    }
                }
                if (!preSteps.isEmpty()) {
                    flow.setPre(preSteps);
                }
                if (!postSteps.isEmpty()) {
                    flow.setPost(postSteps);
                }
            }
            return flow;
        });
    }
}
