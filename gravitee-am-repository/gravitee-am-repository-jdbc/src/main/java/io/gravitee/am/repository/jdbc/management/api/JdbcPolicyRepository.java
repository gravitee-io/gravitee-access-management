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
import io.gravitee.am.model.Policy;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcPolicy;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringPolicyRepository;
import io.gravitee.am.repository.management.api.PolicyRepository;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcPolicyRepository extends AbstractJdbcRepository implements PolicyRepository {

    @Autowired
    protected SpringPolicyRepository policyRepository;

    protected Policy toEntity(JdbcPolicy entity) {
        return mapper.map(entity, Policy.class);
    }

    protected JdbcPolicy toJdbcEntity(Policy entity) {
        return mapper.map(entity, JdbcPolicy.class);
    }

    @Override
    public Single<List<Policy>> findAll() {
        LOGGER.debug("findAll()");
        return policyRepository.findAll()
                .map(this::toEntity)
                .toList()
                .doOnError(error -> LOGGER.error("Unable to retrieve all policies", error));
    }

    @Override
    public Single<List<Policy>> findByDomain(String domain) {
        LOGGER.debug("findByDomain({})", domain);
        return policyRepository.findByDomain(domain)
                .map(this::toEntity)
                .toList()
                .doOnError(error -> LOGGER.error("Unable to retrieve all policies for domain {}", domain, error));
    }

    @Override
    public Maybe<Policy> findById(String id) {
        LOGGER.debug("findById({})", id);
        return policyRepository.findById(id)
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("Unable to retrieve the policy with id {}", id, error));
    }

    @Override
    public Single<Policy> create(Policy item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create Policy with id {}", item.getId());

        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = dbClient.insert().into("policies");
        // doesn't use the class introspection to detect the fields due to keyword column name
        insertSpec = addQuotedField(insertSpec,"id", item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec,"type", item.getType(), String.class);
        insertSpec = addQuotedField(insertSpec,"name", item.getName(), String.class);
        insertSpec = addQuotedField(insertSpec,"order", item.getOrder(), Integer.class); // keyword
        insertSpec = addQuotedField(insertSpec,"domain", item.getDomain(), String.class);
        insertSpec = addQuotedField(insertSpec,"client", item.getClient(), String.class);
        insertSpec = addQuotedField(insertSpec,"enabled", item.isEnabled(), Boolean.class);
        insertSpec = addQuotedField(insertSpec,"configuration", item.getConfiguration(), String.class);
        insertSpec = addQuotedField(insertSpec,"extension_point", item.getExtensionPoint() == null ? null : item.getExtensionPoint().name(), String.class);
        insertSpec = addQuotedField(insertSpec,"created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec,"updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Integer> action = insertSpec.fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to create policy with id {}", item.getId(), error));
    }

    @Override
    public Single<Policy> update(Policy item) {
        LOGGER.debug("update policy with id '{}'", item.getId());

        final DatabaseClient.GenericUpdateSpec updateSpec = dbClient.update().table("policies");
        // doesn't use the class introspection to detect the fields due to keyword column name
        Map<SqlIdentifier, Object> updateFields = new HashMap<>();
        updateFields = addQuotedField(updateFields,"id", item.getId(), String.class);
        updateFields = addQuotedField(updateFields,"type", item.getType(), String.class);
        updateFields = addQuotedField(updateFields,"name", item.getName(), String.class);
        updateFields = addQuotedField(updateFields,"order", item.getOrder(), Integer.class); // keyword
        updateFields = addQuotedField(updateFields,"domain", item.getDomain(), String.class);
        updateFields = addQuotedField(updateFields,"client", item.getClient(), String.class);
        updateFields = addQuotedField(updateFields,"enabled", item.isEnabled(), Boolean.class);
        updateFields = addQuotedField(updateFields,"configuration", item.getConfiguration(), String.class);
        updateFields = addQuotedField(updateFields,"extension_point", item.getExtensionPoint() == null ? null : item.getExtensionPoint().name(), String.class);
        updateFields = addQuotedField(updateFields,"created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields,"updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        Mono<Integer> action = updateSpec.using(Update.from(updateFields)).matching(from(where("id").is(item.getId()))).fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to update AccessPolicy with id {}", item.getId(), error));
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return policyRepository.deleteById(id)
                .doOnError(error -> LOGGER.error("Unable to delete the policy with id {}", id, error));
    }
}
