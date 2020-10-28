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
import io.gravitee.am.model.Email;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcEmail;
import io.gravitee.am.repository.jdbc.management.api.model.mapper.LocalDateConverter;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringEmailRepository;
import io.gravitee.am.repository.management.api.EmailRepository;
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
import java.util.Optional;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcEmailRepository extends AbstractJdbcRepository implements EmailRepository {
    @Autowired
    private SpringEmailRepository emailRepository;

    protected final LocalDateConverter dateConverter = new LocalDateConverter();

    protected Email toEntity(JdbcEmail entity) {
        return mapper.map(entity, Email.class);
    }

    protected JdbcEmail toJdbcEntity(Email entity) {
        return mapper.map(entity, JdbcEmail.class);
    }

    @Override
    public Single<List<Email>> findAll() {
        LOGGER.debug("findAll()");
        return emailRepository.findAll()
                .map(this::toEntity)
                .toList()
                .doOnError(error -> LOGGER.error("unable to list all Emails", error));
    }

    @Override
    public Single<List<Email>> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findAll({},{})", referenceType, referenceId);
        return emailRepository.findAllByReference(referenceId, referenceType.name())
                .map(this::toEntity)
                .toList()
                .doOnError(error -> LOGGER.error("unable to list all Emails with refId={} and refType={}",
                        referenceId, referenceType, error));
    }

    @Override
    public Single<List<Email>> findByDomain(String domain) {
        LOGGER.debug("findByDomain({})", domain);
        return findAll(ReferenceType.DOMAIN, domain);
    }

    @Override
    public Single<List<Email>> findByClient(ReferenceType referenceType, String referenceId, String client) {
        LOGGER.debug("findByClient({}, {}, {})", referenceType, referenceId, client);
        return emailRepository.findAllByReferenceAndClient(referenceId, referenceType.name(), client)
                .map(this::toEntity)
                .toList()
                .doOnError(error -> LOGGER.error("unable to list all Emails with refId={}, refType={}, client={}",
                        referenceId, referenceType, client, error));
    }

    @Override
    public Maybe<Email> findByTemplate(ReferenceType referenceType, String referenceId, String template) {
        LOGGER.debug("findByTemplate({}, {}, {})", referenceType, referenceId, template);
        return emailRepository.findByTemplate(referenceId, referenceType.name(), template)
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("unable to get email with refId={}, refType={}, template={}",
                        referenceId, referenceType, template, error));
    }

    @Override
    public Maybe<Email> findByDomainAndTemplate(String domain, String template) {
        LOGGER.debug("findByDomainAndTemplate({}, {})", domain, template);
        return findByTemplate(ReferenceType.DOMAIN, domain, template);
    }

    @Override
    public Maybe<Email> findByClientAndTemplate(ReferenceType referenceType, String referenceId, String client, String template) {
        LOGGER.debug("findByClientAndTemplate({}, {}, {}, {})", referenceType, referenceId, client, template);
        return emailRepository.findByClientAndTemplate(referenceId, referenceType.name(), client, template)
                .map(this::toEntity)
                .doOnError(error -> LOGGER.error("unable to get email with refId={}, refType={}, client={}, template={}",
                        referenceId, referenceType, client, template, error));
    }

    @Override
    public Maybe<Email> findByDomainAndClientAndTemplate(String domain, String client, String template) {
        LOGGER.debug("findByClientAndTemplate({}, {}, {})", domain, client, template);
        return findByClientAndTemplate(ReferenceType.DOMAIN, domain, client, template);
    }

    @Override
    public Maybe<Email> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("findById({}, {}, {})", referenceType, referenceId, id);
        return emailRepository.findById(referenceId, referenceType.name(), id).map(this::toEntity)
                .doOnError(error -> LOGGER.error("unable to retrieve email with refId={}, refType={}, id={}",
                        referenceId, referenceType, id, error));
    }

    @Override
    public Maybe<Email> findById(String id) {
        LOGGER.debug("findById({})", id);
        return emailRepository.findById(id).map(this::toEntity)
                .doOnError(error -> LOGGER.error("unable to retrieve email with id={}", id, error));
    }

    @Override
    public Single<Email> create(Email item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create email with id {}", item.getId());

        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = dbClient.insert().into("emails");
        // doesn't use the class introspection to detect the fields due to keyword column name
        insertSpec = addQuotedField(insertSpec,"id", item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec,"enabled", item.isEnabled(), Boolean.class);
        insertSpec = addQuotedField(insertSpec,"client", item.getClient(), String.class);
        insertSpec = addQuotedField(insertSpec,"content", item.getContent(), String.class);
        insertSpec = addQuotedField(insertSpec,"expires_after", item.getExpiresAfter(), int.class);
        insertSpec = addQuotedField(insertSpec,"from", item.getFrom(), String.class);
        insertSpec = addQuotedField(insertSpec,"from_name", item.getFromName(), String.class);
        insertSpec = addQuotedField(insertSpec,"reference_id", item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec,"reference_type", item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec,"subject", item.getSubject(), String.class);
        insertSpec = addQuotedField(insertSpec,"template", item.getTemplate(), String.class);
        insertSpec = addQuotedField(insertSpec,"created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec,"updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Integer> action = insertSpec.fetch().rowsUpdated();
        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to create email with id {}", item.getId(), error));
    }

    @Override
    public Single<Email> update(Email item) {
        LOGGER.debug("update email with id {}", item.getId());

        final DatabaseClient.GenericUpdateSpec updateSpec = dbClient.update().table("emails");

        // doesn't use the class introspection to detect the fields due to keyword column name
        Map<SqlIdentifier, Object> updateFields = new HashMap<>();
        updateFields = addQuotedField(updateFields,"id", item.getId(), String.class);
        updateFields = addQuotedField(updateFields,"enabled", item.isEnabled(), Boolean.class);
        updateFields = addQuotedField(updateFields,"client", item.getClient(), String.class);
        updateFields = addQuotedField(updateFields,"content", item.getContent(), String.class);
        updateFields = addQuotedField(updateFields,"expires_after", item.getExpiresAfter(), int.class);
        updateFields = addQuotedField(updateFields,"from", item.getFrom(), String.class);
        updateFields = addQuotedField(updateFields,"from_name", item.getFromName(), String.class);
        updateFields = addQuotedField(updateFields,"reference_id", item.getReferenceId(), String.class);
        updateFields = addQuotedField(updateFields,"reference_type", item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        updateFields = addQuotedField(updateFields,"subject", item.getSubject(), String.class);
        updateFields = addQuotedField(updateFields,"template", item.getTemplate(), String.class);
        updateFields = addQuotedField(updateFields,"created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields,"updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Integer> action = updateSpec.using(Update.from(updateFields)).matching(from(where("id").is(item.getId()))).fetch().rowsUpdated();

        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle())
                .doOnError((error) -> LOGGER.error("unable to update email with id {}", item.getId(), error));

    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return emailRepository.deleteById(id)
                .doOnError(error -> LOGGER.error("unable to delete email with id={}", id, error));
    }
}
