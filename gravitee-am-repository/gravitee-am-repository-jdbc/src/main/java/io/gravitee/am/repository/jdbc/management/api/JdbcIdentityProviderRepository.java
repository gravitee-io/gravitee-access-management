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
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcIdentityProviderRepository extends AbstractJdbcRepository implements IdentityProviderRepository {
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

        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = dbClient.insert().into("identities");

        // doesn't use the class introspection to allow the usage of Json type in PostgreSQL
        insertSpec = addQuotedField(insertSpec, "id", item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, "type", item.getType(), String.class);
        insertSpec = addQuotedField(insertSpec, "name", item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec, "external", item.isExternal(), boolean.class);
        insertSpec = addQuotedField(insertSpec, "reference_id", item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec, "configuration", item.getConfiguration(), String.class);
        insertSpec = addQuotedField(insertSpec, "reference_type", item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec, "created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, "updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, "domain_whitelist", ofNullable(item.getDomainWhitelist()).orElse(List.of()));
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, "mappers", item.getMappers());
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, "role_mapper", item.getRoleMapper());

        Mono<Integer> action = insertSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<IdentityProvider> update(IdentityProvider item) {
        LOGGER.debug("update identityProvider with id {}", item.getId());

        final DatabaseClient.GenericUpdateSpec updateSpec = dbClient.update().table("identities");
        Map<SqlIdentifier, Object> updateFields = new HashMap<>();
        // doesn't use the class introspection to allow the usage of Json type in PostgreSQL
        updateFields = addQuotedField(updateFields, "id", item.getId(), String.class);
        updateFields = addQuotedField(updateFields, "type", item.getType(), String.class);
        updateFields = addQuotedField(updateFields, "name", item.getName(), String.class);
        updateFields = addQuotedField(updateFields, "external", item.isExternal(), boolean.class);
        updateFields = addQuotedField(updateFields, "reference_id", item.getReferenceId(), String.class);
        updateFields = addQuotedField(updateFields, "configuration", item.getConfiguration(), String.class);
        updateFields = addQuotedField(updateFields, "reference_type", item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        updateFields = addQuotedField(updateFields, "created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields, "updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields, "domain_whitelist", ofNullable(item.getDomainWhitelist()).orElse(List.of()), List.class);
        updateFields = databaseDialectHelper.addJsonField(updateFields, "domain_whitelist", ofNullable(item.getDomainWhitelist()).orElse(List.of()));
        updateFields = databaseDialectHelper.addJsonField(updateFields, "mappers", item.getMappers());
        updateFields = databaseDialectHelper.addJsonField(updateFields, "role_mapper", item.getRoleMapper());

        Mono<Integer> action = updateSpec.using(Update.from(updateFields)).matching(from(where("id").is(item.getId()))).fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return this.identityProviderRepository.deleteById(id);
    }
}
