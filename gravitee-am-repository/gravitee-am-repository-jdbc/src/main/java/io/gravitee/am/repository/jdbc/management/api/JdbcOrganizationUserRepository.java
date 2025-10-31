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
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.repository.common.UserIdFields;
import io.gravitee.am.repository.jdbc.common.dialect.ScimSearch;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcOrganizationUser;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringOrganizationUserAddressesRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringOrganizationUserAttributesRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringOrganizationUserDynamicRoleRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringOrganizationUserEntitlementRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringOrganizationUserRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringOrganizationUserRoleRepository;
import io.gravitee.am.repository.management.api.OrganizationUserRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.util.StreamUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static io.gravitee.am.repository.jdbc.common.dialect.DatabaseDialectHelper.ScimRepository.ORGANIZATION_USERS;
import static java.util.stream.Stream.concat;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcOrganizationUserRepository extends AbstractJdbcRepository implements OrganizationUserRepository, InitializingBean {
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
    private static final String USER_COL_PASSWORD = "password";
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
    private static final String USER_COL_LOGGED_AT = "logged_at";
    private static final String USER_COL_LAST_USERNAME_RESET = "last_username_reset";
    private static final String USER_COL_MFA_ENROLLMENT_SKIPPED_AT = "mfa_enrollment_skipped_at";
    private static final String USER_COL_CREATED_AT = "created_at";
    private static final String USER_COL_UPDATED_AT = "updated_at";
    private static final String USER_COL_X_509_CERTIFICATES = "x509_certificates";
    private static final String USER_COL_FACTORS = "factors";
    private static final String USER_COL_ADDITIONAL_INFORMATION = "additional_information";
    private static final String USER_COL_FORCE_RESET_PASSWORD = "force_reset_password";
    private static final String USER_COL_SERVICE_ACCOUNT = "service_account";


    private static final List<String> USER_COLUMNS = List.of(
            USER_COL_ID,
            USER_COL_EXTERNAL_ID,
            USER_COL_USERNAME,
            USER_COL_PASSWORD,
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
            USER_COL_LAST_USERNAME_RESET,
            USER_COL_MFA_ENROLLMENT_SKIPPED_AT,
            USER_COL_CREATED_AT,
            USER_COL_UPDATED_AT,
            USER_COL_X_509_CERTIFICATES,
            USER_COL_FACTORS,
            USER_COL_ADDITIONAL_INFORMATION,
            USER_COL_FORCE_RESET_PASSWORD,
            USER_COL_SERVICE_ACCOUNT
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
    public static final String REF_ID = "refId";
    public static final String REF_TYPE = "refType";
    private static final UserIdFields USER_ID_FIELDS = new UserIdFields(USER_COL_ID, USER_COL_SOURCE, USER_COL_EXTERNAL_ID);

    private String updateUserStatement;
    private String insertUserStatement;
    private String insertAddressStatement;
    private String insertAttributesStatement;

    @Autowired
    protected SpringOrganizationUserRepository userRepository;

    @Autowired
    protected SpringOrganizationUserRoleRepository roleRepository;

    @Autowired
    protected SpringOrganizationUserDynamicRoleRepository dynamicRoleRepository;

    @Autowired
    protected SpringOrganizationUserAddressesRepository addressesRepository;

    @Autowired
    protected SpringOrganizationUserAttributesRepository attributesRepository;

    @Autowired
    protected SpringOrganizationUserEntitlementRepository entitlementRepository;

    protected User toEntity(JdbcOrganizationUser entity) {
        return mapper.map(entity, User.class);
    }

    protected JdbcOrganizationUser toJdbcEntity(User entity) {
        return mapper.map(entity, JdbcOrganizationUser.class);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insertUserStatement = createInsertStatement("organization_users", USER_COLUMNS);
        this.updateUserStatement = createUpdateStatement("organization_users", USER_COLUMNS, List.of(USER_COL_ID));
        this.insertAddressStatement = createInsertStatement("organization_user_addresses", ADDRESS_COLUMNS);
        this.insertAttributesStatement = createInsertStatement("organization_user_attributes", ATTRIBUTES_COLUMNS);
    }

    @Override
    public Flowable<User> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findByReference({})", referenceId);
        return userRepository.findByReference(referenceType.name(), referenceId)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toFlowable())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        LOGGER.debug("findAll({}, {}, {}, {})", referenceType, referenceId, page, size);
        return fluxToFlowable(getTemplate().select(JdbcOrganizationUser.class)
                .matching(Query.query(where(USER_COL_REFERENCE_ID).is(referenceId)
                        .and(where(USER_COL_REFERENCE_TYPE).is(referenceType.name())))
                        .sort(Sort.by(USER_COL_USERNAME).ascending())
                        .with(PageRequest.of(page, size))
                ).all())
                .map(this::toEntity)
                .concatMap(user -> completeUser(user).toFlowable())
                .toList()
                .flatMap(content -> userRepository.countByReference(referenceType.name(), referenceId)
                        .map((count) -> new Page<>(content, page, count)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size) {
        LOGGER.debug("search({}, {}, {}, {}, {})", referenceType, referenceId, query, page, size);

        boolean wildcardSearch = query.contains("*");
        String wildcardValue = query.replaceAll("\\*+", "%");

        String search = this.databaseDialectHelper.buildSearchUserQuery(wildcardSearch, page, size, true);
        String count = this.databaseDialectHelper.buildCountUserQuery(wildcardSearch, true);

        return fluxToFlowable(getTemplate().getDatabaseClient().sql(search)
                .bind(ATTR_COL_VALUE, wildcardSearch ? wildcardValue : query)
                .bind(REF_ID, referenceId)
                .bind(REF_TYPE, referenceType.name())
                .map((row, rowMetadata) -> rowMapper.read(JdbcOrganizationUser.class, row))
                .all())
                .map(this::toEntity)
                .concatMap(app -> completeUser(app).toFlowable())
                .toList()
                .flatMap(data -> monoToSingle(getTemplate().getDatabaseClient().sql(count)
                        .bind(ATTR_COL_VALUE, wildcardSearch ? wildcardValue : query)
                        .bind(REF_ID, referenceId)
                        .bind(REF_TYPE, referenceType.name())
                        .map((row, rowMetadat) -> row.get(0, Long.class)).first())
                        .map(total -> new Page<>(data, page, total)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria criteria, int page, int size) {
        LOGGER.debug("search({}, {}, {}, {}, {})", referenceType, referenceId, criteria, page, size);

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(" FROM organization_users WHERE reference_id = :refId AND reference_type = :refType AND ");
        ScimSearch search = this.databaseDialectHelper.prepareScimSearchQuery(queryBuilder, criteria, USER_COL_USERNAME, page, size, ORGANIZATION_USERS);

        // execute query
        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec executeSelect = getTemplate().getDatabaseClient().sql(search.getSelectQuery()).bind(REF_TYPE, referenceType.name()).bind(REF_ID, referenceId);
        for (Map.Entry<String, Object> entry : search.getBinding().entrySet()) {
            executeSelect = executeSelect.bind(entry.getKey(), entry.getValue());
        }
        Flux<JdbcOrganizationUser> userFlux = executeSelect.map((row, rowMetadat) -> rowMapper.read(JdbcOrganizationUser.class, row)).all();

        // execute count to provide total in the Page
        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec executeCount = getTemplate().getDatabaseClient().sql(search.getCountQuery());
        executeCount = executeCount.bind(REF_TYPE, referenceType.name()).bind(REF_ID, referenceId);
        for (Map.Entry<String, Object> entry : search.getBinding().entrySet()) {
            executeCount = executeCount.bind(entry.getKey(), entry.getValue());
        }
        Mono<Long> userCount = executeCount.map((row, rowMetadat) -> row.get(0, Long.class)).first();

        return fluxToFlowable(userFlux)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toFlowable())
                .toList()
                .flatMap(list -> monoToSingle(userCount).map(total -> new Page<User>(list, page, total)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<User> search(ReferenceType referenceType, String referenceId, FilterCriteria criteria) {
        LOGGER.debug("search({}, {}, {})", referenceType, referenceId, criteria);

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(" FROM organization_users WHERE reference_id = :refId AND reference_type = :refType AND ");
        ScimSearch search = this.databaseDialectHelper.prepareScimSearchQuery(queryBuilder, criteria, USER_COL_USERNAME, -1, -1, ORGANIZATION_USERS);

        // execute query
        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec executeSelect = getTemplate().getDatabaseClient().sql(search.getSelectQuery()).bind(REF_TYPE, referenceType.name()).bind(REF_ID, referenceId);
        for (Map.Entry<String, Object> entry : search.getBinding().entrySet()) {
            executeSelect = executeSelect.bind(entry.getKey(), entry.getValue());
        }
        Flux<JdbcOrganizationUser> userFlux = executeSelect.map((row, rowMetadat) -> rowMapper.read(JdbcOrganizationUser.class, row)).all();

        return fluxToFlowable(userFlux)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toFlowable())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source) {
        LOGGER.debug("findByUsernameAndSource({},{},{},{})", referenceType, referenceId, username, source);
        return userRepository.findByUsernameAndSource(referenceType.name(), referenceId, username, source)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findByExternalIdAndSource(ReferenceType referenceType, String referenceId, String externalId, String source) {
        LOGGER.debug("findByExternalIdAndSource({},{},{},{})", referenceType, referenceId, externalId, source);
        return userRepository.findByExternalIdAndSource(referenceType.name(), referenceId, externalId, source)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<User> findByIdIn(List<String> ids) {
        LOGGER.debug("findByIdIn({})", ids);
        if (ids == null || ids.isEmpty()) {
            return Flowable.empty();
        }
        return userRepository.findByIdIn(ids)
                .map(this::toEntity)
                .concatMap(user -> completeUser(user).toFlowable())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findById(Reference reference, UserId userId) {
        Criteria criteria = userIdMatches(userId)
                .and(referenceMatches(reference));
        LOGGER.debug("findById({},{})", reference, userId);
        return findOne(Query.query(criteria), JdbcOrganizationUser.class)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findById(String id) {
        LOGGER.debug("findById({})", id);
        return userRepository.findById(id)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<User> create(User item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        LOGGER.debug("Create user with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec insertSpec = getTemplate().getDatabaseClient().sql(insertUserStatement);

        insertSpec = addQuotedField(insertSpec, USER_COL_ID, item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_EXTERNAL_ID, item.getExternalId(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_USERNAME, item.getUsername(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_PASSWORD, item.getPassword(), String.class);
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
        insertSpec = addQuotedField(insertSpec, USER_COL_LAST_USERNAME_RESET, dateConverter.convertTo(item.getLastUsernameReset(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_MFA_ENROLLMENT_SKIPPED_AT, dateConverter.convertTo(item.getMfaEnrollmentSkippedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, USER_COL_X_509_CERTIFICATES, item.getX509Certificates());
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, USER_COL_FACTORS, item.getFactors());
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, USER_COL_ADDITIONAL_INFORMATION, item.getAdditionalInformation());
        insertSpec = addQuotedField(insertSpec, USER_COL_FORCE_RESET_PASSWORD, item.getForceResetPassword(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_SERVICE_ACCOUNT, item.getServiceAccount(), Boolean.class);

        Mono<Long> insertAction = insertSpec.fetch().rowsUpdated();
        insertAction = persistChildEntities(insertAction, item, UpdateActions.updateAll());

        return monoToSingle(insertAction.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<User> update(User item) {
        return update(item, UpdateActions.updateAll());
    }

    @Override
    public Single<User> update(User item, UpdateActions updateActions) {
        LOGGER.debug("Update User with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);

        DatabaseClient.GenericExecuteSpec update = getTemplate().getDatabaseClient().sql(updateUserStatement);

        update = addQuotedField(update, USER_COL_ID, item.getId(), String.class);
        update = addQuotedField(update, USER_COL_EXTERNAL_ID, item.getExternalId(), String.class);
        update = addQuotedField(update, USER_COL_USERNAME, item.getUsername(), String.class);
        update = addQuotedField(update, USER_COL_PASSWORD, item.getPassword(), String.class);
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
        update = addQuotedField(update, USER_COL_LAST_USERNAME_RESET, dateConverter.convertTo(item.getLastUsernameReset(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_MFA_ENROLLMENT_SKIPPED_AT, dateConverter.convertTo(item.getMfaEnrollmentSkippedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        update = databaseDialectHelper.addJsonField(update, USER_COL_X_509_CERTIFICATES, item.getX509Certificates());
        update = databaseDialectHelper.addJsonField(update, USER_COL_FACTORS, item.getFactors());
        update = databaseDialectHelper.addJsonField(update, USER_COL_ADDITIONAL_INFORMATION, item.getAdditionalInformation());
        update = addQuotedField(update, USER_COL_FORCE_RESET_PASSWORD, item.getForceResetPassword(), Boolean.class);
        update = addQuotedField(update, USER_COL_SERVICE_ACCOUNT, item.getServiceAccount(), Boolean.class);

        Mono<Long> action = update.fetch().rowsUpdated();

        if (updateActions.updateRequire()) {
            action = deleteChildEntities(item.getId(), updateActions).then(action);
            action = persistChildEntities(action, item, updateActions);
        }

        return monoToSingle(action.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Long> delete = getTemplate().delete(JdbcOrganizationUser.class).matching(Query.query(where(USER_COL_ID).is(id))).all();

        return monoToCompletable(delete.then(deleteChildEntities(id, UpdateActions.updateAll())).as(trx::transactional))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("deleteByReference({}, {})", referenceType, referenceId);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Long> delete = getTemplate().getDatabaseClient().sql("DELETE FROM organization_users WHERE reference_type = :refType AND reference_id = :refId").bind(REF_TYPE, referenceType.name()).bind(REF_ID, referenceId).fetch().rowsUpdated();
        return monoToCompletable(deleteChildEntitiesByRef(referenceType.name(), referenceId).then(delete).as(trx::transactional))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Criteria userIdMatches(UserId userId) {
        if (userId.id() == null) {
            // where().is() doesn't accept nulls
            throw new IllegalArgumentException("Internal user id must not be null");
        }
        return Criteria.where(USER_COL_ID).is(userId.id());
    }

    private Mono<Long> deleteChildEntitiesByRef(String refType, String refId) {
        Mono<Long> deleteRoles = getTemplate().getDatabaseClient().sql("DELETE FROM organization_user_roles WHERE user_id IN (SELECT id FROM organization_users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind(REF_TYPE, refType).bind(REF_ID, refId).fetch().rowsUpdated();
        Mono<Long> deleteAddresses =getTemplate().getDatabaseClient().sql("DELETE FROM organization_user_addresses WHERE user_id IN (SELECT id FROM organization_users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind(REF_TYPE, refType).bind(REF_ID, refId).fetch().rowsUpdated();
        Mono<Long> deleteAttributes = getTemplate().getDatabaseClient().sql("DELETE FROM organization_user_attributes WHERE user_id IN (SELECT id FROM organization_users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind(REF_TYPE, refType).bind(REF_ID, refId).fetch().rowsUpdated();
        Mono<Long> deleteEntitlements = getTemplate().getDatabaseClient().sql("DELETE FROM organization_user_entitlements WHERE user_id IN (SELECT id FROM organization_users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind(REF_TYPE, refType).bind(REF_ID, refId).fetch().rowsUpdated();
        return deleteRoles.then(deleteAddresses).then(deleteAttributes).then(deleteEntitlements).map(Long::longValue);
    }

    private Mono<Long> persistChildEntities(Mono<Long> actionFlow, User item, UpdateActions updateActions) {
        final List<Address> addresses = item.getAddresses();
        if (addresses != null && !addresses.isEmpty() && updateActions.updateAddresses()) {
            actionFlow = actionFlow.then(Flux.fromIterable(addresses).concatMap(address -> {
                DatabaseClient.GenericExecuteSpec insert = getTemplate().getDatabaseClient().sql(insertAddressStatement).bind(FK_USER_ID, item.getId());
                insert = address.getType() != null ? insert.bind(ADDR_COL_TYPE, address.getType()) : insert.bindNull(ADDR_COL_TYPE, String.class);
                insert = address.getFormatted() != null ? insert.bind(ADDR_COL_FORMATTED, address.getFormatted()) : insert.bindNull(ADDR_COL_FORMATTED, String.class);
                insert = address.getStreetAddress() != null ? insert.bind(ADDR_COL_STREET_ADDRESS, address.getStreetAddress()) : insert.bindNull(ADDR_COL_STREET_ADDRESS, String.class);
                insert = address.getLocality() != null ? insert.bind(ADDR_COL_LOCALITY, address.getLocality()) : insert.bindNull(ADDR_COL_LOCALITY, String.class);
                insert = address.getRegion() != null ? insert.bind(ADDR_COL_REGION, address.getRegion()) : insert.bindNull(ADDR_COL_REGION, String.class);
                insert = address.getPostalCode() != null ? insert.bind(ADDR_COL_POSTAL_CODE, address.getPostalCode()) : insert.bindNull(ADDR_COL_POSTAL_CODE, String.class);
                insert = address.getCountry() != null ? insert.bind(ADDR_COL_COUNTRY, address.getCountry()) : insert.bindNull(ADDR_COL_COUNTRY, String.class);
                insert = address.isPrimary() != null ? insert.bind(ADDR_COL_PRIMARY, address.isPrimary()) : insert.bindNull(ADDR_COL_PRIMARY, Boolean.class);
                return insert.fetch().rowsUpdated();
            }).reduce(Long::sum));
        }

        if (updateActions.updateRole()) {
            actionFlow = addJdbcRoles(actionFlow, item, item.getRoles(), "organization_user_roles");
        }
        if (updateActions.updateDynamicRole()) {
            actionFlow = addJdbcRoles(actionFlow, item, item.getDynamicRoles(), "organization_dynamic_user_roles");
        }

        final List<String> entitlements = item.getEntitlements();
        if (entitlements != null && !entitlements.isEmpty() && updateActions.updateEntitlements()) {
            actionFlow = actionFlow.then(Flux.fromIterable(entitlements).concatMap(entitlement ->
                            getTemplate().getDatabaseClient().sql("INSERT INTO organization_user_entitlements(user_id, entitlement) VALUES(:user, :entitlement)")
                                    .bind("user", item.getId())
                                    .bind("entitlement", entitlement)
                                    .fetch().rowsUpdated())
                    .reduce(Long::sum));
        }

        if (updateActions.updateAttributes()) {
            Optional<Mono<Long>> attributes = concat(concat(concat(convertAttributes(item, item.getEmails(), ATTRIBUTE_USER_FIELD_EMAIL),
                                    convertAttributes(item, item.getPhoneNumbers(), ATTRIBUTE_USER_FIELD_PHONE)),
                            convertAttributes(item, item.getIms(), ATTRIBUTE_USER_FIELD_IM)),
                    convertAttributes(item, item.getPhotos(), ATTRIBUTE_USER_FIELD_PHOTO))
                    .map(jdbcAttr -> {
                        DatabaseClient.GenericExecuteSpec insert = getTemplate().getDatabaseClient().sql(insertAttributesStatement)
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
        }
        return actionFlow;
    }

    private <T extends JdbcOrganizationUser.AbstractRole> Mono<Long> addJdbcRoles(
            Mono<Long> actionFlow, User item, List<String> roles, String roleTable) {
        if (roles != null && !roles.isEmpty()) {
            return actionFlow.then(Flux.fromIterable(roles).concatMap(role -> {
                try {
                    return getTemplate().getDatabaseClient().sql("INSERT INTO " + roleTable + "(user_id, role) VALUES(:user, :role)")
                            .bind("user", item.getId())
                            .bind("role", role)
                            .fetch().rowsUpdated();
                } catch (Exception e) {
                    LOGGER.error("An unexpected error has occurred", e);
                    return Mono.just(0L);
                }
            }).reduce(Long::sum));
        }
        return actionFlow;
    }

    private Stream<JdbcOrganizationUser.Attribute> convertAttributes(User item, List<Attribute> attributes, String field) {
        if (attributes != null && !attributes.isEmpty()) {
            return attributes.stream().map(attr -> {
                JdbcOrganizationUser.Attribute jdbcAttr = mapper.map(attr, JdbcOrganizationUser.Attribute.class);
                jdbcAttr.setUserId(item.getId());
                jdbcAttr.setUserField(field);
                return jdbcAttr;
            });
        }
        return Stream.empty();
    }

    private Mono<Long> deleteChildEntities(String userId, UpdateActions actions) {
        var result = Mono.<Long>empty();
        Query criteria = Query.query(where(FK_USER_ID).is(userId));
        if (actions.updateRole()) {
            Mono<Long> deleteRoles = getTemplate().delete(JdbcOrganizationUser.Role.class).matching(criteria).all();
            result = result.then(deleteRoles);
        }
        if (actions.updateDynamicRole()) {
            Mono<Long> deleteDynamicRoles = getTemplate().delete(JdbcOrganizationUser.DynamicRole.class).matching(criteria).all();
            result = result.then(deleteDynamicRoles);
        }
        if (actions.updateAddresses()) {
            Mono<Long> deleteAddresses = getTemplate().delete(JdbcOrganizationUser.Address.class).matching(criteria).all();
            result = result.then(deleteAddresses);
        }
        if (actions.updateAttributes()) {
            Mono<Long> deleteAttributes = getTemplate().delete(JdbcOrganizationUser.Attribute.class).matching(criteria).all();
            result = result.then(deleteAttributes);
        }
        if (actions.updateEntitlements()) {
            Mono<Long> deleteEntitlements = getTemplate().delete(JdbcOrganizationUser.Entitlements.class).matching(criteria).all();
            result = result.then(deleteEntitlements);
        }
        return result;
    }

    private Single<User> completeUser(User userToComplete) {
        return Single.just(userToComplete)
                .flatMap(user ->
                        roleRepository.findByUserId(user.getId()).map(JdbcOrganizationUser.Role::getRole).toList().map(roles -> {
                            user.setRoles(roles);
                            return user;
                        }))
                .flatMap(user ->
                        dynamicRoleRepository.findByUserId(user.getId()).map(JdbcOrganizationUser.DynamicRole::getRole).toList().map(roles -> {
                            user.setDynamicRoles(roles);
                            return user;
                        }))
                .flatMap(user ->
                        entitlementRepository.findByUserId(user.getId()).map(JdbcOrganizationUser.Entitlements::getEntitlement).toList().map(entitlements -> {
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
                                    Map<String, List<Attribute>> map = attributes.stream().collect(StreamUtils.toMultiMap(JdbcOrganizationUser.Attribute::getUserField, attr -> mapper.map(attr, Attribute.class)));
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
