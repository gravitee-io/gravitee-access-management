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
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static org.springframework.data.relational.core.sql.SqlIdentifier.quoted;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcGroupRepository extends AbstractJdbcRepository implements GroupRepository {
    private final int CONCURRENT_FLATMAP = 1;

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
    public Single<List<Group>> findByMember(String memberId) {
        LOGGER.debug("findByMember({})", memberId);

        Flowable<JdbcGroup> flow = fluxToFlowable(dbClient.execute("SELECT * FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g INNER JOIN group_members m ON g.id = m.group_id where m.member = :mid")
                .bind("mid", memberId)
                .as(JdbcGroup.class)
                .fetch()
                .all());

        return flow.map(this::toEntity)
                .flatMap(group -> completeWithMembersAndRole(Maybe.just(group), group.getId()).toFlowable(), CONCURRENT_FLATMAP)
                .toList()
                .doOnError(error -> LOGGER.error("Unable to retrieve groups by memberId '{}'", memberId, error));
    }

    @Override
    public Single<List<Group>> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findAll({}, {})", referenceType, referenceId);
        Flowable<JdbcGroup> flow = fluxToFlowable(dbClient.execute("SELECT * FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g WHERE g.reference_id = :refId AND g.reference_type = :refType")
                .bind("refId", referenceId)
                .bind("refType", referenceType.name())
                .as(JdbcGroup.class)
                .fetch()
                .all());

        return flow.map(this::toEntity)
                .flatMap(group -> completeWithMembersAndRole(Maybe.just(group), group.getId()).toFlowable(), CONCURRENT_FLATMAP)
                .toList()
                .doOnError(error -> LOGGER.error("Unable to retrieve groups by referenceId '{}' and referenceType '{}'", referenceId, referenceType, error));
    }

    @Override
    public Single<List<Group>> findByDomain(String domain) {
        LOGGER.debug("findByDomain({})", domain);
        return findAll(ReferenceType.DOMAIN, domain);
    }

    @Override
    public Single<Page<Group>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        LOGGER.debug("findAll({}, {}, {}, {})", referenceType, referenceId, page, size);

        Single<Long> counter = monoToSingle(dbClient.execute("SELECT count(*) FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g WHERE g.reference_id = :refId AND g.reference_type = :refType")
                .bind("refId", referenceId)
                .bind("refType", referenceType.name())
                .as(Long.class)
                .fetch()
                .first());

        return fluxToFlowable(dbClient.select()
                .from(databaseDialectHelper.toSql(quoted("groups")) )
                .matching(from(where("reference_id").is(referenceId)
                        .and(where("reference_type").is(referenceType.name()))))
                .orderBy(Sort.Order.asc("id"))
                .page(PageRequest.of(page, size))
                .as(JdbcGroup.class).fetch().all())
                .map(this::toEntity)
                .flatMap(group -> completeWithMembersAndRole(Maybe.just(group), group.getId()).toFlowable(), CONCURRENT_FLATMAP)
                .toList()
                .flatMap(content -> counter.map((count) -> new Page<Group>(content, page, count)));
    }

    @Override
    public Single<Page<Group>> findByDomain(String domain, int page, int size) {
        LOGGER.debug("findByDomain({}, {}, {})", domain, page, size);
        return findAll(ReferenceType.DOMAIN, domain, page, size);
    }

    @Override
    public Single<List<Group>> findByIdIn(List<String> ids) {
        LOGGER.debug("findByIdIn with ids {}", ids);
        if (ids == null || ids.isEmpty()) {
            return Single.just(Collections.emptyList());
        }
        Flowable<JdbcGroup> flow = fluxToFlowable(dbClient.execute("SELECT * FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g WHERE g.id IN (:ids)")
                .bind("ids", ids)
                .as(JdbcGroup.class)
                .fetch()
                .all());

        return flow.map(this::toEntity).flatMap(group -> completeWithMembersAndRole(Maybe.just(group), group.getId()).toFlowable(), CONCURRENT_FLATMAP)
                .toList()
                .doOnError(error -> LOGGER.error("Unable to retrieve groups with the ids '{}'", ids, error));
    }

    @Override
    public Maybe<Group> findByName(ReferenceType referenceType, String referenceId, String groupName) {
        LOGGER.debug("findByName({}, {}, {})", referenceType, referenceId, groupName);
        Maybe<JdbcGroup> maybe = monoToMaybe(dbClient.execute("SELECT * FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g WHERE g.reference_id = :refId AND g.reference_type = :refType AND g.name = :name")
                .bind("refId", referenceId)
                .bind("refType", referenceType.name())
                .bind("name", groupName)
                .as(JdbcGroup.class)
                .fetch()
                .first());

        return maybe.map(this::toEntity)
                .flatMap(group -> completeWithMembersAndRole(Maybe.just(group), group.getId()))
                .doOnError(error -> LOGGER.error("Unable to retrieve group with referenceId {}, referenceType {} and groupName {}",
                        referenceId, referenceType, groupName, error));
    }

    @Override
    public Maybe<Group> findByDomainAndName(String domain, String groupName) {
        LOGGER.debug("findByDomainAndName({}, {})", domain, groupName);
        return findByName(ReferenceType.DOMAIN, domain, groupName);
    }

    @Override
    public Maybe<Group> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("findById({}, {}, {})", referenceType, referenceId, id);
        Maybe<JdbcGroup> maybe = monoToMaybe(dbClient.execute("SELECT * FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g WHERE g.reference_id = :refId AND g.reference_type = :refType AND g.id = :id")
                .bind("refId", referenceId)
                .bind("refType", referenceType.name())
                .bind("id", id)
                .as(JdbcGroup.class)
                .fetch()
                .first());

        return completeWithMembersAndRole(maybe.map(this::toEntity), id)
                .doOnError(error -> LOGGER.error("Unable to retrieve group with referenceId {}, referenceType {} and id {}",
                        referenceId, referenceType, id, error));
    }

    @Override
    public Maybe<Group> findById(String id) {
        LOGGER.debug("findById({})", id);
        Maybe<JdbcGroup> maybe = monoToMaybe(dbClient.execute("SELECT * FROM " +
                databaseDialectHelper.toSql(quoted("groups")) +
                " g WHERE g.id = :id")
                .bind("id", id)
                .as(JdbcGroup.class)
                .fetch()
                .first());

        return completeWithMembersAndRole(maybe.map(this::toEntity), id)
                .doOnError((error) -> LOGGER.error("unable to retrieve Group with id {}", id, error));
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

        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = dbClient.insert().into(quoted("groups"));
        insertSpec = addQuotedField(insertSpec,"id", item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec,"reference_id", item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec,"reference_type", item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec,"name", item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec,"description", item.getDescription(), String.class);
        insertSpec = addQuotedField(insertSpec,"created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec,"updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Integer> action = insertSpec.fetch().rowsUpdated();

        action = persistChildEntities(action, item);

        return monoToSingle(action.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to create group with id {}", item.getId(), error));
    }

    private Mono<Integer> persistChildEntities(Mono<Integer> actionFlow, Group item) {
        final List<String> roles = item.getRoles();
        if (roles != null && !roles.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(roles).concatMap(roleValue -> {
                JdbcGroup.JdbcRole role = new JdbcGroup.JdbcRole();
                role.setRole(roleValue);
                role.setGroupId(item.getId());
                return dbClient.insert().into(JdbcGroup.JdbcRole.class).using(role).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        final List<String> members = item.getMembers();
        if (members != null && !members.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(members).concatMap(memberValue -> {
                JdbcGroup.JdbcMember member = new JdbcGroup.JdbcMember();
                member.setMember(memberValue);
                member.setGroupId(item.getId());
                return dbClient.insert().into(JdbcGroup.JdbcMember.class).using(member).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        return actionFlow;
    }

    @Override
    public Single<Group> update(Group item) {
        LOGGER.debug("update Group with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        final DatabaseClient.GenericUpdateSpec updateSpec = dbClient.update().table(databaseDialectHelper.toSql(quoted("groups")));

        Map<SqlIdentifier, Object> updateFields = new HashMap<>();
        // doesn't use the class introspection to handle json objects
        updateFields = addQuotedField(updateFields,"id", item.getId(), String.class);
        updateFields = addQuotedField(updateFields,"reference_id", item.getReferenceId(), String.class);
        updateFields = addQuotedField(updateFields,"reference_type", item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        updateFields = addQuotedField(updateFields,"name", item.getName(), String.class);
        updateFields = addQuotedField(updateFields,"description", item.getDescription(), String.class);
        updateFields = addQuotedField(updateFields,"created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields,"updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Integer> action = updateSpec.using(Update.from(updateFields)).matching(from(where("id").is(item.getId()))).fetch().rowsUpdated();

        action = deleteChildEntities(item.getId()).then(action);
        action = persistChildEntities(action, item);

        return monoToSingle(action.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to delete group with id {}", item.getId(), error));
    }

    private Mono<Integer> deleteChildEntities(String groupId) {
        Mono<Integer> deleteRoles = dbClient.delete().from(JdbcGroup.JdbcRole.class).matching(from(where("group_id").is(groupId))).fetch().rowsUpdated();
        Mono<Integer> deleteMembers = dbClient.delete().from(JdbcGroup.JdbcMember.class).matching(from(where("group_id").is(groupId))).fetch().rowsUpdated();
        return deleteRoles.then(deleteMembers);
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete Group with id {}", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> delete = dbClient.delete().from(databaseDialectHelper.toSql(quoted("groups"))).matching(from(where("id").is(id))).fetch().rowsUpdated();
        return monoToCompletable(delete.then(deleteChildEntities(id)).as(trx::transactional))
                .doOnError((error) -> LOGGER.error("unable to delete Group with id {}", id, error));
    }
}
