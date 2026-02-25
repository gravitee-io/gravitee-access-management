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
import io.gravitee.am.model.AuthorizationDataVersion;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcAuthorizationDataVersion;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringAuthorizationDataVersionRepository;
import io.gravitee.am.repository.management.api.AuthorizationDataVersionRepository;
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
public class JdbcAuthorizationDataVersionRepository extends AbstractJdbcRepository implements AuthorizationDataVersionRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_DATA_ID = "data_id";
    public static final String COL_DOMAIN_ID = "domain_id";
    public static final String COL_VERSION = "version";
    public static final String COL_CONTENT = "content";
    public static final String COL_COMMENT = "comment";
    public static final String COL_CREATED_BY = "created_by";
    public static final String COL_CREATED_AT = "created_at";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_DATA_ID,
            COL_DOMAIN_ID,
            COL_VERSION,
            COL_CONTENT,
            COL_COMMENT,
            COL_CREATED_BY,
            COL_CREATED_AT
    );

    private String insertStatement;

    @Autowired
    private SpringAuthorizationDataVersionRepository authorizationDataVersionRepository;

    protected AuthorizationDataVersion toEntity(JdbcAuthorizationDataVersion entity) {
        return mapper.map(entity, AuthorizationDataVersion.class);
    }

    protected JdbcAuthorizationDataVersion toJdbcEntity(AuthorizationDataVersion entity) {
        return mapper.map(entity, JdbcAuthorizationDataVersion.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insertStatement = createInsertStatement("authorization_data_versions", columns);
    }

    @Override
    public Flowable<AuthorizationDataVersion> findByDataId(String dataId) {
        LOGGER.debug("findByDataId({})", dataId);
        return this.authorizationDataVersionRepository.findByDataId(dataId)
                .map(this::toEntity);
    }

    @Override
    public Maybe<AuthorizationDataVersion> findByDataIdAndVersion(String dataId, int version) {
        LOGGER.debug("findByDataIdAndVersion({}, {})", dataId, version);
        return this.authorizationDataVersionRepository.findByDataIdAndVersion(dataId, version)
                .map(this::toEntity);
    }

    @Override
    public Single<AuthorizationDataVersion> create(AuthorizationDataVersion item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create authorizationDataVersion with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(insertStatement);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DATA_ID, item.getDataId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DOMAIN_ID, item.getDomainId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_VERSION, item.getVersion(), Integer.class);
        insertSpec = addQuotedField(insertSpec, COL_CONTENT, item.getContent(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_COMMENT, item.getComment(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_BY, item.getCreatedBy(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap(i -> this.authorizationDataVersionRepository.findById(item.getId())
                .map(this::toEntity).toSingle());
    }

    @Override
    public Completable deleteByDataId(String dataId) {
        LOGGER.debug("deleteByDataId({})", dataId);
        return monoToCompletable(getTemplate().delete(JdbcAuthorizationDataVersion.class)
                .matching(Query.query(where("data_id").is(dataId)))
                .all())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        LOGGER.debug("deleteByDomain({})", domainId);
        return monoToCompletable(getTemplate().delete(JdbcAuthorizationDataVersion.class)
                .matching(Query.query(where("domain_id").is(domainId)))
                .all())
                .observeOn(Schedulers.computation());
    }
}
