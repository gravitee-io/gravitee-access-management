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
import io.gravitee.am.model.Role;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcRole;
import io.gravitee.am.repository.jdbc.management.api.spring.role.SpringRoleOauthScopeRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.role.SpringRoleRepository;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcRoleRepository extends AbstractJdbcRepository implements RoleRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_SYSTEM = "system";
    public static final String COL_DEFAULT_ROLE = "default_role";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_REFERENCE_ID = "reference_id";
    public static final String COL_REFERENCE_TYPE = "reference_type";
    public static final String COL_ASSIGNABLE_TYPE = "assignable_type";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";
    public static final String COL_PERMISSION_ACLS = "permission_acls";

    private static final List<String> columns = List.of(COL_ID,
            COL_NAME,
            COL_SYSTEM,
            COL_DEFAULT_ROLE,
            COL_DESCRIPTION,
            COL_REFERENCE_ID,
            COL_REFERENCE_TYPE,
            COL_ASSIGNABLE_TYPE,
            COL_CREATED_AT,
            COL_UPDATED_AT,
            COL_PERMISSION_ACLS);

    private String INSERT_STATEMENT;
    private String UPDATE_STATEMENT;

    @Autowired
    private SpringRoleRepository roleRepository;

    @Autowired
    private SpringRoleOauthScopeRepository oauthScopeRepository;

    protected Role toEntity(JdbcRole entity) {
        return mapper.map(entity, Role.class);
    }

    protected JdbcRole toJdbcEntity(Role entity) {
        return mapper.map(entity, JdbcRole.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("roles", columns);
        this.UPDATE_STATEMENT = createUpdateStatement("roles", columns, List.of(COL_ID));
    }

    @Override
    public Flowable<Role> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findAll({}, {})", referenceType, referenceId);
        return roleRepository.findByReference(referenceType.name(), referenceId)
                .map(this::toEntity)
                .flatMap(role -> completeWithScopes(Maybe.just(role), role.getId()).toFlowable());
    }

    @Override
    public Single<Page<Role>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        LOGGER.debug("findAll({}, {}, {}, {})", referenceType, referenceId, page, size);
        return fluxToFlowable(getTemplate().select(Query
                        .query(where(COL_REFERENCE_ID).is(referenceId)
                        .and(where(COL_REFERENCE_TYPE).is(referenceType.name())))
                        .sort(Sort.by(COL_NAME).ascending())
                        .with(PageRequest.of(page, size)), JdbcRole.class))
                .map(this::toEntity)
                .flatMap(role -> completeWithScopes(Maybe.just(role), role.getId()).toFlowable())
                .toList()
                .flatMap(content -> roleRepository.countByReference(referenceType.name(), referenceId)
                        .map((count) -> new Page<Role>(content, page, count)));
    }

    @Override
    public Single<Page<Role>> search(ReferenceType referenceType, String referenceId, String query, int page, int size) {
        LOGGER.debug("search({}, {}, {}, {}, {})", referenceType, referenceId, query, page, size);

        boolean wildcardSearch = query.contains("*");
        String wildcardValue = query.replaceAll("\\*+", "%");

        String search = this.databaseDialectHelper.buildSearchRoleQuery(wildcardSearch, page, size);
        String count = this.databaseDialectHelper.buildCountRoleQuery(wildcardSearch);

        return fluxToFlowable(getTemplate().getDatabaseClient().sql(search)
                .bind("value", wildcardSearch ? wildcardValue : query)
                .bind("refId", referenceId)
                .bind("refType", referenceType.name())
                .map((row, rowMetadata) -> rowMapper.read(JdbcRole.class, row)).all())
                .map(this::toEntity)
                .flatMap(role -> completeWithScopes(Maybe.just(role), role.getId()).toFlowable())
                .toList()
                .flatMap(data -> monoToSingle(getTemplate().getDatabaseClient().sql(count)
                        .bind("value", wildcardSearch ? wildcardValue : query)
                        .bind("refId", referenceId)
                        .bind("refType", referenceType.name())
                        .map((row, rowMetadata) -> row.get(0, Long.class))
                        .first())
                        .map(total -> new Page<Role>(data, page, total)));
    }

    @Override
    public Flowable<Role> findByIdIn(List<String> ids) {
        LOGGER.debug("findByIdIn({})", ids);
        if (ids == null || ids.isEmpty()) {
            return Flowable.empty();
        }
        return roleRepository.findByIdIn(ids)
                .map(this::toEntity)
                .flatMap(role -> completeWithScopes(Maybe.just(role), role.getId()).toFlowable());
    }

    @Override
    public Maybe<Role> findById(ReferenceType referenceType, String referenceId, String role) {
        LOGGER.debug("findById({},{},{})", referenceType, referenceId, role);
        return completeWithScopes(roleRepository.findById(referenceType.name(), referenceId, role)
                .map(this::toEntity), role);
    }

    @Override
    public Maybe<Role> findByNameAndAssignableType(ReferenceType referenceType, String referenceId, String name, ReferenceType assignableType) {
        LOGGER.debug("findByNameAndAssignableType({},{},{},{})", referenceType, referenceId, name, assignableType);
        return roleRepository.findByNameAndAssignableType(referenceType.name(), referenceId, name, assignableType.name())
                .map(this::toEntity)
                .flatMap(role -> completeWithScopes(Maybe.just(role), role.getId()));
    }

    @Override
    public Flowable<Role> findByNamesAndAssignableType(ReferenceType referenceType, String referenceId, List<String> names, ReferenceType assignableType) {
        LOGGER.debug("findByNamesAndAssignableType({},{},{},{})", referenceType, referenceId, names, assignableType);
        return roleRepository.findByNamesAndAssignableType(referenceType.name(), referenceId, names, assignableType.name())
                .map(this::toEntity)
                .flatMapMaybe(role -> completeWithScopes(Maybe.just(role), role.getId()));
    }

    @Override
    public Maybe<Role> findById(String id) {
        LOGGER.debug("findById({})", id);
        return completeWithScopes(roleRepository.findById(id)
                .map(this::toEntity), id);
    }

    @Override
    public Single<Role> create(Role item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create Role with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(INSERT_STATEMENT);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_NAME, item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_SYSTEM, item.isSystem(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DEFAULT_ROLE, item.isDefaultRole(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DESCRIPTION, item.getDescription(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_ASSIGNABLE_TYPE, item.getAssignableType() == null ? null : item.getAssignableType().name(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        insertSpec = databaseDialectHelper.addJsonField(insertSpec, COL_PERMISSION_ACLS, item.getPermissionAcls());

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        final List<String> resourceScopes = item.getOauthScopes();
        if (resourceScopes != null && !resourceScopes.isEmpty()) {
            action = action.then(Flux.fromIterable(resourceScopes).concatMap(insertScopr(item)
            ).reduce(Long::sum));
        }

        return monoToSingle(action.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<Role> update(Role item) {
        LOGGER.debug("Update Role with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Void> deleteScopes = getTemplate().delete(JdbcRole.OAuthScope.class)
                .matching(Query.query(where("role_id").is(item.getId()))).all().then();

        DatabaseClient.GenericExecuteSpec update = getTemplate().getDatabaseClient().sql(UPDATE_STATEMENT);

        update = addQuotedField(update, COL_ID, item.getId(), String.class);
        update = addQuotedField(update, COL_NAME, item.getName(), String.class);
        update = addQuotedField(update, COL_SYSTEM, item.isSystem(), String.class);
        update = addQuotedField(update, COL_DEFAULT_ROLE, item.isDefaultRole(), String.class);
        update = addQuotedField(update, COL_DESCRIPTION, item.getDescription(), String.class);
        update = addQuotedField(update, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        update = addQuotedField(update, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        update = addQuotedField(update, COL_ASSIGNABLE_TYPE, item.getAssignableType() == null ? null : item.getAssignableType().name(), String.class);
        update = addQuotedField(update, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        update = databaseDialectHelper.addJsonField(update, COL_PERMISSION_ACLS, item.getPermissionAcls());

        Mono<Long> action = update.fetch().rowsUpdated();

        final List<String> resourceScopes = item.getOauthScopes();
        if (resourceScopes != null && !resourceScopes.isEmpty()) {
            action = action.then(Flux.fromIterable(resourceScopes).concatMap(insertScopr(item))
                    .reduce(Long::sum));
        }

        return monoToSingle(deleteScopes.then(action).as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    private Function<String, Publisher<? extends Long>> insertScopr(Role item) {
        return scope ->
                getTemplate().getDatabaseClient().sql("INSERT INTO role_oauth_scopes(role_id, scope) VALUES(:role_id, :scope)")
                        .bind("role_id", item.getId())
                        .bind("scope", scope)
                        .fetch().rowsUpdated();
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("Delete Role with id {}", id);

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> deleteScopes = getTemplate().delete(JdbcRole.OAuthScope.class)
                .matching(Query.query(where("role_id").is(id))).all();

        Mono<Integer> delete = getTemplate().delete(JdbcRole.class)
                .matching(Query.query(where(COL_ID).is(id))).all();

        return monoToCompletable(delete.then(deleteScopes.as(trx::transactional)));
    }

    private Maybe<Role> completeWithScopes(Maybe<Role> maybeRole, String id) {
        Maybe<List<String>> scopes = oauthScopeRepository.findAllByRole(id)
                .map(JdbcRole.OAuthScope::getScope)
                .toList()
                .toMaybe();

        return maybeRole.zipWith(scopes, (role, scope) -> {
            LOGGER.debug("findById({}) fetch {} oauth scopes", id, scope == null ? 0 : scope.size());
            role.setOauthScopes(scope);
            return role;
        });
    }
}
