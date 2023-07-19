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
import io.gravitee.am.model.Group;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcGroup;
import io.gravitee.am.repository.jdbc.management.api.spring.group.SpringGroupMemberRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.group.SpringGroupRoleRepository;
import io.gravitee.am.repository.management.api.GroupRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.sql.SqlIdentifier.quoted;
import static reactor.adapter.rxjava.RxJava3Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcGroupRepository extends AbstractJdbcRepository implements GroupRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_REFERENCE_ID = "reference_id";
    public static final String COL_REFERENCE_TYPE = "reference_type";
    public static final String COL_NAME = "name";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_REFERENCE_ID,
            COL_REFERENCE_TYPE,
            COL_NAME,
            COL_DESCRIPTION,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );

    private final int CONCURRENT_FLATMAP = 1;

    private String INSERT_STATEMENT;
    private String UPDATE_STATEMENT;

    @Autowired
    private SpringGroupRoleRepository roleRepository;

    @Autowired
    private SpringGroupMemberRepository memberRepository;

    protected Group toEntity(JdbcGroup entity) {
        return mapper.map(entity, Group.class);
    }

    protected JdbcGroup  toJdbcEntity(Group entity) {
        return mapper.map(entity, JdbcGroup .class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement(databaseDialectHelper.toSql(SqlIdentifier.quoted("groups")), columns);
        this.UPDATE_STATEMENT = createUpdateStatement(databaseDialectHelper.toSql(SqlIdentifier.quoted("groups")), columns, List.of(COL_ID));
    }

    @Override
    public Flowable<Group> findByMember(String memberId) {
        LOGGER.debug("findByMember({})", memberId);

        Flowable<JdbcGroup> flow = fluxToFlowable(getTemplate().getDatabaseClient().sql("SELECT * FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g INNER JOIN group_members m ON g.id = m.group_id where m.member = :mid")
                .bind("mid", memberId)
                .map((row, rowMetadata) ->rowMapper.read(JdbcGroup.class, row))
                .all());

        return flow.map(this::toEntity)
                .flatMap(group -> completeWithMembersAndRole(Maybe.just(group), group.getId()).toFlowable(), CONCURRENT_FLATMAP);
    }

    @Override
    public Flowable<Group> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findAll({}, {})", referenceType, referenceId);
        Flowable<JdbcGroup> flow = fluxToFlowable(getTemplate().getDatabaseClient().sql("SELECT * FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g WHERE g.reference_id = :refId AND g.reference_type = :refType")
                .bind("refId", referenceId)
                .bind("refType", referenceType.name())
                .map((row, rowMetadata) ->rowMapper.read(JdbcGroup.class, row))
                .all());

        return flow.map(this::toEntity)
                .flatMap(group -> completeWithMembersAndRole(Maybe.just(group), group.getId()).toFlowable(), CONCURRENT_FLATMAP);
    }

    @Override
    public Single<Page<Group>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        LOGGER.debug("findAll({}, {}, {}, {})", referenceType, referenceId, page, size);

        Single<Long> counter = monoToSingle(getTemplate().getDatabaseClient().sql("SELECT count(*) FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g WHERE g.reference_id = :refId AND g.reference_type = :refType")
                .bind("refId", referenceId)
                .bind("refType", referenceType.name())
                .map((row, rowMetadata) ->row.get(0, Long.class))
                .first());

        return fluxToFlowable(getTemplate().getDatabaseClient().sql("SELECT * FROM " +
                        databaseDialectHelper.toSql(quoted("groups")) +
                        " g WHERE g.reference_id = :refId AND g.reference_type = :refType " + databaseDialectHelper.buildPagingClause(page, size))
                        .bind("refId", referenceId)
                        .bind("refType", referenceType.name())
                .map((row, rowMetadata) ->rowMapper.read(JdbcGroup.class, row))
                .all())
                .map(this::toEntity)
                .flatMap(group -> completeWithMembersAndRole(Maybe.just(group), group.getId()).toFlowable(), CONCURRENT_FLATMAP)
                .toList()
                .flatMap(content -> counter.map((count) -> new Page<Group>(content, page, count)));
    }

    @Override
    public Flowable<Group> findByIdIn(List<String> ids) {
        LOGGER.debug("findByIdIn with ids {}", ids);
        if (ids == null || ids.isEmpty()) {
            return Flowable.empty();
        }

        Flowable<JdbcGroup> flow = fluxToFlowable(getTemplate().getDatabaseClient().sql("SELECT * FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g WHERE g.id IN (:ids)")
                .bind("ids", ids)
                .map((row, rowMetadata) ->rowMapper.read(JdbcGroup.class, row))
                .all());

        return flow.map(this::toEntity).flatMap(group -> completeWithMembersAndRole(Maybe.just(group), group.getId()).toFlowable(), CONCURRENT_FLATMAP);
    }

    @Override
    public Maybe<Group> findByName(ReferenceType referenceType, String referenceId, String groupName) {
        LOGGER.debug("findByName({}, {}, {})", referenceType, referenceId, groupName);
        Maybe<JdbcGroup> maybe = monoToMaybe(getTemplate().getDatabaseClient().sql("SELECT * FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g WHERE g.reference_id = :refId AND g.reference_type = :refType AND g.name = :name")
                .bind("refId", referenceId)
                .bind("refType", referenceType.name())
                .bind("name", groupName)
                .map((row, rowMetadata) ->rowMapper.read(JdbcGroup.class, row))
                .first());

        return maybe.map(this::toEntity)
                .flatMap(group -> completeWithMembersAndRole(Maybe.just(group), group.getId()));
    }

    @Override
    public Maybe<Group> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("findById({}, {}, {})", referenceType, referenceId, id);
        Maybe<JdbcGroup> maybe = monoToMaybe(getTemplate().getDatabaseClient().sql("SELECT * FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g WHERE g.reference_id = :refId AND g.reference_type = :refType AND g.id = :id")
                .bind("refId", referenceId)
                .bind("refType", referenceType.name())
                .bind("id", id)
                .map((row, rowMetadata) ->rowMapper.read(JdbcGroup.class, row))
                .first());

        return completeWithMembersAndRole(maybe.map(this::toEntity), id);
    }

    @Override
    public Maybe<Group> findById(String id) {
        LOGGER.debug("findById({})", id);
        Maybe<JdbcGroup> maybe = monoToMaybe(getTemplate().getDatabaseClient().sql("SELECT * FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g WHERE g.id = :id")
                .bind("id", id)
                .map((row, rowMetadata) ->rowMapper.read(JdbcGroup.class, row))
                .first());

        return completeWithMembersAndRole(maybe.map(this::toEntity), id);
    }

    private Maybe<Group> completeWithMembersAndRole(Maybe<Group> maybeGroup, String id) {
        Maybe<List<String>> members = memberRepository.findAllByGroup(id)
                .map(JdbcGroup.JdbcMember::getMember)
                .toList()
                .toMaybe();

        Maybe<List<String>> roles = roleRepository.findAllByGroup(id)
                .map(JdbcGroup.JdbcRole::getRole)
                .toList()
                .toMaybe();

        return maybeGroup
                .zipWith(members, (grp, member) -> {
                    LOGGER.debug("findById({}) fetch {} group members", id, member == null ? 0 : member.size());
                    grp.setMembers(member);
                    return grp;
                }).zipWith(roles, (grp, role) -> {
                    LOGGER.debug("findById({}) fetch {} group roles", id, role == null ? 0 : role.size());
                    grp.setRoles(role);
                    return grp;
                });
    }

    @Override
    public Single<Group> create(Group item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create Group with id {}", item.getId());
        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(INSERT_STATEMENT);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_NAME, item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_DESCRIPTION, item.getDescription(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();

        action = persistChildEntities(action, item);

        return monoToSingle(action.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    private Mono<Long> persistChildEntities(Mono<Long> actionFlow, Group item) {
        final List<String> roles = item.getRoles();
        if (roles != null && !roles.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(roles).concatMap(roleValue ->
                    getTemplate().getDatabaseClient()
                            .sql("INSERT INTO group_roles(group_id, role) VALUES (:gid, :role)")
                            .bind("gid", item.getId())
                            .bind("role", roleValue).fetch().rowsUpdated()
            ).reduce(Long::sum));
        }

        final List<String> members = item.getMembers();
        if (members != null && !members.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(members).concatMap(memberValue ->
                    getTemplate().getDatabaseClient()
                            .sql("INSERT INTO group_members(group_id, member) VALUES (:gid, :member)")
                            .bind("gid", item.getId())
                            .bind("member", memberValue).fetch().rowsUpdated()
            ).reduce(Long::sum));
        }

        return actionFlow;
    }

    @Override
    public Single<Group> update(Group item) {
        LOGGER.debug("update Group with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec update = getTemplate().getDatabaseClient().sql(UPDATE_STATEMENT);

        update = addQuotedField(update, COL_ID, item.getId(), String.class);
        update = addQuotedField(update, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        update = addQuotedField(update, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        update = addQuotedField(update, COL_NAME, item.getName(), String.class);
        update = addQuotedField(update, COL_DESCRIPTION, item.getDescription(), String.class);
        update = addQuotedField(update, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = update.fetch().rowsUpdated();
        action = deleteChildEntities(item.getId()).then(action);
        action = persistChildEntities(action, item);

        return monoToSingle(action.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    private Mono<Long> deleteChildEntities(String groupId) {
        final Query criteria = Query.query(where("group_id").is(groupId));
        Mono<Integer> deleteRoles = getTemplate().delete(criteria, JdbcGroup.JdbcRole.class);
        Mono<Integer> deleteMembers = getTemplate().delete(criteria, JdbcGroup.JdbcMember.class);
        return deleteRoles.then(deleteMembers).map(Integer::longValue);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete Group with id {}", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Long> delete = getTemplate().getDatabaseClient().sql("DELETE FROM " + databaseDialectHelper.toSql(quoted("groups")) + " WHERE id = :id").bind(COL_ID, id).fetch().rowsUpdated();
        return monoToCompletable(delete.then(deleteChildEntities(id)).as(trx::transactional));
    }
}
