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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.AuthorizationBundle;
import io.gravitee.am.model.BundleComponentRef;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAuthorizationBundle;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringAuthorizationBundleRepository;
import io.gravitee.am.repository.management.api.AuthorizationBundleRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
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
public class JdbcAuthorizationBundleRepository extends AbstractJdbcRepository implements AuthorizationBundleRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_DOMAIN_ID = "domain_id";
    public static final String COL_NAME = "name";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_ENGINE_TYPE = "engine_type";
    public static final String COL_POLICY_SETS_JSON = "policy_sets_json";
    public static final String COL_ENTITY_STORES_JSON = "entity_stores_json";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_DOMAIN_ID,
            COL_NAME,
            COL_DESCRIPTION,
            COL_ENGINE_TYPE,
            COL_POLICY_SETS_JSON,
            COL_ENTITY_STORES_JSON,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private String insertStatement;
    private String updateStatement;

    @Autowired
    private SpringAuthorizationBundleRepository authorizationBundleRepository;

    protected AuthorizationBundle toEntity(JdbcAuthorizationBundle entity) {
        AuthorizationBundle bundle = new AuthorizationBundle();
        bundle.setId(entity.getId());
        bundle.setDomainId(entity.getDomainId());
        bundle.setName(entity.getName());
        bundle.setDescription(entity.getDescription());
        bundle.setEngineType(entity.getEngineType());
        bundle.setPolicySets(deserializeRefs(entity.getPolicySetsJson()));
        bundle.setEntityStores(deserializeRefs(entity.getEntityStoresJson()));
        bundle.setCreatedAt(dateConverter.convertFrom(entity.getCreatedAt(), null));
        bundle.setUpdatedAt(dateConverter.convertFrom(entity.getUpdatedAt(), null));
        return bundle;
    }

    protected JdbcAuthorizationBundle toJdbcEntity(AuthorizationBundle bundle) {
        JdbcAuthorizationBundle jdbc = new JdbcAuthorizationBundle();
        jdbc.setId(bundle.getId());
        jdbc.setDomainId(bundle.getDomainId());
        jdbc.setName(bundle.getName());
        jdbc.setDescription(bundle.getDescription());
        jdbc.setEngineType(bundle.getEngineType());
        jdbc.setPolicySetsJson(serializeRefs(bundle.getPolicySets()));
        jdbc.setEntityStoresJson(serializeRefs(bundle.getEntityStores()));
        jdbc.setCreatedAt(dateConverter.convertTo(bundle.getCreatedAt(), null));
        jdbc.setUpdatedAt(dateConverter.convertTo(bundle.getUpdatedAt(), null));
        return jdbc;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insertStatement = createInsertStatement("authorization_bundles", columns);
        this.updateStatement = createUpdateStatement("authorization_bundles", columns, List.of(COL_ID));
    }

    @Override
    public Maybe<AuthorizationBundle> findById(String id) {
        LOGGER.debug("findById({})", id);
        return this.authorizationBundleRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Flowable<AuthorizationBundle> findByDomain(String domainId) {
        LOGGER.debug("findByDomain({})", domainId);
        return this.authorizationBundleRepository.findByDomain(domainId)
                .map(this::toEntity);
    }

    @Override
    public Maybe<AuthorizationBundle> findByDomainAndId(String domainId, String id) {
        LOGGER.debug("findByDomainAndId({}, {})", domainId, id);
        return this.authorizationBundleRepository.findByDomainAndId(domainId, id)
                .map(this::toEntity);
    }

    @Override
    public Flowable<AuthorizationBundle> findByDomainAndComponentId(String domainId, String componentId) {
        LOGGER.debug("findByDomainAndComponentId({}, {})", domainId, componentId);
        // POC: use LIKE on JSON columns to find bundles referencing this component
        String query = "SELECT * FROM authorization_bundles WHERE domain_id = :domainId " +
                "AND (policy_sets_json LIKE :pattern OR entity_stores_json LIKE :pattern)";
        String pattern = "%" + componentId + "%";
        return Flowable.fromPublisher(
                getTemplate().getDatabaseClient().sql(query)
                        .bind("domainId", domainId)
                        .bind("pattern", pattern)
                        .map((row, metadata) -> {
                            JdbcAuthorizationBundle jdbc = new JdbcAuthorizationBundle();
                            jdbc.setId(row.get(COL_ID, String.class));
                            jdbc.setDomainId(row.get(COL_DOMAIN_ID, String.class));
                            jdbc.setName(row.get(COL_NAME, String.class));
                            jdbc.setDescription(row.get(COL_DESCRIPTION, String.class));
                            jdbc.setEngineType(row.get(COL_ENGINE_TYPE, String.class));
                            jdbc.setPolicySetsJson(row.get(COL_POLICY_SETS_JSON, String.class));
                            jdbc.setEntityStoresJson(row.get(COL_ENTITY_STORES_JSON, String.class));
                            jdbc.setCreatedAt(row.get(COL_CREATED_AT, LocalDateTime.class));
                            jdbc.setUpdatedAt(row.get(COL_UPDATED_AT, LocalDateTime.class));
                            return jdbc;
                        })
                        .all())
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationBundle> create(AuthorizationBundle item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create authorizationBundle with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(insertStatement);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DOMAIN_ID, item.getDomainId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_NAME, item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DESCRIPTION, item.getDescription(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_ENGINE_TYPE, item.getEngineType(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_POLICY_SETS_JSON, serializeRefs(item.getPolicySets()), String.class);
        insertSpec = addQuotedField(insertSpec, COL_ENTITY_STORES_JSON, serializeRefs(item.getEntityStores()), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<AuthorizationBundle> update(AuthorizationBundle item) {
        LOGGER.debug("Update authorizationBundle with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec updateSpec = getTemplate().getDatabaseClient().sql(updateStatement);

        updateSpec = addQuotedField(updateSpec, COL_ID, item.getId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_DOMAIN_ID, item.getDomainId(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_NAME, item.getName(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_DESCRIPTION, item.getDescription(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_ENGINE_TYPE, item.getEngineType(), String.class);
        updateSpec = addQuotedField(updateSpec, COL_POLICY_SETS_JSON, serializeRefs(item.getPolicySets()), String.class);
        updateSpec = addQuotedField(updateSpec, COL_ENTITY_STORES_JSON, serializeRefs(item.getEntityStores()), String.class);
        updateSpec = addQuotedField(updateSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateSpec = addQuotedField(updateSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = updateSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap(i -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return this.authorizationBundleRepository.deleteById(id);
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        LOGGER.debug("deleteByDomain({})", domainId);
        return monoToCompletable(getTemplate().delete(JdbcAuthorizationBundle.class)
                .matching(Query.query(where("domain_id").is(domainId)))
                .all())
                .observeOn(Schedulers.computation());
    }

    private static String serializeRefs(List<BundleComponentRef> refs) {
        try {
            return refs != null ? JSON_MAPPER.writeValueAsString(refs) : "[]";
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static List<BundleComponentRef> deserializeRefs(String json) {
        try {
            return json != null ? JSON_MAPPER.readValue(json, JSON_MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, BundleComponentRef.class)) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
