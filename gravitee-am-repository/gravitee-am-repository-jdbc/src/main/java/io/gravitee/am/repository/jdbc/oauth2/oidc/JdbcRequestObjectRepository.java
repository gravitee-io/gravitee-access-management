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
package io.gravitee.am.repository.jdbc.oauth2.oidc;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.oauth2.oidc.model.JdbcRequestObject;
import io.gravitee.am.repository.jdbc.oauth2.oidc.spring.SpringRequestObjectRepository;
import io.gravitee.am.repository.oidc.api.RequestObjectRepository;
import io.gravitee.am.repository.oidc.model.RequestObject;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;
import static reactor.adapter.rxjava.RxJava2Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcRequestObjectRepository extends AbstractJdbcRepository implements RequestObjectRepository {

    @Autowired
    private SpringRequestObjectRepository requestObjectRepository;

    protected RequestObject toEntity(JdbcRequestObject entity) {
        return mapper.map(entity, RequestObject.class);
    }

    protected JdbcRequestObject toJdbcEntity(RequestObject entity) {
        return mapper.map(entity, JdbcRequestObject.class);
    }

    @Override
    public Maybe<RequestObject> findById(String id) {
        LOGGER.debug("findById({})", id);
        LocalDateTime now = LocalDateTime.now(UTC);
        return requestObjectRepository.findById(id)
                .filter(bean -> bean.getExpireAt() == null || bean.getExpireAt().isAfter(now))
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve RequestObject with id {}", id, error));
    }

    @Override
    public Single<RequestObject> create(RequestObject requestObject) {
        requestObject.setId(requestObject.getId() == null ? RandomString.generate() : requestObject.getId());
        LOGGER.debug("Create requestObject with id {}", requestObject.getId());

        Mono<Integer> action = dbClient.insert()
                .into(JdbcRequestObject.class)
                .using(toJdbcEntity(requestObject))
                .fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(requestObject.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("Unable to create requestObject with id {}", requestObject.getId(), error));
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return requestObjectRepository.deleteById(id)
                .doOnError(error -> LOGGER.error("Unable to delete RequestObject with id {}", id, error));
    }
}
