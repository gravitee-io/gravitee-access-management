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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcEmailRepository extends AbstractJdbcRepository implements EmailRepository, InitializingBean {

    public static final String COL_ID = "id";
    public static final String COL_ENABLED = "enabled";
    public static final String COL_CLIENT = "client";
    public static final String COL_CONTENT = "content";
    public static final String COL_EXPIRES_AFTER = "expires_after";
    public static final String COL_FROM = "from";
    public static final String COL_FROM_NAME = "from_name";
    public static final String COL_REFERENCE_ID = "reference_id";
    public static final String COL_REFERENCE_TYPE = "reference_type";
    public static final String COL_SUBJECT = "subject";
    public static final String COL_TEMPLATE = "template";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    private static final List<String> columns = List.of(
            COL_ID,
            COL_ENABLED,
            COL_CLIENT,
            COL_CONTENT,
            COL_EXPIRES_AFTER,
            COL_FROM,
            COL_FROM_NAME,
            COL_REFERENCE_ID,
            COL_REFERENCE_TYPE,
            COL_SUBJECT,
            COL_TEMPLATE,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );

    private String INSERT_STATEMENT;
    private String UPDATE_STATEMENT;

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
    public void afterPropertiesSet() throws Exception {
        this.INSERT_STATEMENT = createInsertStatement("emails", columns);
        this.UPDATE_STATEMENT = createUpdateStatement("emails", columns, List.of(COL_ID));
    }

    @Override
    public Flowable<Email> findAll() {
        LOGGER.debug("findAll()");
        return emailRepository.findAll()
                .map(this::toEntity);
    }

    @Override
    public Flowable<Email> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findAll({},{})", referenceType, referenceId);
        return emailRepository.findAllByReference(referenceId, referenceType.name())
                .map(this::toEntity);
    }

    @Override
    public Flowable<Email> findByClient(ReferenceType referenceType, String referenceId, String client) {
        LOGGER.debug("findByClient({}, {}, {})", referenceType, referenceId, client);
        return emailRepository.findAllByReferenceAndClient(referenceId, referenceType.name(), client)
                .map(this::toEntity);
    }

    @Override
    public Maybe<Email> findByTemplate(ReferenceType referenceType, String referenceId, String template) {
        LOGGER.debug("findByTemplate({}, {}, {})", referenceType, referenceId, template);
        return emailRepository.findByTemplate(referenceId, referenceType.name(), template)
                .map(this::toEntity);
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
                .map(this::toEntity);
    }

    @Override
    public Maybe<Email> findByDomainAndClientAndTemplate(String domain, String client, String template) {
        LOGGER.debug("findByClientAndTemplate({}, {}, {})", domain, client, template);
        return findByClientAndTemplate(ReferenceType.DOMAIN, domain, client, template);
    }

    @Override
    public Maybe<Email> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("findById({}, {}, {})", referenceType, referenceId, id);
        return emailRepository.findById(referenceId, referenceType.name(), id).map(this::toEntity);
    }

    @Override
    public Maybe<Email> findById(String id) {
        LOGGER.debug("findById({})", id);
        return emailRepository.findById(id).map(this::toEntity);
    }

    @Override
    public Single<Email> create(Email item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("create email with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec insertSpec = template.getDatabaseClient().sql(INSERT_STATEMENT);

        insertSpec = addQuotedField(insertSpec, COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_ENABLED, item.isEnabled(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, COL_CLIENT, item.getClient(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CONTENT, item.getContent(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_EXPIRES_AFTER, item.getExpiresAfter(), int.class);
        insertSpec = addQuotedField(insertSpec, COL_FROM, item.getFrom(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_FROM_NAME, item.getFromName(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_SUBJECT, item.getSubject(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_TEMPLATE, item.getTemplate(), String.class);
        insertSpec = addQuotedField(insertSpec, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = insertSpec.fetch().rowsUpdated();
        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<Email> update(Email item) {
        LOGGER.debug("update email with id {}", item.getId());

        DatabaseClient.GenericExecuteSpec update = template.getDatabaseClient().sql(UPDATE_STATEMENT);

        update = addQuotedField(update, COL_ID, item.getId(), String.class);
        update = addQuotedField(update, COL_ENABLED, item.isEnabled(), Boolean.class);
        update = addQuotedField(update, COL_CLIENT, item.getClient(), String.class);
        update = addQuotedField(update, COL_CONTENT, item.getContent(), String.class);
        update = addQuotedField(update, COL_EXPIRES_AFTER, item.getExpiresAfter(), int.class);
        update = addQuotedField(update, COL_FROM, item.getFrom(), String.class);
        update = addQuotedField(update, COL_FROM_NAME, item.getFromName(), String.class);
        update = addQuotedField(update, COL_REFERENCE_ID, item.getReferenceId(), String.class);
        update = addQuotedField(update, COL_REFERENCE_TYPE, item.getReferenceType() == null ? null : item.getReferenceType().name(), String.class);
        update = addQuotedField(update, COL_SUBJECT, item.getSubject(), String.class);
        update = addQuotedField(update, COL_TEMPLATE, item.getTemplate(), String.class);
        update = addQuotedField(update, COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);

        Mono<Long> action = update.fetch().rowsUpdated();
        return monoToSingle(action).flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        return emailRepository.deleteById(id);
    }
}
