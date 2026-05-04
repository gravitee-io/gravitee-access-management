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
package io.gravitee.am.repository.jdbc.oauth2.aauth;

import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.oauth2.aauth.model.JdbcAAuthBootstrapBinding;
import io.gravitee.am.repository.oidc.api.AAuthBootstrapBindingRepository;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapBinding;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * JDBC repository for AAUTH bootstrap bindings.
 * Follows the same pattern as {@link JdbcAAuthPendingRequestRepository}.
 *
 * @author GraviteeSource Team
 */
@Repository
public class JdbcAAuthBootstrapBindingRepository extends AbstractJdbcRepository implements AAuthBootstrapBindingRepository {

    protected AAuthBootstrapBinding toEntity(JdbcAAuthBootstrapBinding entity) {
        return mapper.map(entity, AAuthBootstrapBinding.class);
    }

    protected JdbcAAuthBootstrapBinding toJdbcEntity(AAuthBootstrapBinding entity) {
        return mapper.map(entity, JdbcAAuthBootstrapBinding.class);
    }

    @Override
    public Maybe<AAuthBootstrapBinding> findById(String id) {
        LOGGER.debug("findById({})", id);
        return monoToMaybe(getTemplate().select(Query.query(where("id").is(id)), JdbcAAuthBootstrapBinding.class).singleOrEmpty())
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AAuthBootstrapBinding> findByDomainAndUserId(String domain, String userId) {
        LOGGER.debug("findByDomainAndUserId({}, {})", domain, userId);
        return fluxToFlowable(getTemplate().select(Query.query(where("domain").is(domain).and("user_id").is(userId)), JdbcAAuthBootstrapBinding.class))
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AAuthBootstrapBinding> findByDomainAndAgentServerUrlAndUserId(String domain, String agentServerUrl, String userId) {
        LOGGER.debug("findByDomainAndAgentServerUrlAndUserId({}, {}, {})", domain, agentServerUrl, userId);
        return monoToMaybe(getTemplate().select(Query.query(
                        where("domain").is(domain)
                                .and("agent_server_url").is(agentServerUrl)
                                .and("user_id").is(userId)), JdbcAAuthBootstrapBinding.class).singleOrEmpty())
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthBootstrapBinding> create(AAuthBootstrapBinding binding) {
        binding.setId(binding.getId() == null ? SecureRandomString.generate() : binding.getId());
        LOGGER.debug("Create AAuthBootstrapBinding with id {}", binding.getId());
        return monoToSingle(getTemplate().insert(toJdbcEntity(binding))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthBootstrapBinding> update(AAuthBootstrapBinding binding) {
        LOGGER.debug("Update AAuthBootstrapBinding with id {}", binding.getId());
        return monoToSingle(getTemplate().update(toJdbcEntity(binding))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return monoToCompletable(getTemplate().delete(Query.query(where("id").is(id)), JdbcAAuthBootstrapBinding.class))
                .observeOn(Schedulers.computation());
    }
}
