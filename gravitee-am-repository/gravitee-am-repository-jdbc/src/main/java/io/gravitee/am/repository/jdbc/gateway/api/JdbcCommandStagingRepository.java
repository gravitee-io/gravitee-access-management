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
package io.gravitee.am.repository.jdbc.gateway.api;

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.command.CommandStaging;
import io.gravitee.am.repository.gateway.api.CommandStagingRepository;
import io.gravitee.am.repository.jdbc.gateway.api.model.JdbcCommandStaging;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.provider.common.DateHelper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author GraviteeSource Team
 */
@Repository
public class JdbcCommandStagingRepository extends AbstractJdbcRepository implements CommandStagingRepository {

    private static final String TERMINAL_CLIENTS_SEPARATOR = ",";

    @Override
    public Maybe<CommandStaging> createIfAbsent(CommandStaging commandStaging) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        commandStaging.setCreatedAt(DateHelper.toDate(now));
        commandStaging.setUpdatedAt(DateHelper.toDate(now));

        LOGGER.debug("Create CommandStaging with id {}", commandStaging.getId());

        JdbcCommandStaging jdbcEntity = toJdbcEntity(commandStaging);
        return monoToSingle(getTemplate().insert(jdbcEntity))
                .map(this::toEntity)
                .toMaybe()
                .onErrorResumeNext(error -> {
                    if (error instanceof DuplicateKeyException) {
                        LOGGER.debug("CommandStaging {} already exists, another node staged it first", commandStaging.getId());
                        return Maybe.empty();
                    }
                    return Maybe.error(error);
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<CommandStaging> findOldestByUpdateDate(Reference reference, int limit) {
        LOGGER.debug("Find oldest CommandStaging entries by update date with limit {}", limit);

        Query query = Query.query(where(REFERENCE_ID_FIELD).is(reference.id())
                        .and(where(REF_TYPE_FIELD).is(reference.type().name())))
                .sort(Sort.by("updated_at").ascending())
                .limit(limit);

        return fluxToFlowable(getTemplate().select(query, JdbcCommandStaging.class))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<CommandStaging> update(CommandStaging commandStaging) {
        LOGGER.debug("Update CommandStaging with id {}", commandStaging.getId());

        commandStaging.setUpdatedAt(DateHelper.toDate(LocalDateTime.now(ZoneOffset.UTC)));
        JdbcCommandStaging jdbcEntity = toJdbcEntity(commandStaging);

        return monoToSingle(getTemplate().update(jdbcEntity))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("Delete CommandStaging with id '{}'", id);

        return monoToCompletable(
                getTemplate().delete(JdbcCommandStaging.class)
                        .matching(Query.query(where("id").is(id)))
                        .all()
        ).observeOn(Schedulers.computation());
    }

    protected CommandStaging toEntity(JdbcCommandStaging jdbcEntity) {
        if (jdbcEntity == null) {
            return null;
        }

        CommandStaging commandStaging = new CommandStaging();
        commandStaging.setId(jdbcEntity.getId());
        commandStaging.setCommand(jdbcEntity.getCommand());
        commandStaging.setUserId(jdbcEntity.getUserId());
        commandStaging.setReferenceType(jdbcEntity.getReferenceType() != null ?
                ReferenceType.valueOf(jdbcEntity.getReferenceType()) : null);
        commandStaging.setReferenceId(jdbcEntity.getReferenceId());
        commandStaging.setAttempts(jdbcEntity.getAttempts());
        commandStaging.setTerminalClientIds(fromJoinedClients(jdbcEntity.getTerminalClients()));
        commandStaging.setCreatedAt(DateHelper.toDate(jdbcEntity.getCreatedAt()));
        commandStaging.setUpdatedAt(DateHelper.toDate(jdbcEntity.getUpdatedAt()));
        return commandStaging;
    }

    protected JdbcCommandStaging toJdbcEntity(CommandStaging commandStaging) {
        if (commandStaging == null) {
            return null;
        }

        JdbcCommandStaging jdbcEntity = new JdbcCommandStaging();
        jdbcEntity.setId(commandStaging.getId());
        jdbcEntity.setCommand(commandStaging.getCommand());
        jdbcEntity.setUserId(commandStaging.getUserId());
        jdbcEntity.setReferenceType(commandStaging.getReferenceType() != null ?
                commandStaging.getReferenceType().name() : null);
        jdbcEntity.setReferenceId(commandStaging.getReferenceId());
        jdbcEntity.setAttempts(commandStaging.getAttempts());
        jdbcEntity.setTerminalClients(toJoinedClients(commandStaging.getTerminalClientIds()));
        jdbcEntity.setCreatedAt(DateHelper.toLocalDateTime(commandStaging.getCreatedAt()));
        jdbcEntity.setUpdatedAt(DateHelper.toLocalDateTime(commandStaging.getUpdatedAt()));
        return jdbcEntity;
    }

    private static String toJoinedClients(List<String> clientIds) {
        return clientIds == null || clientIds.isEmpty() ? null : String.join(TERMINAL_CLIENTS_SEPARATOR, clientIds);
    }

    private static List<String> fromJoinedClients(String joined) {
        return joined == null || joined.isBlank() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(joined.split(TERMINAL_CLIENTS_SEPARATOR)));
    }
}
