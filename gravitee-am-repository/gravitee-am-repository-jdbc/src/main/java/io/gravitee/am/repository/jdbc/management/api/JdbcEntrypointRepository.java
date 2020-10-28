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
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcEntrypoint;
import io.gravitee.am.repository.jdbc.management.api.spring.entrypoint.SpringEntrypointRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.entrypoint.SpringEntrypointTagRepository;
import io.gravitee.am.repository.management.api.EntrypointRepository;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava2Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcEntrypointRepository extends AbstractJdbcRepository implements EntrypointRepository {
    @Autowired
    private SpringEntrypointRepository entrypointRepository;

    @Autowired
    private SpringEntrypointTagRepository tagRepository;

    protected Entrypoint toEntity(JdbcEntrypoint entity) {
        return mapper.map(entity, Entrypoint.class);
    }

    protected JdbcEntrypoint toJdbcEntity(Entrypoint entity) {
        return mapper.map(entity, JdbcEntrypoint.class);
    }

    @Override
    public Maybe<Entrypoint> findById(String id, String organizationId) {
        LOGGER.debug("findById({}, {})", id, organizationId);
        return entrypointRepository.findById(id, organizationId)
                .map(this::toEntity)
                .flatMap(entrypoint -> completeTags(entrypoint).toMaybe())
                .doOnError(error -> LOGGER.error("Unable to retrieve entrypoint with id={} and organization={}",
                        id, organizationId, error));
    }

    @Override
    public Flowable<Entrypoint> findAll(String organizationId) {
        LOGGER.debug("findAll({})", organizationId);

        return entrypointRepository.findAllByOrganization(organizationId).map(this::toEntity)
                .flatMap(entrypoint -> completeTags(entrypoint).toFlowable())
                .doOnError(error -> LOGGER.error("Unable to list all entrypoints with organization {}", organizationId, error));
    }

    private Single<Entrypoint> completeTags(Entrypoint entrypoint) {
        return tagRepository.findAllByEntrypoint(entrypoint.getId())
                .map(JdbcEntrypoint.Tag::getTag).toList().map(tags -> {
                    entrypoint.setTags(tags);
                    return entrypoint;
                });
    }

    @Override
    public Maybe<Entrypoint> findById(String id) {
        LOGGER.debug("findById({})", id);
        return entrypointRepository.findById(id)
                .map(this::toEntity)
                .flatMap(entrypoint -> completeTags(entrypoint).toMaybe())
                .doOnError(error -> LOGGER.error("Unable to retrieve entrypoint with id={} ", id, error));
    }

    @Override
    public Single<Entrypoint> create(Entrypoint item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create Entrypoint with id {}", item.getId());
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> action = dbClient.insert()
                .into(JdbcEntrypoint.class)
                .using(toJdbcEntity(item))
                .fetch().rowsUpdated();

        final List<String> tags = item.getTags();
        if (tags != null && !tags.isEmpty()) {
            action = action.then(Flux.fromIterable(tags).concatMap(tagValue -> {
                JdbcEntrypoint.Tag tag = new JdbcEntrypoint.Tag();
                tag.setTag(tagValue);
                tag.setEntrypointId(item.getId());
                return dbClient.insert().into(JdbcEntrypoint.Tag.class).using(tag).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        return monoToSingle(action.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to create entrypoint with id {}", item.getId(), error));
    }

    @Override
    public Single<Entrypoint> update(Entrypoint item) {
        LOGGER.debug("update Entrypoint with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> action = dbClient.update()
                .table(JdbcEntrypoint.class)
                .using(toJdbcEntity(item))
                .fetch().rowsUpdated();

        final List<String> tags = item.getTags();
        if (tags != null & !tags.isEmpty()) {
            action = action.then(Flux.fromIterable(tags).concatMap(tagValue -> {
                JdbcEntrypoint.Tag tag = new JdbcEntrypoint.Tag();
                tag.setTag(tagValue);
                tag.setEntrypointId(item.getId());
                return dbClient.insert().into(JdbcEntrypoint.Tag.class).using(tag).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        return monoToSingle(deleteTags(item.getId()).then(action).as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to create entrypoint with id {}", item.getId(), error));
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> delete = dbClient.delete().from(JdbcEntrypoint.class)
                .matching(from(where("id").is(id)))
                .fetch().rowsUpdated();

        return monoToCompletable(deleteTags(id).then(delete).as(trx::transactional))
                .doOnError(error -> LOGGER.error("Unable to delete entrypoint with id {}", id, error));
    }

    private Mono<Integer> deleteTags(String id) {
        return dbClient.delete().from(JdbcEntrypoint.Tag.class)
                .matching(from(where("entrypoint_id").is(id)))
                .fetch().rowsUpdated();
    }
}
