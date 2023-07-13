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
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcIdentityProvider;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringIdentityProviderRepository;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static java.util.Optional.ofNullable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcIdentityProviderRepository extends AbstractJdbcRepository implements IdentityProviderRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_TYPE = "type";
    public static final String COL_NAME = "name";
    public static final String COL_SYSTEM = "system";
    public static final String COL_EXTERNAL = "external";
    public static final String COL_REFERENCE_ID = "reference_id";
    public static final String COL_CONFIGURATION = "configuration";
    public static final String COL_REFERENCE_TYPE = "reference_type";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";
    public static final String COL_DOMAIN_WHITELIST = "domain_whitelist";
    public static final String COL_MAPPERS = "mappers";
    public static final String COL_ROLE_MAPPER = "role_mapper";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_TYPE,
            COL_NAME,
            COL_SYSTEM,
            COL_EXTERNAL,
            COL_REFERENCE_ID,
            COL_CONFIGURATION,
            COL_REFERENCE_TYPE,
            COL_CREATED_AT,
            COL_UPDATED_AT,
            COL_DOMAIN_WHITELIST,
            COL_MAPPERS,
            COL_ROLE_MAPPER
    );

    private String INSERT_STATEMENT;
    private String UPDATE_STATEMENT;

    @Autowired
    private SpringIdentityProviderRepository identityProviderRepository;

    protected IdentityProvider toEntity(JdbcIdentityProvider entity) {
        IdentityProvider idp = mapper.map(entity, IdentityProvider.class);
        // init to empty map t adopt same behaviour as Mongo Repository
        idp.setRoleMapper(ofNullable(idp.getRoleMapper()).orElse(new HashMap<>()));
        idp.setMappers(ofNullable(idp.getMappers()).orElse(new HashMap<>()));
        idp.setDomainWhitelist(ofNullable(idp.getDomainWhitelist()).orElse(new ArrayList<>()));
        return idp;
    }

    protected JdbcIdentityProvider toJdbcEntity(IdentityProvider entity) {
        return mapper.map(entity, JdbcIdentityProvider.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("identities", columns);
        this.UPDATE_STATEMENT = createUpdateStatement("identities", columns, List.of(COL_ID));
    }

    @Override
    public Flowable<IdentityProvider> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findAll({}, {}", referenceType, referenceId);
        return this.identityProviderRepository.findAll(referenceType.name(), referenceId)
                .map(this::toEntity);
    }

    @Override
    public Flowable<IdentityProvider> findAll(ReferenceType referenceType) {
        LOGGER.debug("findAll()");
        return this.identityProviderRepository.findAll(referenceType.name())
                .map(this::toEntity);
    }

    @Override
    public Flowable<IdentityProvider> findAll() {
        LOGGER.debug("findAll()");
        return this.identityProviderRepository.findAll()
                .map(this::toEntity);
    }

    @Override
    public Maybe<IdentityProvider> findById(ReferenceType referenceType, String referenceId, String identityProviderId) {
        LOGGER.debug("findById({},{},{})", referenceType, referenceId, identityProviderId);
        return this.identityProviderRepository.findById(referenceType.name(), referenceId, identityProviderId)
                .map(this::toEntity);
    }

    @Override
    public Maybe<IdentityProvider> findById(String id) {
        LOGGER.debug("findById({})", id);
        return this.identityProviderRepository.findById(id)
                .map(this::toEntity);
    }

    @Override
    public Single<IdentityProvider> create(IdentityProvider item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create identityProvider with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = template.getDatabaseClient().sql(INSERT_STATEMENT);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_TYPE, item.getType(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_NAME, item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_SYSTEM, item.isSystem(), boolean.class);
        insertSpec = addQuotedField(insertSpec, COL_EXTERNAL, item.isExternal(), boolean.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CONFIGURATION, item.getConfiguration(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, COL_DOMAIN_WHITELIST, ofNullable(item.getDomainWhitelist()).orElse(List.of()));
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, COL_MAPPERS, item.getMappers());
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, COL_ROLE_MAPPER, item.getRoleMapper());

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<IdentityProvider> update(IdentityProvider item) {
        LOGGER.debug("update identityProvider with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec update = template.getDatabaseClient().sql(UPDATE_STATEMENT);

        update = addQuotedField(update, COL_ID, item.getId(), String.class);
        update = addQuotedField(update, COL_TYPE, item.getType(), String.class);
        update = addQuotedField(update, COL_NAME, item.getName(), String.class);
        update = addQuotedField(update, COL_SYSTEM, item.isSystem(), boolean.class);
        update = addQuotedField(update, COL_EXTERNAL, item.isExternal(), boolean.class);
        update = addQuotedField(update, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        update = addQuotedField(update, COL_CONFIGURATION, item.getConfiguration(), String.class);
        update = addQuotedField(update, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        update = addQuotedField(update, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        update = databaseDialectHelper.addJsonField(update, COL_DOMAIN_WHITELIST, ofNullable(item.getDomainWhitelist()).orElse(List.of()));
        update = databaseDialectHelper.addJsonField(update, COL_MAPPERS, item.getMappers());
        update = databaseDialectHelper.addJsonField(update, COL_ROLE_MAPPER, item.getRoleMapper());

        Mono<Long> action = update.fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return this.identityProviderRepository.deleteById(id);
    }
}
