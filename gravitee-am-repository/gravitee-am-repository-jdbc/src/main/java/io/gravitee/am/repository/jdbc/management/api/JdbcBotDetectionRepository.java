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
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcBotDetection;
import io.gravitee.am.repository.management.api.BotDetectionRepository;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcBotDetectionRepository extends AbstractJdbcRepository implements BotDetectionRepository {

    public static final String REFERENCE_ID_FIELD = "reference_id";
    public static final String REF_TYPE_FIELD = "reference_type";
    public static final String ID_FIELD = "id";

    protected BotDetection toEntity(JdbcBotDetection entity) {
        return mapper.map(entity, BotDetection.class);
    }

    protected JdbcBotDetection toJdbcEntity(BotDetection entity) {
        return mapper.map(entity, JdbcBotDetection.class);
    }

    @Override
    public Flowable<BotDetection> findAll() {
        LOGGER.debug("findAll()");
        return fluxToFlowable(dbClient.select()
                .from(JdbcBotDetection.class)
                .fetch()
                .all())
                .map(this::toEntity);
    }

    @Override
    public Flowable<BotDetection> findByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findByReference({}, {})", referenceType, referenceId);
        return fluxToFlowable(dbClient.select()
                .from(JdbcBotDetection.class)
                .matching(from(where(REFERENCE_ID_FIELD).is(referenceId).and(where(REF_TYPE_FIELD).is(referenceType.name()))))
                .fetch()
                .all())
                .map(this::toEntity);
    }

    @Override
    public Maybe<BotDetection> findById(String id) {
        LOGGER.debug("findById({})", id);
        return monoToMaybe(dbClient.select()
                .from(JdbcBotDetection.class)
                .matching(from(where(ID_FIELD).is(id)))
                .fetch()
                .first())
                .map(this::toEntity);
    }

    @Override
    public Single<BotDetection> create(BotDetection item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create bot detection with id {}", item.getId());

        Mono<Integer> action = dbClient.insert()
                .into(JdbcBotDetection.class)
                .using(toJdbcEntity(item))
                .fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<BotDetection> update(BotDetection item) {
        LOGGER.debug("update bot detection with id {}", item.getId());
        Mono<Integer> action = dbClient.update()
                .table(JdbcBotDetection.class)
                .using(toJdbcEntity(item))
                .fetch().rowsUpdated();
        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(dbClient.delete()
                .from(JdbcBotDetection.class)
                .matching(from(where(ID_FIELD).is(id)))
                .fetch().rowsUpdated());
    }
}
