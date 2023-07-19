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
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAccessPolicy;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringAccessPolicyRepository;
import io.gravitee.am.repository.management.api.AccessPolicyRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcAccessPolicyRepository extends AbstractJdbcRepository implements AccessPolicyRepository, InitializingBean {

    @Autowired
    protected SpringAccessPolicyRepository accessPolicyRepository;

    protected AccessPolicy toAccessPolicy(JdbcAccessPolicy entity) {
        return mapper.map(entity, AccessPolicy.class);
    }

    public static final String COL_ID = "id";
    public static final String COL_TYPE = "type";
    public static final String COL_ENABLED = "enabled";
    public static final String COL_NAME = "name";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_ORDER = "order";
    public static final String COL_CONDITION = "condition";
    public static final String COL_DOMAIN = "domain";
    public static final String COL_RESOURCE = "resource";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    private static List<String> columns = List.of(COL_ID,COL_TYPE,
            COL_ENABLED,
            COL_NAME,
            COL_DESCRIPTION,
            COL_ORDER,
            COL_CONDITION,
            COL_DOMAIN,
            COL_RESOURCE,
            COL_CREATED_AT,
            COL_UPDATED_AT);

    private String INSERT_STATEMENT;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("uma_access_policies", columns);
    }

    @Override
    public Single<Page<AccessPolicy>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("findByDomain(domain:{}, page:{}, size:{})", domain, page, size);
        return fluxToFlowable(getTemplate().select(JdbcAccessPolicy.class)
                .matching(
                        query(where(COL_DOMAIN).is(domain))
                                .sort(Sort.by(Sort.Order.desc(COL_UPDATED_AT)))
                                .with(PageRequest.of(page, size))).all()).toList()
                .map(content -> content.stream().map(this::toAccessPolicy).collect(Collectors.toList()))
                .flatMap(content -> accessPolicyRepository.countByDomain(domain)
                        .map((count) -> new Page<>(content, page, count)));
    }

    @Override
    public Flowable<AccessPolicy> findByDomainAndResource(String domain, String resource) {
        LOGGER.debug("findByDomainAndResource(domain:{}, resources:{})", domain, resource);
        return accessPolicyRepository.findByDomainAndResource(domain, resource).map(this::toAccessPolicy);
    }

    @Override
    public Flowable<AccessPolicy> findByResources(List<String> resources) {
        LOGGER.debug("findByResources({})", resources);
        return accessPolicyRepository.findByResourceIn(resources).map(this::toAccessPolicy);
    }

    @Override
    public Single<Long> countByResource(String resource) {
        LOGGER.debug("countByResource({})", resource);
        return accessPolicyRepository.countByResource(resource);
    }

    @Override
    public Maybe<AccessPolicy> findById(String id) {
        LOGGER.debug("findById({})", id);
        return accessPolicyRepository.findById(id)
                .map(this::toAccessPolicy);
    }

    @Override
    public Single<AccessPolicy> create(AccessPolicy item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create AccessPolicy with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec sql = getTemplate().getDatabaseClient().sql(INSERT_STATEMENT);
        sql = addQuotedField(sql, COL_ID, item.getId(), String.class);
        sql = addQuotedField(sql, COL_TYPE, item.getType() == null ? null : item.getType().name(), String.class);
        sql = addQuotedField(sql, COL_ENABLED, item.isEnabled(), Boolean.class);
        sql = addQuotedField(sql, COL_NAME, item.getName(), String.class);
        sql = addQuotedField(sql, COL_DESCRIPTION, item.getDescription(), String.class);
        sql = addQuotedField(sql, COL_ORDER, item.getOrder(), Integer.class); // keyword
        sql = addQuotedField(sql, COL_CONDITION, item.getCondition(), String.class);
        sql = addQuotedField(sql, COL_DOMAIN, item.getDomain(), String.class);
        sql = addQuotedField(sql, COL_RESOURCE, item.getResource(), String.class);
        sql = addQuotedField(sql, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        sql = addQuotedField(sql, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        return monoToSingle(sql.fetch().rowsUpdated()).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<AccessPolicy> update(AccessPolicy item) {
        LOGGER.debug("update AccessPolicy with id {}", item.getId());

        Map<SqlIdentifier, Object> updateFields = new HashMap<>();
        updateFields = addQuotedField(updateFields, COL_ID, item.getId());
        updateFields = addQuotedField(updateFields, COL_TYPE, item.getType() == null ? null : item.getType().name());
        updateFields = addQuotedField(updateFields, COL_ENABLED, item.isEnabled());
        updateFields = addQuotedField(updateFields, COL_NAME, item.getName());
        updateFields = addQuotedField(updateFields, COL_DESCRIPTION, item.getDescription());
        updateFields = addQuotedField(updateFields, COL_ORDER, item.getOrder());
        updateFields = addQuotedField(updateFields, COL_CONDITION, item.getCondition());
        updateFields = addQuotedField(updateFields, COL_DOMAIN, item.getDomain());
        updateFields = addQuotedField(updateFields, COL_RESOURCE, item.getResource());
        updateFields = addQuotedField(updateFields, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null));
        updateFields = addQuotedField(updateFields, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null));

        return monoToSingle(getTemplate().update(query(where(COL_ID).is(item.getId())), Update.from(updateFields), JdbcAccessPolicy.class))
                .flatMap(__ -> Single.defer(() -> this.findById(item.getId()).toSingle()));
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete AccessPolicy with id {}", id);
        return accessPolicyRepository.deleteById(id);
    }
}
