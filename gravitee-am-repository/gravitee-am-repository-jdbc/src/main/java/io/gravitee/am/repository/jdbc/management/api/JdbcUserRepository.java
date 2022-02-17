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

import io.gravitee.am.common.analytics.Field;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.repository.jdbc.common.dialect.ScimUserSearch;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcUser;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcUser.AbstractRole;
import io.gravitee.am.repository.jdbc.management.api.spring.user.*;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.util.StreamUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Stream.concat;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcUserRepository extends AbstractJdbcRepository implements UserRepository, InitializingBean {
    private static final String ATTRIBUTE_USER_FIELD_EMAIL = "email";
    private static final String ATTRIBUTE_USER_FIELD_PHOTO = "photo";
    private static final String ATTRIBUTE_USER_FIELD_IM = "im";
    private static final String ATTRIBUTE_USER_FIELD_PHONE = "phoneNumber";

    private static final String FK_USER_ID = "user_id";

    private static final String ATTR_COL_TYPE = "type";
    private static final String ATTR_COL_PRIMARY = "primary";
    private static final String ATTR_COL_VALUE = "value";
    private static final String ATTR_COL_USER_FIELD = "user_field";

    private static final String ADDR_COL_TYPE = "type";
    private static final String ADDR_COL_FORMATTED = "formatted";
    private static final String ADDR_COL_STREET_ADDRESS = "street_address";
    private static final String ADDR_COL_LOCALITY = "locality";
    private static final String ADDR_COL_REGION = "region";
    private static final String ADDR_COL_POSTAL_CODE = "postal_code";
    private static final String ADDR_COL_COUNTRY = "country";
    private static final String ADDR_COL_PRIMARY = "primary";

    private static final String USER_COL_ID = "id";
    private static final String USER_COL_EMAIL = "email";
    private static final String USER_COL_EXTERNAL_ID = "external_id";
    private static final String USER_COL_USERNAME = "username";
    private static final String USER_COL_DISPLAY_NAME = "display_name";
    private static final String USER_COL_NICK_NAME = "nick_name";
    private static final String USER_COL_FIRST_NAME = "first_name";
    private static final String USER_COL_LAST_NAME = "last_name";
    private static final String USER_COL_TITLE = "title";
    private static final String USER_COL_TYPE = "type";
    private static final String USER_COL_PREFERRED_LANGUAGE = "preferred_language";
    private static final String USER_COL_ACCOUNT_NON_EXPIRED = "account_non_expired";
    private static final String USER_COL_ACCOUNT_LOCKED_AT = "account_locked_at";
    private static final String USER_COL_ACCOUNT_LOCKED_UNTIL = "account_locked_until";
    private static final String USER_COL_ACCOUNT_NON_LOCKED = "account_non_locked";
    private static final String USER_COL_CREDENTIALS_NON_EXPIRED = "credentials_non_expired";
    private static final String USER_COL_ENABLED = "enabled";
    private static final String USER_COL_INTERNAL = "internal";
    private static final String USER_COL_PRE_REGISTRATION = "pre_registration";
    private static final String USER_COL_REGISTRATION_COMPLETED = "registration_completed";
    private static final String USER_COL_NEWSLETTER = "newsletter";
    private static final String USER_COL_REGISTRATION_USER_URI = "registration_user_uri";
    private static final String USER_COL_REGISTRATION_ACCESS_TOKEN = "registration_access_token";
    private static final String USER_COL_REFERENCE_TYPE = "reference_type";
    private static final String USER_COL_REFERENCE_ID = "reference_id";
    private static final String USER_COL_SOURCE = "source";
    private static final String USER_COL_CLIENT = "client";
    private static final String USER_COL_LOGINS_COUNT = "logins_count";
    private static final String USER_COL_MFA_ENROLLMENT_SKIPPED_AT = "mfa_enrollment_skipped_at";
    private static final String USER_COL_LOGGED_AT = "logged_at";
    private static final String USER_COL_CREATED_AT = "created_at";
    private static final String USER_COL_UPDATED_AT = "updated_at";
    private static final String USER_COL_X_509_CERTIFICATES = "x509_certificates";
    private static final String USER_COL_FACTORS = "factors";
    private static final String USER_COL_ADDITIONAL_INFORMATION = "additional_information";

    public static final String USER_COL_LAST_PASSWORD_RESET = "last_password_reset";
    public static final String USER_COL_LAST_LOGOUT_AT = "last_logout_at";
    private static final List<String> USER_COLUMNS = List.of(
            USER_COL_ID,
            USER_COL_EXTERNAL_ID,
            USER_COL_USERNAME,
            USER_COL_EMAIL,
            USER_COL_DISPLAY_NAME,
            USER_COL_NICK_NAME,
            USER_COL_FIRST_NAME,
            USER_COL_LAST_NAME,
            USER_COL_TITLE,
            USER_COL_TYPE,
            USER_COL_PREFERRED_LANGUAGE,
            USER_COL_ACCOUNT_NON_EXPIRED,
            USER_COL_ACCOUNT_LOCKED_AT,
            USER_COL_ACCOUNT_LOCKED_UNTIL,
            USER_COL_ACCOUNT_NON_LOCKED,
            USER_COL_CREDENTIALS_NON_EXPIRED,
            USER_COL_ENABLED,
            USER_COL_INTERNAL,
            USER_COL_PRE_REGISTRATION,
            USER_COL_REGISTRATION_COMPLETED,
            USER_COL_NEWSLETTER,
            USER_COL_REGISTRATION_USER_URI,
            USER_COL_REGISTRATION_ACCESS_TOKEN,
            USER_COL_REFERENCE_TYPE,
            USER_COL_REFERENCE_ID,
            USER_COL_SOURCE,
            USER_COL_CLIENT,
            USER_COL_LOGINS_COUNT,
            USER_COL_LOGGED_AT,
            USER_COL_LAST_PASSWORD_RESET,
            USER_COL_LAST_LOGOUT_AT,
            USER_COL_MFA_ENROLLMENT_SKIPPED_AT,
            USER_COL_CREATED_AT,
            USER_COL_UPDATED_AT,
            USER_COL_X_509_CERTIFICATES,
            USER_COL_FACTORS,
            USER_COL_ADDITIONAL_INFORMATION
    );


    private static final List<String> ADDRESS_COLUMNS = List.of(
            FK_USER_ID,
            ADDR_COL_TYPE,
            ADDR_COL_FORMATTED,
            ADDR_COL_STREET_ADDRESS,
            ADDR_COL_LOCALITY,
            ADDR_COL_REGION,
            ADDR_COL_POSTAL_CODE,
            ADDR_COL_COUNTRY,
            ADDR_COL_PRIMARY
    );

    private static final List<String> ATTRIBUTES_COLUMNS = List.of(
            FK_USER_ID,
            ATTR_COL_USER_FIELD,
            ATTR_COL_VALUE,
            ATTR_COL_TYPE,
            ATTR_COL_PRIMARY
    );


    private static short CONCURRENT_FLATMAP = 1;

    private String UPDATE_USER_STATEMENT;
    private String INSERT_USER_STATEMENT;
    private String INSERT_ADDRESS_STATEMENT;
    private String INSERT_ATTRIBUTES_STATEMENT;

    @Autowired
    protected SpringUserRepository userRepository;

    @Autowired
    protected SpringUserRoleRepository roleRepository;

    @Autowired
    protected SpringDynamicUserRoleRepository dynamicRoleRepository;

    @Autowired
    protected SpringUserAddressesRepository addressesRepository;

    @Autowired
    protected SpringUserAttributesRepository attributesRepository;

    @Autowired
    protected SpringUserEntitlementRepository entitlementRepository;

    protected User toEntity(JdbcUser entity) {
        return mapper.map(entity, User.class);
    }

    protected JdbcUser toJdbcEntity(User entity) {
        return mapper.map(entity, JdbcUser.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.INSERT_USER_STATEMENT = createInsertStatement("users", USER_COLUMNS);
        this.UPDATE_USER_STATEMENT = createUpdateStatement("users", USER_COLUMNS, List.of(USER_COL_ID));
        this.INSERT_ADDRESS_STATEMENT = createInsertStatement("user_addresses", ADDRESS_COLUMNS);
        this.INSERT_ATTRIBUTES_STATEMENT = createInsertStatement("user_attributes", ATTRIBUTES_COLUMNS);
    }

    @Override
    public Flowable<User> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findByReference({})", referenceId);
        return userRepository.findByReference(referenceType.name(), referenceId)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toFlowable());
    }

    @Override
    public Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        LOGGER.debug("findAll({}, {}, {}, {})", referenceType, referenceId, page, size);
        return fluxToFlowable(template.select(JdbcUser.class)
                .matching(Query.query(where(USER_COL_REFERENCE_ID).is(referenceId)
                                .and(where(USER_COL_REFERENCE_TYPE).is(referenceType.name())))
                        .sort(Sort.by(USER_COL_ID).ascending())
                        .with(PageRequest.of(page, size))
                ).all())
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toFlowable(), CONCURRENT_FLATMAP)
                .toList()
                .flatMap(content -> userRepository.countByReference(referenceType.name(), referenceId)
                        .map((count) -> new Page<>(content, page, count)));
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size) {
        LOGGER.debug("search({}, {}, {}, {}, {})", referenceType, referenceId, query, page, size);

        boolean wildcardSearch = query.contains("*");
        String wildcardValue = query.replaceAll("\\*+", "%");

        String search = this.databaseDialectHelper.buildSearchUserQuery(wildcardSearch, page, size);
        String count = this.databaseDialectHelper.buildCountUserQuery(wildcardSearch);

        return fluxToFlowable(template.getDatabaseClient().sql(search)
                .bind(ATTR_COL_VALUE, wildcardSearch ? wildcardValue : query)
                .bind("refId", referenceId)
                .bind("refType", referenceType.name())
                .map(row -> rowMapper.read(JdbcUser.class, row)).all())
                .map(this::toEntity)
                .flatMap(app -> completeUser(app).toFlowable(), CONCURRENT_FLATMAP) // single thread to keep order
                .toList()
                .flatMap(data -> monoToSingle(template.getDatabaseClient().sql(count)
                        .bind(ATTR_COL_VALUE, wildcardSearch ? wildcardValue : query)
                        .bind("refId", referenceId)
                        .bind("refType", referenceType.name())
                        .map(row -> row.get(0, Long.class))
                        .first())
                        .map(total -> new Page<>(data, page, total)));
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria criteria, int page, int size) {
        LOGGER.debug("search({}, {}, {}, {}, {})", referenceType, referenceId, criteria, page, size);

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(" FROM users WHERE reference_id = :refId AND reference_type = :refType AND ");
        ScimUserSearch search = this.databaseDialectHelper.prepareScimSearchUserQuery(queryBuilder, criteria, page, size);

        // execute query
        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec executeSelect = template.getDatabaseClient().sql(search.getSelectQuery());
        executeSelect = executeSelect.bind("refType", referenceType.name()).bind("refId", referenceId);
        for (Map.Entry<String, Object> entry : search.getBinding().entrySet()) {
            executeSelect = executeSelect.bind(entry.getKey(), entry.getValue());
        }
        Flux<JdbcUser> userFlux = executeSelect.map(row -> rowMapper.read(JdbcUser.class, row)).all();

        // execute count to provide total in the Page
        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec executeCount = template.getDatabaseClient().sql(search.getCountQuery());
        executeCount = executeCount.bind("refType", referenceType.name()).bind("refId", referenceId);
        for (Map.Entry<String, Object> entry : search.getBinding().entrySet()) {
            executeCount = executeCount.bind(entry.getKey(), entry.getValue());
        }
        Mono<Long> userCount = executeCount.map(row -> row.get(0, Long.class)).first();

        return fluxToFlowable(userFlux)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toFlowable())
                .toList()
                .flatMap(list -> monoToSingle(userCount).map(total -> new Page<User>(list, page, total)));
    }

    @Override
    public Flowable<User> findByDomainAndEmail(String domain, String email, boolean strict) {
        return fluxToFlowable(template.getDatabaseClient().sql(databaseDialectHelper.buildFindUserByReferenceAndEmail(DOMAIN, domain, email, strict))
                .bind("refId", domain)
                .bind("refType", DOMAIN.name())
                .bind("email", email)
                .map(row -> rowMapper.read(JdbcUser.class, row))
                .all())
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toFlowable());
    }

    @Override
    public Maybe<User> findByUsernameAndDomain(String domain, String username) {
        LOGGER.debug("findByUsernameAndDomain({},{},{})", domain, username);
        return userRepository.findByUsername(ReferenceType.DOMAIN.name(), domain, username)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe());
    }

    @Override
    public Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source) {
        LOGGER.debug("findByUsernameAndSource({},{},{},{})", referenceType, referenceId, username, source);
        return userRepository.findByUsernameAndSource(referenceType.name(), referenceId, username, source)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe());
    }

    @Override
    public Maybe<User> findByExternalIdAndSource(ReferenceType referenceType, String referenceId, String externalId, String source) {
        LOGGER.debug("findByExternalIdAndSource({},{},{},{})", referenceType, referenceId, externalId, source);
        return userRepository.findByExternalIdAndSource(referenceType.name(), referenceId, externalId, source)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe());
    }

    @Override
    public Flowable<User> findByIdIn(List<String> ids) {
        LOGGER.debug("findByIdIn({})", ids);
        if (ids == null || ids.isEmpty()) {
            return Flowable.empty();
        }
        return userRepository.findByIdIn(ids)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toFlowable(), CONCURRENT_FLATMAP);
    }

    @Override
    public Maybe<User> findById(ReferenceType referenceType, String referenceId, String userId) {
        LOGGER.debug("findById({},{},{})", referenceType, referenceId, userId);
        return userRepository.findById(referenceType.name(), referenceId, userId)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe());
    }

    @Override
    public Single<Long> countByReference(ReferenceType refType, String domain) {
        return userRepository.countByReference(refType.name(), domain);
    }

    @Override
    public Single<Long> countByApplication(String domain, String application) {
        return userRepository.countByClient(ReferenceType.DOMAIN.name(), domain, application);
    }

    @Override
    public Single<Map<Object, Object>> statistics(AnalyticsQuery query) {
        switch (query.getField()) {
            case Field.USER_STATUS:
                return usersStatusRepartition(query);
            case Field.USER_REGISTRATION:
                return registrationsStatusRepartition(query);
        }

        return Single.just(Collections.emptyMap());
    }

    private Single<Map<Object, Object>> usersStatusRepartition(AnalyticsQuery query) {
        boolean filteringByApplication = query.getApplication() != null && !query.getApplication().isEmpty();

        Single<Long> total = filteringByApplication
                ? userRepository.countByClient(DOMAIN.name(), query.getDomain(), query.getApplication())
                : userRepository.countByReference(DOMAIN.name(), query.getDomain());
        Single<Long> disabled = filteringByApplication
                ? userRepository.countDisabledUserByClient(DOMAIN.name(), query.getDomain(), query.getApplication(), false)
                : userRepository.countDisabledUser(DOMAIN.name(), query.getDomain(), false);
        Single<Long> locked = filteringByApplication
                ? userRepository.countLockedUserByClient(DOMAIN.name(), query.getDomain(), query.getApplication(), false, LocalDateTime.now(UTC))
                : userRepository.countLockedUser(DOMAIN.name(), query.getDomain(), false, LocalDateTime.now(UTC));
        Single<Long> inactive = filteringByApplication
                ? userRepository.countInactiveUserByClient(DOMAIN.name(), query.getDomain(), query.getApplication(), LocalDateTime.now(UTC).minus(90, ChronoUnit.DAYS))
                : userRepository.countInactiveUser(DOMAIN.name(), query.getDomain(), LocalDateTime.now(UTC).minus(90, ChronoUnit.DAYS));

        return Single.just(new HashMap<>())
                .flatMap((stats) -> disabled.map(count -> {
                    LOGGER.debug("usersStatusRepartition(disabled) = {}", count);
                    stats.put("disabled", count);
                    return stats;
                })).flatMap((stats) -> locked.map(count -> {
                    LOGGER.debug("usersStatusRepartition(locked) = {}", count);
                    stats.put("locked", count);
                    return stats;
                })).flatMap((stats) -> inactive.map(count -> {
                    LOGGER.debug("usersStatusRepartition(inactive) = {}", count);
                    stats.put("inactive", count);
                    return stats;
                })).flatMap((stats) -> total.map(count -> {
                    long value = count - (stats.values().stream().mapToLong(l -> (Long) l).sum());
                    stats.put("active", value);
                    LOGGER.debug("usersStatusRepartition(active) = {}", value);
                    return stats;
                }));
    }

    private Single<Map<Object, Object>> registrationsStatusRepartition(AnalyticsQuery query) {
        LOGGER.debug("process statistic registrationsStatusRepartition({})", query);
        Single<Long> total = userRepository.countPreRegisteredUser(DOMAIN.name(), query.getDomain(), true);
        Single<Long> completed = userRepository.countRegistrationCompletedUser(DOMAIN.name(), query.getDomain(), true, true);
        return Single.just(new HashMap<>())
                .flatMap((stats) -> total.map(count -> {
                    LOGGER.debug("registrationsStatusRepartition(total) = {}", count);
                    stats.put("total", count);
                    return stats;
                })).flatMap((stats) -> completed.map(count -> {
                    LOGGER.debug("registrationsStatusRepartition(completed) = {}", count);
                    stats.put("completed", count);
                    return stats;
                }));
    }

    @Override
    public Maybe<User> findById(String id) {
        LOGGER.debug("findById({})", id);
        return userRepository.findById(id)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe());
    }

    @Override
    public Single<User> create(User item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create user with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        DatabaseClient.GenericExecuteSpec insertSpec = template.getDatabaseClient().sql(INSERT_USER_STATEMENT);

        insertSpec = addQuotedField(insertSpec, USER_COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_EXTERNAL_ID, item.getExternalId(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_USERNAME, item.getUsername(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_EMAIL, item.getEmail(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_DISPLAY_NAME, item.getDisplayName(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_NICK_NAME, item.getNickName(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_FIRST_NAME, item.getFirstName(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_LAST_NAME, item.getLastName(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_TITLE, item.getTitle(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_TYPE, item.getType(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_PREFERRED_LANGUAGE, item.getPreferredLanguage(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_ACCOUNT_NON_EXPIRED, item.isAccountNonExpired(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_ACCOUNT_LOCKED_AT, dateConverter.convertTo(item.getAccountLockedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_ACCOUNT_LOCKED_UNTIL, dateConverter.convertTo(item.getAccountLockedUntil(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_ACCOUNT_NON_LOCKED, item.isAccountNonLocked(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_CREDENTIALS_NON_EXPIRED, item.isCredentialsNonExpired(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_ENABLED, item.isEnabled(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_INTERNAL, item.isInternal(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_PRE_REGISTRATION, item.isPreRegistration(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_REGISTRATION_COMPLETED, item.isRegistrationCompleted(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_NEWSLETTER, item.isNewsletter(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_REGISTRATION_USER_URI, item.getRegistrationUserUri(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_REGISTRATION_ACCESS_TOKEN, item.getRegistrationAccessToken(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_REFERENCE_TYPE, item.getReferenceType() != null ? item.getReferenceType().name() : null, String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_REFERENCE_ID, item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_SOURCE, item.getSource(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_CLIENT, item.getClient(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_LOGINS_COUNT, item.getLoginsCount(), Integer.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_LOGGED_AT, dateConverter.convertTo(item.getLoggedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_LAST_PASSWORD_RESET, dateConverter.convertTo(item.getLastPasswordReset(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_LAST_LOGOUT_AT, dateConverter.convertTo(item.getLastLogoutAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_MFA_ENROLLMENT_SKIPPED_AT, dateConverter.convertTo(item.getMfaEnrollmentSkippedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, USER_COL_X_509_CERTIFICATES, item.getX509Certificates());
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, USER_COL_FACTORS, item.getFactors());
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, USER_COL_ADDITIONAL_INFORMATION, item.getAdditionalInformation());

        Mono<Integer> insertAction = insertSpec.fetch().rowsUpdated();

        insertAction = persistChildEntities(insertAction, item);

        return monoToSingle(insertAction.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<User> update(User item) {
        LOGGER.debug("Update User with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec update = template.getDatabaseClient().sql(UPDATE_USER_STATEMENT);

        update = addQuotedField(update, USER_COL_ID, item.getId(), String.class);
        update = addQuotedField(update, USER_COL_EXTERNAL_ID, item.getExternalId(), String.class);
        update = addQuotedField(update, USER_COL_USERNAME, item.getUsername(), String.class);
        update = addQuotedField(update, USER_COL_EMAIL, item.getEmail(), String.class);
        update = addQuotedField(update, USER_COL_DISPLAY_NAME, item.getDisplayName(), String.class);
        update = addQuotedField(update, USER_COL_NICK_NAME, item.getNickName(), String.class);
        update = addQuotedField(update, USER_COL_FIRST_NAME, item.getFirstName(), String.class);
        update = addQuotedField(update, USER_COL_LAST_NAME, item.getLastName(), String.class);
        update = addQuotedField(update, USER_COL_TITLE, item.getTitle(), String.class);
        update = addQuotedField(update, USER_COL_TYPE, item.getType(), String.class);
        update = addQuotedField(update, USER_COL_PREFERRED_LANGUAGE, item.getPreferredLanguage(), String.class);
        update = addQuotedField(update, USER_COL_ACCOUNT_NON_EXPIRED, item.isAccountNonExpired(), Boolean.class);
        update = addQuotedField(update, USER_COL_ACCOUNT_LOCKED_AT, dateConverter.convertTo(item.getAccountLockedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_ACCOUNT_LOCKED_UNTIL, dateConverter.convertTo(item.getAccountLockedUntil(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_ACCOUNT_NON_LOCKED, item.isAccountNonLocked(), Boolean.class);
        update = addQuotedField(update, USER_COL_CREDENTIALS_NON_EXPIRED, item.isCredentialsNonExpired(), Boolean.class);
        update = addQuotedField(update, USER_COL_ENABLED, item.isEnabled(), Boolean.class);
        update = addQuotedField(update, USER_COL_INTERNAL, item.isInternal(), Boolean.class);
        update = addQuotedField(update, USER_COL_PRE_REGISTRATION, item.isPreRegistration(), Boolean.class);
        update = addQuotedField(update, USER_COL_REGISTRATION_COMPLETED, item.isRegistrationCompleted(), Boolean.class);
        update = addQuotedField(update, USER_COL_NEWSLETTER, item.isNewsletter(), Boolean.class);
        update = addQuotedField(update, USER_COL_REGISTRATION_USER_URI, item.getRegistrationUserUri(), String.class);
        update = addQuotedField(update, USER_COL_REGISTRATION_ACCESS_TOKEN, item.getRegistrationAccessToken(), String.class);
        update = addQuotedField(update, USER_COL_REFERENCE_TYPE, item.getReferenceType() != null ? item.getReferenceType().name() : null, String.class);
        update = addQuotedField(update, USER_COL_REFERENCE_ID, item.getReferenceId(), String.class);
        update = addQuotedField(update, USER_COL_SOURCE, item.getSource(), String.class);
        update = addQuotedField(update, USER_COL_CLIENT, item.getClient(), String.class);
        update = addQuotedField(update, USER_COL_LOGINS_COUNT, item.getLoginsCount(), Integer.class);
        update = addQuotedField(update, USER_COL_LOGGED_AT, dateConverter.convertTo(item.getLoggedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_LAST_PASSWORD_RESET, dateConverter.convertTo(item.getLastPasswordReset(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_LAST_LOGOUT_AT, dateConverter.convertTo(item.getLastLogoutAt(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_MFA_ENROLLMENT_SKIPPED_AT, dateConverter.convertTo(item.getMfaEnrollmentSkippedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        update = databaseDialectHelper.addJsonField(update, USER_COL_X_509_CERTIFICATES, item.getX509Certificates());
        update = databaseDialectHelper.addJsonField(update, USER_COL_FACTORS, item.getFactors());
        update = databaseDialectHelper.addJsonField(update, USER_COL_ADDITIONAL_INFORMATION, item.getAdditionalInformation());

        Mono<Integer> action = update.fetch().rowsUpdated();

        action = deleteChildEntities(item.getId()).then(action);
        action = persistChildEntities(action, item);

        return monoToSingle(action.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> delete = template.delete(JdbcUser.class).matching(Query.query(where("id").is(id))).all();

        return monoToCompletable(delete.then(deleteChildEntities(id)).as(trx::transactional));
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("deleteByReference({}, {})", referenceType, referenceId);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> delete = template.getDatabaseClient().sql("DELETE FROM users WHERE reference_type = :refType AND reference_id = :refId").bind("refType", referenceType.name()).bind("refId", referenceId).fetch().rowsUpdated();
        return monoToCompletable(deleteChildEntitiesByRef(referenceType.name(), referenceId).then(delete).as(trx::transactional));
    }

    private Mono<Integer> deleteChildEntitiesByRef(String refType, String refId) {
        Mono<Integer> deleteRoles =  template.getDatabaseClient().sql("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind("refType", refType).bind("refId", refId).fetch().rowsUpdated();
        Mono<Integer> deleteAddresses = template.getDatabaseClient().sql("DELETE FROM user_addresses WHERE user_id IN (SELECT id FROM users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind("refType", refType).bind("refId", refId).fetch().rowsUpdated();
        Mono<Integer> deleteAttributes = template.getDatabaseClient().sql("DELETE FROM user_attributes WHERE user_id IN (SELECT id FROM users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind("refType", refType).bind("refId", refId).fetch().rowsUpdated();
        Mono<Integer> deleteEntitlements = template.getDatabaseClient().sql("DELETE FROM user_entitlements WHERE user_id IN (SELECT id FROM users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind("refType", refType).bind("refId", refId).fetch().rowsUpdated();
        return deleteRoles.then(deleteAddresses).then(deleteAttributes).then(deleteEntitlements);
    }

    private Mono<Integer> persistChildEntities(Mono<Integer> actionFlow, User item) {
        final List<Address> addresses = item.getAddresses();
        if (addresses != null && !addresses.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(addresses).concatMap(address -> {
                DatabaseClient.GenericExecuteSpec insert = template.getDatabaseClient().sql(INSERT_ADDRESS_STATEMENT).bind(FK_USER_ID, item.getId());
                insert = address.getType() != null ? insert.bind(ADDR_COL_TYPE, address.getType()) : insert.bindNull(ADDR_COL_TYPE, String.class);
                insert = address.getFormatted() != null ? insert.bind(ADDR_COL_FORMATTED, address.getFormatted()) : insert.bindNull(ADDR_COL_FORMATTED, String.class);
                insert = address.getStreetAddress() != null ? insert.bind(ADDR_COL_STREET_ADDRESS, address.getStreetAddress()) : insert.bindNull(ADDR_COL_STREET_ADDRESS, String.class);
                insert = address.getLocality() != null ? insert.bind(ADDR_COL_LOCALITY, address.getLocality()) : insert.bindNull(ADDR_COL_LOCALITY, String.class);
                insert = address.getRegion() != null ? insert.bind(ADDR_COL_REGION, address.getRegion()) : insert.bindNull(ADDR_COL_REGION, String.class);
                insert = address.getPostalCode() != null ? insert.bind(ADDR_COL_POSTAL_CODE, address.getPostalCode()) : insert.bindNull(ADDR_COL_POSTAL_CODE, String.class);
                insert = address.getCountry() != null ? insert.bind(ADDR_COL_COUNTRY, address.getCountry()) : insert.bindNull(ADDR_COL_COUNTRY, String.class);
                insert = address.isPrimary() != null ? insert.bind(ADDR_COL_PRIMARY, address.isPrimary()) : insert.bindNull(ADDR_COL_PRIMARY, Boolean.class);
                return insert.fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        actionFlow = addJdbcRoles(actionFlow, item, item.getRoles(), "user_roles");
        actionFlow = addJdbcRoles(actionFlow, item, item.getDynamicRoles(), "dynamic_user_roles");

        final List<String> entitlements = item.getEntitlements();
        if (entitlements != null && !entitlements.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(entitlements).concatMap(entitlement ->
                            template.getDatabaseClient().sql("INSERT INTO user_entitlements(user_id, entitlement) VALUES(:user, :entitlement)")
                                    .bind("user", item.getId())
                                    .bind("entitlement", entitlement)
                                    .fetch().rowsUpdated())
                    .reduce(Integer::sum));
        }

        Optional<Mono<Integer>> attributes = concat(concat(concat(convertAttributes(item, item.getEmails(), ATTRIBUTE_USER_FIELD_EMAIL),
                                convertAttributes(item, item.getPhoneNumbers(), ATTRIBUTE_USER_FIELD_PHONE)),
                        convertAttributes(item, item.getIms(), ATTRIBUTE_USER_FIELD_IM)),
                convertAttributes(item, item.getPhotos(), ATTRIBUTE_USER_FIELD_PHOTO))
                .map(jdbcAttr -> {
                    DatabaseClient.GenericExecuteSpec insert = template.getDatabaseClient().sql(INSERT_ATTRIBUTES_STATEMENT)
                            .bind(FK_USER_ID, item.getId())
                            .bind(ATTR_COL_USER_FIELD, jdbcAttr.getUserField());
                    insert = jdbcAttr.getValue() != null ? insert.bind(ATTR_COL_VALUE, jdbcAttr.getValue()) : insert.bindNull(ATTR_COL_VALUE, String.class);
                    insert = jdbcAttr.getType() != null ? insert.bind(ATTR_COL_TYPE, jdbcAttr.getType()) : insert.bindNull(ATTR_COL_TYPE, String.class);
                    insert = jdbcAttr.getPrimary() != null ? insert.bind(ATTR_COL_PRIMARY, jdbcAttr.getPrimary()) : insert.bindNull(ATTR_COL_PRIMARY, Boolean.class);
                    return insert.fetch().rowsUpdated();
                })
                .reduce(Mono::then);
        if (attributes.isPresent()) {
            actionFlow = actionFlow.then(attributes.get());
        }

        return actionFlow;
    }

    private <T extends AbstractRole> Mono<Integer> addJdbcRoles(Mono<Integer> actionFlow,
                                                                User item, List<String> roles, String roleTable) {
        if (roles != null && !roles.isEmpty()) {
            return actionFlow.then(Flux.fromIterable(roles).concatMap(role -> {
                try {
                    return template.getDatabaseClient().sql("INSERT INTO " + roleTable + "(user_id, role) VALUES(:user, :role)")
                            .bind("user", item.getId())
                            .bind("role", role)
                            .fetch().rowsUpdated();
                } catch (Exception e) {
                    LOGGER.error("An unexpected error has occurred", e);
                    return Mono.just(0);
                }
            }).reduce(Integer::sum));
        }
        return actionFlow;
    }

    private Stream<JdbcUser.Attribute> convertAttributes(User item, List<Attribute> attributes, String field) {
        if (attributes != null && !attributes.isEmpty()) {
            return attributes.stream().map(attr -> {
                JdbcUser.Attribute jdbcAttr = mapper.map(attr, JdbcUser.Attribute.class);
                jdbcAttr.setUserId(item.getId());
                jdbcAttr.setUserField(field);
                return jdbcAttr;
            });
        }
        return Stream.empty();
    }

    private Mono<Integer> deleteChildEntities(String userId) {
        Mono<Integer> deleteRoles = template.delete(JdbcUser.Role.class).matching(Query.query(where("user_id").is(userId))).all();
        Mono<Integer> deleteDynamicRoles = template.delete(JdbcUser.DynamicRole.class).matching(Query.query(where("user_id").is(userId))).all();
        Mono<Integer> deleteAddresses = template.delete(JdbcUser.Address.class).matching(Query.query(where("user_id").is(userId))).all();
        Mono<Integer> deleteAttributes = template.delete(JdbcUser.Attribute.class).matching(Query.query(where("user_id").is(userId))).all();
        Mono<Integer> deleteEntitlements = template.delete(JdbcUser.Entitlements.class).matching(Query.query(where("user_id").is(userId))).all();
        return deleteRoles.then(deleteDynamicRoles).then(deleteAddresses).then(deleteAttributes).then(deleteEntitlements);
    }

    private Single<User> completeUser(User userToComplete) {
        return Single.just(userToComplete)
                .flatMap(user ->
                        roleRepository.findByUserId(user.getId()).map(JdbcUser.Role::getRole).toList().map(roles -> {
                            user.setRoles(roles);
                            return user;
                        }))
                .flatMap(user ->
                        dynamicRoleRepository.findByUserId(user.getId()).map(JdbcUser.DynamicRole::getRole).toList().map(roles -> {
                            user.setDynamicRoles(roles);
                            return user;
                        }))
                .flatMap(user ->
                        entitlementRepository.findByUserId(user.getId()).map(JdbcUser.Entitlements::getEntitlement).toList().map(entitlements -> {
                            user.setEntitlements(entitlements);
                            return user;
                        }))
                .flatMap(user ->
                        addressesRepository.findByUserId(user.getId())
                                .map(jdbcAddr -> mapper.map(jdbcAddr, Address.class))
                                .toList().map(addresses -> {
                                    user.setAddresses(addresses);
                                    return user;
                                }))
                .flatMap(user ->
                        attributesRepository.findByUserId(user.getId())
                                .toList()
                                .map(attributes -> {
                                    Map<String, List<Attribute>> map = attributes.stream().collect(StreamUtils.toMultiMap(JdbcUser.Attribute::getUserField, attr -> mapper.map(attr, Attribute.class)));
                                    if (map.containsKey(ATTRIBUTE_USER_FIELD_EMAIL)) {
                                        user.setEmails(map.get(ATTRIBUTE_USER_FIELD_EMAIL));
                                    }
                                    if (map.containsKey(ATTRIBUTE_USER_FIELD_PHONE)) {
                                        user.setPhoneNumbers(map.get(ATTRIBUTE_USER_FIELD_PHONE));
                                    }
                                    if (map.containsKey(ATTRIBUTE_USER_FIELD_PHOTO)) {
                                        user.setPhotos(map.get(ATTRIBUTE_USER_FIELD_PHOTO));
                                    }
                                    if (map.containsKey(ATTRIBUTE_USER_FIELD_IM)) {
                                        user.setIms(map.get(ATTRIBUTE_USER_FIELD_IM));
                                    }
                                    return user;
                                })
                );
    }

}
