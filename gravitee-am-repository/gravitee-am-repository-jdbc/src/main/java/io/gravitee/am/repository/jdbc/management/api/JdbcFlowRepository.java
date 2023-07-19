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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcFlowRepository extends AbstractJdbcRepository implements FlowRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_CONDITION = "condition";
    public static final String COL_ORDER = "flow_order";
    public static final String COL_REFERENCE_ID = "reference_id";
    public static final String COL_REFERENCE_TYPE = "reference_type";
    public static final String COL_APPLICATION = "application_id";
    public static final String COL_TYPE = "type";
    public static final String COL_ENABLED = "enabled";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_NAME,
            COL_CONDITION,
            COL_ORDER,
            COL_REFERENCE_ID,
            COL_REFERENCE_TYPE,
            COL_APPLICATION,
            COL_TYPE,
            COL_ENABLED,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );

    public static final String COL_STEP_ID = "flow_id";
    public static final String COL_STEP_STAGE = "stage";
    public static final String COL_STEP_STAGE_ORDER = "stage_order";
    public static final String COL_STEP_NAME = "name";
    public static final String COL_STEP_POLICY = "policy";
    public static final String COL_STEP_DESCRIPTION = "description";
    public static final String COL_STEP_CONFIGURATION = "configuration";
    public static final String COL_STEP_ENABLED = "enabled";
    public static final String COL_STEP_CONDITION = "condition";

    private static final List<String> stepsColumns = List.of(
            COL_STEP_ID,
            COL_STEP_STAGE,
            COL_STEP_STAGE_ORDER,
            COL_STEP_NAME,
            COL_STEP_POLICY,
            COL_STEP_DESCRIPTION,
            COL_STEP_CONFIGURATION,
            COL_STEP_ENABLED,
            COL_STEP_CONDITION
    );

    private String INSERT_STATEMENT;
    private String UPDATE_STATEMENT;
    private String INSERT_STEP_STATEMENT;

    @Autowired
    protected SpringFlowRepository flowRepository;

    protected Flow toEntity(JdbcFlow entity) {
        return mapper.map(entity, Flow.class);
    }

    protected JdbcFlow toJdbcEntity(Flow entity) {
        return mapper.map(entity, JdbcFlow.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("flows", columns);
        this.UPDATE_STATEMENT = createUpdateStatement("flows", columns, List.of(COL_ID));
        this.INSERT_STEP_STATEMENT = createInsertStatement("flow_steps", stepsColumns);
    }

    @Override
    public Maybe<Flow> findById(String id) {
        LOGGER.debug("findById({})", id);
        return flowRepository.findById(id)
                .map(this::toEntity)
                .flatMapSingle(this::completeFlow);
    }

    @Override
    public Single<Flow> create(Flow item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create Flow with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(INSERT_STATEMENT);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_NAME, item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CONDITION, item.getCondition(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_ORDER, item.getOrder(), Integer.class);
        insertSpec = addQuotedField(insertSpec, COL_APPLICATION, item.getApplication(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_TYPE, item.getType() == null ? null : item.getType().name(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_ENABLED, item.isEnabled(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> insertAction = insertSpec.fetch().rowsUpdated();
        insertAction = persistChildEntities(insertAction, item);

        return monoToSingle(insertAction.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    private Mono<Long> persistChildEntities(Mono<Long> actionFlow, Flow item) {
        final List<Step> preStep = item.getPre();
        final List<Step> postStep = item.getPost();

        if (preStep != null && !preStep.isEmpty()) {
            actionFlow = actionFlow.then(persistFlowSteps(item, preStep, JdbcFlow.StepType.pre));
        }

        if (postStep != null && !postStep.isEmpty()) {
            actionFlow = actionFlow.then(persistFlowSteps(item, postStep, JdbcFlow.StepType.post));
        }

        return actionFlow;
    }

    private Mono<Long> persistFlowSteps(Flow item, List<Step> steps, JdbcFlow.StepType type) {
        List<JdbcFlow.JdbcStep> jdbcPostSteps = new ArrayList<>();
        for (int i = 0; i < steps.size(); ++i) {
            JdbcFlow.JdbcStep bean = convertToJdbcStep(item, steps.get(i), type);
            bean.setOrder(i + 1);
            jdbcPostSteps.add(bean);
        }
        return Flux.fromIterable(jdbcPostSteps).concatMap(step -> {
            DatabaseClient.GenericExecuteSpec insert = getTemplate().getDatabaseClient().sql(INSERT_STEP_STATEMENT);
            insert = step.getFlowId() != null ? insert.bind(COL_STEP_ID, step.getFlowId()) : insert.bindNull(COL_STEP_ID, String.class);
            insert = step.getStage() != null ? insert.bind(COL_STEP_STAGE, step.getStage()) : insert.bindNull(COL_STEP_STAGE, String.class);
            insert = step.getName() != null ? insert.bind(COL_STEP_NAME, step.getName()) : insert.bindNull(COL_STEP_NAME, String.class);
            insert = step.getPolicy() != null ? insert.bind(COL_STEP_POLICY, step.getPolicy()) : insert.bindNull(COL_STEP_POLICY, String.class);
            insert = step.getDescription() != null ? insert.bind(COL_STEP_DESCRIPTION, step.getDescription()) : insert.bindNull(COL_STEP_DESCRIPTION, String.class);
            insert = step.getConfiguration() != null ? insert.bind(COL_STEP_CONFIGURATION, step.getConfiguration()) : insert.bindNull(COL_STEP_CONFIGURATION, String.class);
            insert = insert.bind(COL_STEP_STAGE_ORDER, step.getOrder());
            insert = insert.bind(COL_STEP_ENABLED, step.isEnabled());
            insert = step.getCondition() != null ? insert.bind(COL_STEP_CONDITION, step.getCondition()) : insert.bindNull(COL_STEP_CONDITION, String.class);
            return insert.fetch().rowsUpdated();
        }).reduce(Long::sum);
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
        bean.setCondition(step.getCondition());
        return bean;
    }

    private Step convertToStep(JdbcFlow.JdbcStep entity) {
        Step bean = new Step();
        bean.setConfiguration(entity.getConfiguration());
        bean.setDescription(entity.getDescription());
        bean.setEnabled(entity.isEnabled());
        bean.setName(entity.getName());
        bean.setPolicy(entity.getPolicy());
        bean.setCondition(entity.getCondition());
        return bean;
    }

    @Override
    public Single<Flow> update(Flow item) {
        LOGGER.debug("Update Flow with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec update = getTemplate().getDatabaseClient().sql(UPDATE_STATEMENT);

        update = addQuotedField(update, COL_ID, item.getId(), String.class);
        update = addQuotedField(update, COL_NAME, item.getName(), String.class);
        update = addQuotedField(update, COL_CONDITION, item.getCondition(), String.class);
        update = addQuotedField(update, COL_ORDER, item.getOrder(), Integer.class);
        update = addQuotedField(update, COL_APPLICATION, item.getApplication(), String.class);
        update = addQuotedField(update, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        update = addQuotedField(update, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        update = addQuotedField(update, COL_TYPE, item.getType() == null ? null : item.getType().name(), String.class);
        update = addQuotedField(update, COL_ENABLED, item.isEnabled(), Boolean.class);
        update = addQuotedField(update, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> updateAction = update.fetch().rowsUpdated();

        updateAction = updateAction.then(deleteChildEntities(item.getId()));
        updateAction = persistChildEntities(updateAction, item);

        return monoToSingle(updateAction.as(trx::transactional)).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    private Mono<Long> deleteChildEntities(String flowId) {
        return getTemplate().delete(Query.query(where(COL_STEP_ID).is(flowId)), JdbcFlow.JdbcStep.class).map(Integer::longValue);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete Flow with id {}", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        return monoToCompletable(getTemplate().delete(JdbcFlow.class)
                .matching(Query.query(where(COL_ID).is(id)))
                .all()
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
        return fluxToFlowable(getTemplate().select(JdbcFlow.JdbcStep.class)
                .matching(Query.query(where(COL_STEP_ID).is(flow.getId())).sort(Sort.by(COL_STEP_STAGE_ORDER).ascending()))
                .all()).toList().map(steps -> {
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
