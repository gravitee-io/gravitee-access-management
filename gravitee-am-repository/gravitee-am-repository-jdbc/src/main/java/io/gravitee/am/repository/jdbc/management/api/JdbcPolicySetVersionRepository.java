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
import io.gravitee.am.model.PolicySetVersion;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcPolicySetVersion;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringPolicySetVersionRepository;
import io.gravitee.am.repository.management.api.PolicySetVersionRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author GraviteeSource Team
 */
@Repository
public class JdbcPolicySetVersionRepository extends AbstractJdbcRepository implements PolicySetVersionRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_POLICY_SET_ID = "policy_set_id";
    public static final String COL_VERSION = "version";
    public static final String COL_CONTENT = "content";
    public static final String COL_COMMIT_MESSAGE = "commit_message";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_CREATED_BY = "created_by";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_POLICY_SET_ID,
            COL_VERSION,
            COL_CONTENT,
            COL_COMMIT_MESSAGE,
            COL_CREATED_AT,
            COL_CREATED_BY
    );

    private String insertStatement;
    private String updateStatement;

    @Autowired
    private SpringPolicySetVersionRepository policySetVersionRepository;

    protected PolicySetVersion toEntity(JdbcPolicySetVersion entity) {
        return mapper.map(entity, PolicySetVersion.class);
    }

    protected JdbcPolicySetVersion toJdbcEntity(PolicySetVersion entity) {
        return mapper.map(entity, JdbcPolicySetVersion.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insertStatement = createInsertStatement("policy_set_versions", columns);
        this.updateStatement = createUpdateStatement("policy_set_versions", columns, List.of(COL_ID));
    }

    @Override
    public Maybe<PolicySetVersion> findById(String id) {
        LOGGER.debug("findById({})", id);
        return this.policySetVersionRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Flowable<PolicySetVersion> findByPolicySetId(String policySetId) {
        LOGGER.debug("findByPolicySetId({})", policySetId);
        return this.policySetVersionRepository.findByPolicySetId(policySetId)
                .map(this::toEntity);
    }

    @Override
    public Maybe<PolicySetVersion> findByPolicySetIdAndVersion(String policySetId, int version) {
        LOGGER.debug("findByPolicySetIdAndVersion({}, {})", policySetId, version);
        return this.policySetVersionRepository.findByPolicySetIdAndVersion(policySetId, version)
                .map(this::toEntity);
    }

    @Override
    public Maybe<PolicySetVersion> findLatestByPolicySetId(String policySetId) {
        LOGGER.debug("findLatestByPolicySetId({})", policySetId);
        return this.policySetVersionRepository.findLatestByPolicySetId(policySetId)
                .map(this::toEntity);
    }

    @Override
    public Single<PolicySetVersion> create(PolicySetVersion item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create policySetVersion with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(insertStatement);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_POLICY_SET_ID, item.getPolicySetId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_VERSION, item.getVersion(), Integer.class);
        insertSpec = addQuotedField(insertSpec, COL_CONTENT, item.getContent(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_COMMIT_MESSAGE, item.getCommitMessage(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_BY, item.getCreatedBy(), String.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<PolicySetVersion> update(PolicySetVersion item) {
        LOGGER.debug("Update policySetVersion with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec updateSpec = getTemplate().getDatabaseClient().sql(updateStatement);

        updateSpec = addQuotedField(updateSpec, COL_ID, item.getId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_POLICY_SET_ID, item.getPolicySetId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_VERSION, item.getVersion(), Integer.class);
        updateSpec = addQuotedField(updateSpec, COL_CONTENT, item.getContent(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_COMMIT_MESSAGE, item.getCommitMessage(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateSpec = addQuotedField(updateSpec, COL_CREATED_BY, item.getCreatedBy(), String.class);

        Mono<Long> action = updateSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return this.policySetVersionRepository.deleteById(id);
    }

    @Override
    public Completable deleteByPolicySetId(String policySetId) {
        LOGGER.debug("deleteByPolicySetId({})", policySetId);
        return monoToCompletable(getTemplate().delete(JdbcPolicySetVersion.class)
                .matching(Query.query(where("policy_set_id").is(policySetId)))
                .all());
    }
}
