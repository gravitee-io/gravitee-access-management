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
package io.gravitee.am.dataplane.jdbc.repository;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.dataplane.api.repository.AuthorizationDetailApprovalRepository;
import io.gravitee.am.dataplane.jdbc.repository.model.JdbcAuthorizationDetailApproval;
import io.gravitee.am.dataplane.jdbc.repository.spring.SpringAuthorizationDetailApprovalRepository;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest;
import io.gravitee.am.repository.jdbc.provider.common.DateHelper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

@Repository
public class JdbcAuthorizationDetailApprovalRepository extends AbstractJdbcRepository implements AuthorizationDetailApprovalRepository {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            AuthorizationDetailApprovalCursorRequest.SORT_FIELD_UPDATED_AT, "updated_at",
            AuthorizationDetailApprovalCursorRequest.SORT_FIELD_EXPIRES_AT, "expires_at",
            AuthorizationDetailApprovalCursorRequest.SORT_FIELD_TYPE, "type",
            AuthorizationDetailApprovalCursorRequest.SORT_FIELD_STATUS, "status");

    @Autowired
    private SpringAuthorizationDetailApprovalRepository springRepository;

    @Override
    public Flowable<AuthorizationDetailApproval> findByDomainAndUserAndClient(String domain, UserId userId, String clientId) {
        LOGGER.debug("findByDomainAndUserAndClient({}, {}, {})", domain, userId, clientId);
        return fluxToFlowable(getTemplate().select(JdbcAuthorizationDetailApproval.class)
                .matching(Query.query(userIdMatches(userId)
                        .and(client(clientId))
                        .and(domain(domain))
                        .and(notExpiredAt(LocalDateTime.now(UTC)))))
                .all())
                .map(JdbcAuthorizationDetailApproval::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CursorPage<AuthorizationDetailApproval, AuthorizationDetailApprovalCursorRequest>> findByDomainAndUser(
            String domain, UserId userId, AuthorizationDetailApprovalCursorRequest cursor, int limit) {
        LOGGER.debug("findByDomainAndUser({}, {}, {}, {})", domain, userId, cursor.getSortField(), limit);
        Map<String, Object> bindings = new HashMap<>();
        StringBuilder where = new StringBuilder(" FROM authorization_detail_approvals WHERE domain = :domain AND expires_at > :now AND ");
        bindings.put("domain", domain);
        bindings.put("now", LocalDateTime.now(UTC));
        where.append(userIdCondition(userId, bindings));
        if (cursor.getClientId() != null) {
            where.append(" AND client_id = :clientId");
            bindings.put("clientId", cursor.getClientId());
        }
        if (cursor.getType() != null) {
            if (cursor.getType().contains("*")) {
                where.append(" AND UPPER(type) LIKE :type").append(databaseDialectHelper.getLikeEscapeClause());
                bindings.put("type", databaseDialectHelper.toWildcardLikePattern(cursor.getType().toUpperCase(Locale.ROOT)));
            } else {
                where.append(" AND UPPER(type) = :type");
                bindings.put("type", cursor.getType().toUpperCase(Locale.ROOT));
            }
        }
        Single<Long> count = monoToSingle(bind(getTemplate().getDatabaseClient().sql("SELECT COUNT(*)" + where), Map.copyOf(bindings))
                .map((row, rowMetadata) -> row.get(0, Long.class))
                .first());

        String column = SORT_COLUMNS.get(cursor.getSortField());
        String direction = cursor.getSortDirection().isAscending() ? " ASC" : " DESC";
        if (!cursor.isFirstPage()) {
            String comparator = cursor.getSortDirection().isAscending() ? " > " : " < ";
            where.append(" AND (").append(column).append(comparator).append(":lastSortValue OR (")
                    .append(column).append(" = :lastSortValue AND id").append(comparator).append(":lastId))");
            bindings.put("lastSortValue", cursor.isDateSort()
                    ? DateHelper.toLocalDateTime(new Date(Long.parseLong(cursor.getLastSortValue())))
                    : cursor.getLastSortValue());
            bindings.put("lastId", cursor.getLastId());
        }
        int offset = cursor.isFirstPage() ? cursor.getPage() * limit : 0;
        String select = "SELECT *" + where
                + databaseDialectHelper.buildPagingClauseUsingOffset(column + direction + ", id" + direction, offset, limit + 1);
        Single<List<AuthorizationDetailApproval>> items = fluxToFlowable(bind(getTemplate().getDatabaseClient().sql(select), bindings)
                .map((row, rowMetadata) -> rowMapper.read(JdbcAuthorizationDetailApproval.class, row))
                .all())
                .map(JdbcAuthorizationDetailApproval::toEntity)
                .toList();
        return Single.zip(count, items, (totalCount, found) -> {
                    if (found.size() <= limit) {
                        return new CursorPage<>(found, (AuthorizationDetailApprovalCursorRequest) null, totalCount);
                    }
                    List<AuthorizationDetailApproval> data = found.subList(0, limit);
                    return new CursorPage<>(data, cursor.next(data.get(limit - 1)), totalCount);
                })
                .observeOn(Schedulers.computation());
    }

    private static String userIdCondition(UserId userId, Map<String, Object> bindings) {
        String external = USER_EXTERNAL_ID_FIELD + " = :userExternalId AND " + USER_SOURCE_FIELD + " = :userSource";
        if (userId.hasExternal()) {
            bindings.put("userExternalId", userId.externalId());
            bindings.put("userSource", userId.source());
        }
        if (userId.id() != null) {
            bindings.put("userId", userId.id());
        }
        if (userId.id() != null && userId.hasExternal()) {
            return "(" + USER_ID_FIELD + " = :userId OR (" + external + "))";
        } else if (userId.hasExternal()) {
            return "(" + external + ")";
        } else if (userId.id() != null) {
            return USER_ID_FIELD + " = :userId";
        }
        throw new IllegalArgumentException("attempt to search by an empty UserId");
    }

    private static DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec, Map<String, Object> bindings) {
        for (Map.Entry<String, Object> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return spec;
    }

    @Override
    public Single<AuthorizationDetailApproval> upsert(AuthorizationDetailApproval approval) {
        return monoToMaybe(getTemplate().select(JdbcAuthorizationDetailApproval.class)
                .matching(Query.query(userIdMatches(approval.getUserId())
                        .and(client(approval.getClientId()))
                        .and(domain(approval.getDomain()))
                        .and(where("digest").is(approval.getDigest()))))
                .first())
                .map(JdbcAuthorizationDetailApproval::toEntity)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(existing -> {
                    if (existing.isEmpty()) {
                        approval.setCreatedAt(new Date());
                        approval.setUpdatedAt(approval.getCreatedAt());
                        return create(approval);
                    }
                    approval.setId(existing.get().getId());
                    approval.setCreatedAt(existing.get().getCreatedAt());
                    approval.setUpdatedAt(new Date());
                    return update(approval);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomain(String domain) {
        LOGGER.debug("deleteByDomain({})", domain);
        return monoToCompletable(getTemplate().delete(JdbcAuthorizationDetailApproval.class)
                .matching(Query.query(domain(domain))).all())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationDetailApproval> findById(String id) {
        LOGGER.debug("findById({})", id);
        LocalDateTime now = LocalDateTime.now(UTC);
        return springRepository.findById(id)
                .filter(bean -> bean.getExpiresAt() == null || bean.getExpiresAt().isAfter(now))
                .map(JdbcAuthorizationDetailApproval::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationDetailApproval> create(AuthorizationDetailApproval item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create AuthorizationDetailApproval with id {}", item.getId());
        return monoToSingle(getTemplate().insert(JdbcAuthorizationDetailApproval.of(item)))
                .map(JdbcAuthorizationDetailApproval::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationDetailApproval> update(AuthorizationDetailApproval item) {
        LOGGER.debug("Update AuthorizationDetailApproval with id {}", item.getId());
        return springRepository.save(JdbcAuthorizationDetailApproval.of(item))
                .map(JdbcAuthorizationDetailApproval::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("Delete AuthorizationDetailApproval with id {}", id);
        return springRepository.deleteById(id)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable purgeExpiredData() {
        LOGGER.debug("purgeExpiredData()");
        return monoToCompletable(getTemplate().delete(JdbcAuthorizationDetailApproval.class)
                .matching(Query.query(where("expires_at").lessThan(LocalDateTime.now(UTC)))).all())
                .observeOn(Schedulers.computation());
    }

    private static Criteria client(String clientId) {
        return where("client_id").is(clientId);
    }

    private static Criteria domain(String domain) {
        return where("domain").is(domain);
    }

    private static Criteria notExpiredAt(LocalDateTime now) {
        return where("expires_at").isNull().or(where("expires_at").greaterThan(now));
    }
}
