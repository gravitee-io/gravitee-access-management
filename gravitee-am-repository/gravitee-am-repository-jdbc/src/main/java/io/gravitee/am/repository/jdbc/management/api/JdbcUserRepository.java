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
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.UserIdentity;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.repository.common.UserIdFields;
import io.gravitee.am.repository.exceptions.RepositoryConnectionException;
import io.gravitee.am.repository.jdbc.common.dialect.ScimSearch;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcUser;
import io.gravitee.am.repository.jdbc.management.api.model.mapper.EnrolledFactorsConverter;
import io.gravitee.am.repository.jdbc.management.api.model.mapper.MapToStringConverter;
import io.gravitee.am.repository.jdbc.management.api.model.mapper.X509Converter;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringDynamicUserGroupRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringDynamicUserRoleRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringUserAddressesRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringUserAttributesRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringUserEntitlementRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringUserIdentitiesRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringUserRepository;
import io.gravitee.am.repository.jdbc.management.api.spring.user.SpringUserRoleRepository;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.r2dbc.spi.R2dbcNonTransientResourceException;
import io.r2dbc.spi.R2dbcTransientException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeSource;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleSource;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.NonTransientDataAccessResourceException;
import org.springframework.dao.TransientDataAccessException;
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
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.model.common.Page.pageFromOffset;
import static io.gravitee.am.repository.jdbc.common.dialect.DatabaseDialectHelper.ScimRepository.USERS;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Stream.concat;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.fluxToFlowable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToMaybe;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

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
    private static final String USER_COL_LAST_LOGIN_WITH_CREDENTIALS = "last_login_with_credentials";
    private static final String USER_COL_CREATED_AT = "created_at";
    private static final String USER_COL_UPDATED_AT = "updated_at";
    private static final String USER_COL_X_509_CERTIFICATES = "x509_certificates";
    private static final String USER_COL_FACTORS = "factors";
    private static final String USER_COL_ADDITIONAL_INFORMATION = "additional_information";
    private static final String USER_COL_FORCE_RESET_PASSWORD = "force_reset_password";

    public static final String USER_COL_LAST_PASSWORD_RESET = "last_password_reset";
    public static final String USER_COL_LAST_USERNAME_RESET = "last_username_reset";
    public static final String USER_COL_LAST_LOGOUT_AT = "last_logout_at";
    public static final String USER_COL_LAST_IDENTITY_USED = "last_identity_used";
    public static final String USER_COL_IDENTITY_ID = "identity_id";
    public static final String USER_COL_PROVIDER_ID = "provider_id";
    public static final String USER_COL_LINKED_AT = "linked_at";

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
            USER_COL_LAST_LOGIN_WITH_CREDENTIALS,
            USER_COL_LAST_PASSWORD_RESET,
            USER_COL_LAST_USERNAME_RESET,
            USER_COL_LAST_LOGOUT_AT,
            USER_COL_MFA_ENROLLMENT_SKIPPED_AT,
            USER_COL_CREATED_AT,
            USER_COL_UPDATED_AT,
            USER_COL_X_509_CERTIFICATES,
            USER_COL_FACTORS,
            USER_COL_LAST_IDENTITY_USED,
            USER_COL_ADDITIONAL_INFORMATION,
            USER_COL_FORCE_RESET_PASSWORD
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

    private static final List<String> IDENTITIES_COLUMNS = List.of(
            FK_USER_ID,
            USER_COL_IDENTITY_ID,
            USER_COL_USERNAME,
            USER_COL_PROVIDER_ID,
            USER_COL_LINKED_AT,
            USER_COL_ADDITIONAL_INFORMATION
    );
    private static final String REF_ID = "refId";
    private static final String REF_TYPE = "refType";
    private static final String EMAIL = "email";
    private static final String USER_ID = "user_id";
    private static final String DYNAMIC_USER_GROUPS_TABLE = "dynamic_user_groups";

    private static final UserIdFields USER_ID_FIELDS = new UserIdFields(USER_COL_ID, USER_COL_SOURCE, USER_COL_EXTERNAL_ID);

    private String updateUserStatement;
    private String insertUserStatement;
    private String insertAddressStatement;
    private String insertAttributesStatement;
    private String insertIdentitiesStatement;

    @Autowired
    protected SpringUserRepository userRepository;

    @Autowired
    protected SpringUserRoleRepository roleRepository;

    @Autowired
    protected SpringDynamicUserRoleRepository dynamicRoleRepository;

    @Autowired
    protected SpringDynamicUserGroupRepository dynamicGroupRepository;

    @Autowired
    protected SpringUserAddressesRepository addressesRepository;

    @Autowired
    protected SpringUserAttributesRepository attributesRepository;

    @Autowired
    protected SpringUserEntitlementRepository entitlementRepository;

    @Autowired
    protected SpringUserIdentitiesRepository identitiesRepository;

    @Autowired
    protected Environment environment;

    private final EnrolledFactorsConverter enrolledFactorsConverter = new EnrolledFactorsConverter();
    private final MapToStringConverter mapToStringConverter = new MapToStringConverter();
    private final X509Converter x509Converter = new X509Converter();

    protected boolean acceptUpsert() {
        return environment.getProperty("resilience.enabled", Boolean.class, false);
    }

    protected User toEntity(JdbcUser entity) {
        var result = new User();
        result.setDisplayName(entity.getDisplayName());
        result.setClient(entity.getClient());
        result.setId(entity.getId());
        result.setEmail(entity.getEmail());
        result.setExternalId(entity.getExternalId());
        result.setFirstName(entity.getFirstName());
        result.setLastName(entity.getLastName());
        result.setLoginsCount(entity.getLoginsCount());
        result.setNewsletter(entity.getNewsletter());
        result.setNickName(entity.getNickName());
        result.setPreferredLanguage(entity.getPreferredLanguage());
        result.setReferenceId(entity.getReferenceId());
        result.setReferenceType(ReferenceType.valueOf(entity.getReferenceType()));
        result.setRegistrationAccessToken(entity.getRegistrationAccessToken());
        result.setRegistrationUserUri(entity.getRegistrationUserUri());
        result.setSource(entity.getSource());
        result.setTitle(entity.getTitle());
        result.setType(entity.getType());
        result.setUsername(entity.getUsername());
        result.setRegistrationCompleted(entity.isRegistrationCompleted());
        result.setPreRegistration(entity.isPreRegistration());
        result.setInternal(entity.isInternal());
        result.setEnabled(entity.isEnabled());
        result.setCredentialsNonExpired(entity.isCredentialsNonExpired());
        result.setAccountNonExpired(entity.isAccountNonExpired());
        result.setAccountNonLocked(entity.isAccountNonLocked());
        result.setLastIdentityUsed(entity.getLastIdentityUsed());

        if (entity.getAccountLockedAt() != null) {
            result.setAccountLockedAt(Date.from(entity.getAccountLockedAt().atZone(UTC).toInstant()));
        }
        if (entity.getAccountLockedUntil() != null) {
            result.setAccountLockedUntil(Date.from(entity.getAccountLockedUntil().atZone(UTC).toInstant()));
        }
        if (entity.getCreatedAt() != null) {
            result.setCreatedAt(Date.from(entity.getCreatedAt().atZone(UTC).toInstant()));
        }
        if (entity.getLastLogoutAt() != null) {
            result.setLastLogoutAt(Date.from(entity.getLastLogoutAt().atZone(UTC).toInstant()));
        }
        if (entity.getLastPasswordReset() != null) {
            result.setLastPasswordReset(Date.from(entity.getLastPasswordReset().atZone(UTC).toInstant()));
        }
        if (entity.getLoggedAt() != null) {
            result.setLoggedAt(Date.from(entity.getLoggedAt().atZone(UTC).toInstant()));
        }
        if (entity.getMfaEnrollmentSkippedAt() != null) {
            result.setMfaEnrollmentSkippedAt(Date.from(entity.getMfaEnrollmentSkippedAt().atZone(UTC).toInstant()));
        }
        if (entity.getUpdatedAt() != null) {
            result.setUpdatedAt(Date.from(entity.getUpdatedAt().atZone(UTC).toInstant()));
        }
        if (entity.getLastLoginWithCredentials() != null) {
            result.setLastLoginWithCredentials(Date.from(entity.getLastLoginWithCredentials().atZone(UTC).toInstant()));
        }
        if (entity.getLastUsernameReset() != null) {
            result.setLastUsernameReset(Date.from(entity.getLastUsernameReset().atZone(UTC).toInstant()));
        }

        result.setFactors(enrolledFactorsConverter.convertFrom(entity.getFactors(), null));
        result.setAdditionalInformation(mapToStringConverter.convertFrom(entity.getAdditionalInformation(), null));
        result.setX509Certificates(x509Converter.convertFrom(entity.getX509Certificates(), null));
        result.setForceResetPassword(entity.getForceResetPassword());

        return result;
    }

    protected JdbcUser toJdbcEntity(User entity) {
        var result = new JdbcUser();

        result.setDisplayName(entity.getDisplayName());
        result.setClient(entity.getClient());
        result.setId(entity.getId());
        result.setEmail(entity.getEmail());
        result.setExternalId(entity.getExternalId());
        result.setFirstName(entity.getFirstName());
        result.setLastName(entity.getLastName());
        result.setLoginsCount(entity.getLoginsCount());
        result.setNewsletter(entity.isNewsletter());
        result.setNickName(entity.getNickName());
        result.setPreferredLanguage(entity.getPreferredLanguage());
        result.setReferenceId(entity.getReferenceId());
        result.setReferenceType(entity.getReferenceType().name());
        result.setRegistrationAccessToken(entity.getRegistrationAccessToken());
        result.setRegistrationUserUri(entity.getRegistrationUserUri());
        result.setSource(entity.getSource());
        result.setTitle(entity.getTitle());
        result.setType(entity.getType());
        result.setUsername(entity.getUsername());
        result.setRegistrationCompleted(entity.isRegistrationCompleted());
        result.setPreRegistration(entity.isPreRegistration());
        result.setInternal(entity.isInternal());
        result.setEnabled(entity.isEnabled());
        result.setCredentialsNonExpired(entity.isCredentialsNonExpired());
        result.setAccountNonExpired(entity.isAccountNonExpired());
        result.setAccountNonLocked(entity.isAccountNonLocked());
        result.setLastIdentityUsed(entity.getLastIdentityUsed());

        if (entity.getAccountLockedAt() != null) {
            result.setAccountLockedAt(LocalDateTime.ofInstant(entity.getAccountLockedAt().toInstant(), UTC));
        }
        if (entity.getAccountLockedUntil() != null) {
            result.setAccountLockedUntil(LocalDateTime.ofInstant(entity.getAccountLockedUntil().toInstant(), UTC));
        }
        if (entity.getCreatedAt() != null) {
            result.setCreatedAt(LocalDateTime.ofInstant(entity.getCreatedAt().toInstant(), UTC));
        }
        if (entity.getLastLogoutAt() != null) {
            result.setLastLogoutAt(LocalDateTime.ofInstant(entity.getLastLogoutAt().toInstant(), UTC));
        }
        if (entity.getLastPasswordReset() != null) {
            result.setLastPasswordReset(LocalDateTime.ofInstant(entity.getLastPasswordReset().toInstant(), UTC));
        }
        if (entity.getLoggedAt() != null) {
            result.setLoggedAt(LocalDateTime.ofInstant(entity.getLoggedAt().toInstant(), UTC));
        }
        if (entity.getMfaEnrollmentSkippedAt() != null) {
            result.setMfaEnrollmentSkippedAt(LocalDateTime.ofInstant(entity.getMfaEnrollmentSkippedAt().toInstant(), UTC));
        }
        if (entity.getUpdatedAt() != null) {
            result.setUpdatedAt(LocalDateTime.ofInstant(entity.getUpdatedAt().toInstant(), UTC));
        }
        if (entity.getLastLoginWithCredentials() != null) {
            result.setLastLoginWithCredentials(LocalDateTime.ofInstant(entity.getLastLoginWithCredentials().toInstant(), UTC));
        }
        if (entity.getLastUsernameReset() != null) {
            result.setLastUsernameReset(LocalDateTime.ofInstant(entity.getLastUsernameReset().toInstant(), UTC));
        }

        result.setFactors(enrolledFactorsConverter.convertTo(entity.getFactors(), null));
        result.setAdditionalInformation(mapToStringConverter.convertTo(entity.getAdditionalInformation(), null));
        result.setX509Certificates(x509Converter.convertTo(entity.getX509Certificates(), null));
        result.setForceResetPassword(entity.getForceResetPassword());

        return result;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insertUserStatement = createInsertStatement("users", USER_COLUMNS);
        this.updateUserStatement = createUpdateStatement("users", USER_COLUMNS, List.of(USER_COL_ID));
        this.insertAddressStatement = createInsertStatement("user_addresses", ADDRESS_COLUMNS);
        this.insertAttributesStatement = createInsertStatement("user_attributes", ATTRIBUTES_COLUMNS);
        this.insertIdentitiesStatement = createInsertStatement("user_identities", IDENTITIES_COLUMNS);
    }

    @Override
    public Flowable<User> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findByReference({})", referenceId);
        return userRepository.findByReference(referenceType.name(), referenceId)
                .map(this::toEntity)
                .concatMap(user -> completeUser(user).toFlowable())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        LOGGER.debug("findAll({}, {}, {}, {})", referenceType, referenceId, page, size);
        return fluxToFlowable(getTemplate().select(JdbcUser.class)
                .matching(Query.query(where(USER_COL_REFERENCE_ID).is(referenceId)
                                .and(where(USER_COL_REFERENCE_TYPE).is(referenceType.name())))
                        .sort(Sort.by(USER_COL_USERNAME).ascending())
                        .with(PageRequest.of(page, size))
                ).all())
                .map(this::toEntity)
                .concatMap(user -> completeUser(user).toFlowable())
                .toList()
                .concatMap(content -> userRepository.countByReference(referenceType.name(), referenceId)
                        .map((count) -> new Page<>(content, page, count)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> findAllScim(ReferenceType referenceType, String referenceId, int startIndex, int count) {
        LOGGER.debug("findAllScim({}, {}, {}, {})", referenceType, referenceId, startIndex, count);
        Sort sortByUsername = Sort.by(USER_COL_USERNAME).ascending();
        OffsetPageRequest pageable = new OffsetPageRequest(startIndex, count, sortByUsername);
        return fluxToFlowable(getTemplate().select(JdbcUser.class)
                .matching(Query.query(where(USER_COL_REFERENCE_ID).is(referenceId)
                                .and(where(USER_COL_REFERENCE_TYPE).is(referenceType.name())))
                        .with(pageable)
                ).all())
                .map(this::toEntity)
                .concatMap(user -> completeUser(user).toFlowable())
                .toList()
                .concatMap(content -> userRepository.countByReference(referenceType.name(), referenceId)
                        .map((totalCount) -> new Page<>(content, pageable.getPageNumber(), totalCount)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size) {
        LOGGER.debug("search({}, {}, {}, {}, {})", referenceType, referenceId, query, page, size);

        boolean wildcardSearch = query.contains("*");
        String wildcardValue = query.replaceAll("\\*+", "%");

        String search = this.databaseDialectHelper.buildSearchUserQuery(wildcardSearch, page, size);
        String count = this.databaseDialectHelper.buildCountUserQuery(wildcardSearch);

        return fluxToFlowable(getTemplate().getDatabaseClient().sql(search)
                .bind(ATTR_COL_VALUE, wildcardSearch ? wildcardValue : query)
                .bind(REF_ID, referenceId)
                .bind(REF_TYPE, referenceType.name())
                .map((row, rowMetadata) -> rowMapper.read(JdbcUser.class, row)).all())
                .map(this::toEntity)
                .concatMap(app -> completeUser(app).toFlowable())
                .toList()
                .concatMap(data -> monoToSingle(getTemplate().getDatabaseClient().sql(count)
                        .bind(ATTR_COL_VALUE, wildcardSearch ? wildcardValue : query)
                        .bind(REF_ID, referenceId)
                        .bind(REF_TYPE, referenceType.name())
                        .map((row, rowMetadata) -> row.get(0, Long.class))
                        .first())
                        .map(total -> new Page<>(data, page, total)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria criteria, int page, int size) {
        LOGGER.debug("search({}, {}, {}, {}, {})", referenceType, referenceId, criteria, page, size);

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(" FROM users WHERE reference_id = :refId AND reference_type = :refType AND ");
        ScimSearch search = this.databaseDialectHelper.prepareScimSearchQuery(queryBuilder, criteria, USER_COL_USERNAME, page, size, USERS);

        // execute query
        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec executeSelect = getTemplate().getDatabaseClient().sql(search.getSelectQuery());
        executeSelect = executeSelect.bind(REF_TYPE, referenceType.name()).bind(REF_ID, referenceId);
        for (Map.Entry<String, Object> entry : search.getBinding().entrySet()) {
            executeSelect = executeSelect.bind(entry.getKey(), entry.getValue());
        }
        Flux<JdbcUser> userFlux = executeSelect.map((row, rowMetadata) -> rowMapper.read(JdbcUser.class, row)).all();

        // execute count to provide total in the Page
        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec executeCount = getTemplate().getDatabaseClient().sql(search.getCountQuery());
        executeCount = executeCount.bind(REF_TYPE, referenceType.name()).bind(REF_ID, referenceId);
        for (Map.Entry<String, Object> entry : search.getBinding().entrySet()) {
            executeCount = executeCount.bind(entry.getKey(), entry.getValue());
        }
        Mono<Long> userCount = executeCount.map((row, rowMetadata) -> row.get(0, Long.class)).first();

        return fluxToFlowable(userFlux)
                .map(this::toEntity)
                .concatMap(user -> completeUser(user).toFlowable())
                .toList()
                .concatMap(list -> monoToSingle(userCount).map(total -> new Page<User>(list, page, total)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> searchScim(ReferenceType referenceType, String referenceId, FilterCriteria criteria, int startIndex, int count) {
        LOGGER.debug("searchScim({}, {}, {}, {}, {})", referenceType, referenceId, criteria, startIndex, count);

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(" FROM users WHERE reference_id = :refId AND reference_type = :refType AND ");
        ScimSearch search = this.databaseDialectHelper.prepareScimSearchQueryUsingOffset(queryBuilder, criteria, USER_COL_USERNAME, startIndex, count, USERS);

        // execute query
        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec executeSelect = getTemplate().getDatabaseClient().sql(search.getSelectQuery());
        executeSelect = executeSelect.bind("refType", referenceType.name()).bind("refId", referenceId);
        for (Map.Entry<String, Object> entry : search.getBinding().entrySet()) {
            executeSelect = executeSelect.bind(entry.getKey(), entry.getValue());
        }
        Flux<JdbcUser> userFlux = executeSelect.map((row, rowMetadata) -> rowMapper.read(JdbcUser.class, row)).all();

        // execute count to provide total in the Page
        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec executeCount = getTemplate().getDatabaseClient().sql(search.getCountQuery());
        executeCount = executeCount.bind("refType", referenceType.name()).bind("refId", referenceId);
        for (Map.Entry<String, Object> entry : search.getBinding().entrySet()) {
            executeCount = executeCount.bind(entry.getKey(), entry.getValue());
        }
        Mono<Long> userCount = executeCount.map((row, rowMetadata) -> row.get(0, Long.class)).first();

        return fluxToFlowable(userFlux)
                .map(this::toEntity)
                .concatMap(user -> completeUser(user).toFlowable())
                .toList()
                .concatMap(list -> monoToSingle(userCount).map(total -> new Page<User>(list, pageFromOffset(startIndex, count), total)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<User> search(ReferenceType referenceType, String referenceId, FilterCriteria criteria) {
        LOGGER.debug("search({}, {}, {})", referenceType, referenceId, criteria);

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(" FROM users WHERE reference_id = :refId AND reference_type = :refType AND ");
        ScimSearch search = this.databaseDialectHelper.prepareScimSearchQuery(queryBuilder, criteria, USER_COL_USERNAME, -1, -1, USERS);

        // execute query
        org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec executeSelect = getTemplate().getDatabaseClient().sql(search.getSelectQuery());
        executeSelect = executeSelect.bind(REF_TYPE, referenceType.name()).bind(REF_ID, referenceId);
        for (Map.Entry<String, Object> entry : search.getBinding().entrySet()) {
            executeSelect = executeSelect.bind(entry.getKey(), entry.getValue());
        }
        Flux<JdbcUser> userFlux = executeSelect.map((row, rowMetadata) -> rowMapper.read(JdbcUser.class, row)).all();

        return fluxToFlowable(userFlux)
                .map(this::toEntity)
                .concatMap(user -> completeUser(user).toFlowable())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<User> findByDomainAndEmail(String domain, String email, boolean strict) {
        return fluxToFlowable(getTemplate().getDatabaseClient().sql(databaseDialectHelper.buildFindUserByReferenceAndEmail(DOMAIN, domain, email, strict))
                .bind(REF_ID, domain)
                .bind(REF_TYPE, DOMAIN.name())
                .bind(EMAIL, email)
                .map((row, rowMetadata) -> rowMapper.read(JdbcUser.class, row))
                .all())
                .map(this::toEntity)
                .concatMap(user -> completeUser(user).toFlowable())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findByUsernameAndDomain(String domain, String username) {
        LOGGER.debug("findByUsernameAndDomain({},{},{})", domain, username);
        return userRepository.findByUsername(ReferenceType.DOMAIN.name(), domain, username)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe())
                .onErrorResumeNext(this::mapException)
                .observeOn(Schedulers.computation());
    }

    public Maybe<User> findById(UserId id) {
        return monoToMaybe(getTemplate().select(JdbcUser.class)
                .matching(Query.query(userIdMatches(id)))
                .first())
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source) {
        LOGGER.debug("findByUsernameAndSource({},{},{},{})", referenceType, referenceId, username, source);
        return userRepository.findByUsernameAndSource(referenceType.name(), referenceId, username, source)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe())
                .onErrorResumeNext(this::mapException)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source, boolean includeLinkedIdentities) {
        LOGGER.debug("findByUsernameAndSource({},{},{},{},{})", referenceType, referenceId, username, source, includeLinkedIdentities);
        return userRepository.findByUsernameAndSource(referenceType.name(), referenceId, username, source)
                .switchIfEmpty(Maybe.defer(() -> includeLinkedIdentities ? userRepository.findByUsernameAndLinkedIdentities(referenceType.name(), referenceId, username, source) : Maybe.empty()))
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe())
                .onErrorResumeNext(this::mapException)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findByExternalIdAndSource(ReferenceType referenceType, String referenceId, String externalId, String source) {
        LOGGER.debug("findByExternalIdAndSource({},{},{},{})", referenceType, referenceId, externalId, source);
        return userRepository.findByExternalIdAndSource(referenceType.name(), referenceId, externalId, source)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe())
                .onErrorResumeNext(this::mapException)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<User> findByIdIn(ReferenceType referenceType, String referenceId, List<String> ids) {
        LOGGER.debug("findByIdIn({})", ids);
        if (ids == null || ids.isEmpty()) {
            return Flowable.empty();
        }
        return userRepository.findByIdIn(referenceType.name(), referenceId, ids)
                .map(this::toEntity)
                .concatMap(user -> completeUser(user).toFlowable())
                .onErrorResumeNext(err -> Flowable.fromMaybe(mapException(err)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findById(Reference reference, UserId userId) {
        LOGGER.debug("findById({},{})", reference, userId);
        var criteria = userIdMatches(userId)
                .and(referenceMatches(reference));
        return findOne(Query.query(criteria), JdbcUser.class)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toMaybe())
                .onErrorResumeNext(this::mapException)
                .observeOn(Schedulers.computation());
    }

    private MaybeSource<? extends User> mapException(Throwable error) {
        if (error instanceof NonTransientDataAccessResourceException ||
                error instanceof R2dbcNonTransientResourceException ||
                error.getCause() instanceof R2dbcNonTransientResourceException) {
            return Maybe.error(new RepositoryConnectionException(error));
        }
        return Maybe.error(error);
    }

    private SingleSource<? extends User> mapExceptionAsSingle(Throwable error, User user, boolean fromUpdate) {
        if (fromUpdate && acceptUpsert() && isTransientDataException(error)) {
            // when resilient mode is enabled, we may have some edge case
            // where a user who was in cache need to be updated but is missing from the CP
            // so in this case, we are falling back to an insert
            return this.create(user).onErrorResumeNext(createError -> {
                LOGGER.warn("Upsert fails for user {} due to : {}", user.getId(), error.getMessage());
                return Single.error(error); // continue with the original error
            });
        }
        return Single.fromMaybe(mapException(error));
    }

    private boolean isTransientDataException(Throwable error) {
        return error instanceof TransientDataAccessException ||
                error instanceof R2dbcTransientException ||
                error.getCause() instanceof R2dbcTransientException;
    }

    @Override
    public Single<Long> countByReference(ReferenceType refType, String domain) {
        return userRepository.countByReference(refType.name(), domain)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Long> countByApplication(String domain, String application) {
        return userRepository.countByClient(ReferenceType.DOMAIN.name(), domain, application)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Map<Object, Object>> statistics(AnalyticsQuery query) {
        return switch (query.getField()) {
            case Field.USER_STATUS -> usersStatusRepartition(query)
                    .observeOn(Schedulers.computation());
            case Field.USER_REGISTRATION -> registrationsStatusRepartition(query)
                    .observeOn(Schedulers.computation());
            default -> Single.just(Collections.emptyMap());
        };
    }

    @Override
    public Criteria userIdMatches(UserId userId) {
        if (userId.id() == null) {
            throw new IllegalArgumentException("Internal user id must not be null");
        }
        return Criteria.where(USER_COL_ID).is(userId.id());
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
                .flatMap(stats -> disabled.map(count -> {
                    LOGGER.debug("usersStatusRepartition(disabled) = {}", count);
                    stats.put("disabled", count);
                    return stats;
                })).flatMap(stats -> locked.map(count -> {
                    LOGGER.debug("usersStatusRepartition(locked) = {}", count);
                    stats.put("locked", count);
                    return stats;
                })).flatMap(stats -> inactive.map(count -> {
                    LOGGER.debug("usersStatusRepartition(inactive) = {}", count);
                    stats.put("inactive", count);
                    return stats;
                })).flatMap(stats -> total.map(count -> {
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
                .flatMap(stats -> total.map(count -> {
                    LOGGER.debug("registrationsStatusRepartition(total) = {}", count);
                    stats.put("total", count);
                    return stats;
                })).flatMap(stats -> completed.map(count -> {
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
                .flatMap(user -> completeUser(user).toMaybe())
                .onErrorResumeNext(this::mapException)
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
        insertSpec = addQuotedField(insertSpec, USER_COL_LAST_LOGIN_WITH_CREDENTIALS, dateConverter.convertTo(item.getLastLoginWithCredentials(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_LAST_PASSWORD_RESET, dateConverter.convertTo(item.getLastPasswordReset(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_LAST_USERNAME_RESET, dateConverter.convertTo(item.getLastUsernameReset(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_LAST_LOGOUT_AT, dateConverter.convertTo(item.getLastLogoutAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_MFA_ENROLLMENT_SKIPPED_AT, dateConverter.convertTo(item.getMfaEnrollmentSkippedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_LAST_IDENTITY_USED, item.getLastIdentityUsed(), String.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, USER_COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, USER_COL_X_509_CERTIFICATES, item.getX509Certificates());
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, USER_COL_FACTORS, item.getFactors());
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, USER_COL_ADDITIONAL_INFORMATION, item.getAdditionalInformation());
        insertSpec = addQuotedField(insertSpec, USER_COL_FORCE_RESET_PASSWORD, item.getForceResetPassword(), Boolean.class);

        Mono<Long> insertAction = insertSpec.fetch().rowsUpdated();

        insertAction = persistChildEntities(insertAction, item, UpdateActions.updateAll());

        return monoToSingle(insertAction.as(trx::transactional))
                .flatMap((i) -> Single.just(item))
                .onErrorResumeNext(err -> mapExceptionAsSingle(err, item, false))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<User> update(User item) {
        return update(item, UpdateActions.updateAll());
    }

    @Override
    public Single<User> update(User item, UpdateActions updateActions) {
        LOGGER.debug("Update User with id {}", item.getId());
        Objects.requireNonNull(item.getId());
        TransactionalOperator trx = TransactionalOperator.create(tm);
        DatabaseClient.GenericExecuteSpec update = getTemplate().getDatabaseClient().sql(updateUserStatement);

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
        update = addQuotedField(update, USER_COL_LAST_LOGIN_WITH_CREDENTIALS, dateConverter.convertTo(item.getLastLoginWithCredentials(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_LAST_PASSWORD_RESET, dateConverter.convertTo(item.getLastPasswordReset(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_LAST_USERNAME_RESET, dateConverter.convertTo(item.getLastUsernameReset(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_LAST_LOGOUT_AT, dateConverter.convertTo(item.getLastLogoutAt(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_MFA_ENROLLMENT_SKIPPED_AT, dateConverter.convertTo(item.getMfaEnrollmentSkippedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_LAST_IDENTITY_USED, item.getLastIdentityUsed(), String.class);
        update = addQuotedField(update, USER_COL_CREATED_AT, dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        update = addQuotedField(update, USER_COL_UPDATED_AT, dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        update = databaseDialectHelper.addJsonField(update, USER_COL_X_509_CERTIFICATES, item.getX509Certificates());
        update = databaseDialectHelper.addJsonField(update, USER_COL_FACTORS, item.getFactors());
        update = databaseDialectHelper.addJsonField(update, USER_COL_ADDITIONAL_INFORMATION, item.getAdditionalInformation());
        update = addQuotedField(update, USER_COL_FORCE_RESET_PASSWORD, item.getForceResetPassword(), Boolean.class);

        Mono<Long> action = update.fetch().rowsUpdated();

        if (updateActions.updateRequire()) {
            action = deleteChildEntities(item.getId(), updateActions).then(action);
            action = persistChildEntities(action, item, updateActions);
        }

        return monoToSingle(action.as(trx::transactional))
                .flatMap((i) -> {
                    if (acceptUpsert() && i == 0) {
                        return this.create(item);
                    } else {
                        return Single.just(item);
                    }
                })
                .onErrorResumeNext(err -> mapExceptionAsSingle(err, item, true))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Long> delete = getTemplate().delete(JdbcUser.class).matching(Query.query(where("id").is(id))).all();

        return monoToCompletable(delete.then(deleteChildEntities(id, UpdateActions.updateAll())).as(trx::transactional))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("deleteByReference({}, {})", referenceType, referenceId);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Long> delete = getTemplate().getDatabaseClient().sql("DELETE FROM users WHERE reference_type = :refType AND reference_id = :refId").bind(REF_TYPE, referenceType.name()).bind(REF_ID, referenceId).fetch().rowsUpdated();
        return monoToCompletable(deleteChildEntitiesByRef(referenceType.name(), referenceId).then(delete).as(trx::transactional))
                .observeOn(Schedulers.computation());
    }

    private Mono<Long> deleteChildEntitiesByRef(String refType, String refId) {
        Mono<Long> deleteRoles = getTemplate().getDatabaseClient().sql("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind(REF_TYPE, refType).bind(REF_ID, refId).fetch().rowsUpdated();
        Mono<Long> deleteAddresses = getTemplate().getDatabaseClient().sql("DELETE FROM user_addresses WHERE user_id IN (SELECT id FROM users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind(REF_TYPE, refType).bind(REF_ID, refId).fetch().rowsUpdated();
        Mono<Long> deleteAttributes = getTemplate().getDatabaseClient().sql("DELETE FROM user_attributes WHERE user_id IN (SELECT id FROM users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind(REF_TYPE, refType).bind(REF_ID, refId).fetch().rowsUpdated();
        Mono<Long> deleteEntitlements = getTemplate().getDatabaseClient().sql("DELETE FROM user_entitlements WHERE user_id IN (SELECT id FROM users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind(REF_TYPE, refType).bind(REF_ID, refId).fetch().rowsUpdated();
        Mono<Long> deleteIdentities = getTemplate().getDatabaseClient().sql("DELETE FROM user_identities WHERE user_id IN (SELECT id FROM users u WHERE u.reference_type = :refType AND u.reference_id = :refId)").bind(REF_TYPE, refType).bind(REF_ID, refId).fetch().rowsUpdated();
        return deleteRoles
                .then(deleteAddresses)
                .then(deleteAttributes)
                .then(deleteEntitlements)
                .then(deleteIdentities);
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
            actionFlow = addJdbcRoles(actionFlow, item, item.getRoles(), "user_roles");
        }
        if (updateActions.updateDynamicRole()) {
            actionFlow = addJdbcRoles(actionFlow, item, item.getDynamicRoles(), "dynamic_user_roles");
        }
        if (updateActions.updateDynamicGroup()) {
            if (item.getDynamicGroups() != null && !item.getDynamicGroups().isEmpty()) {
                actionFlow = actionFlow.then(Flux.fromIterable(item.getDynamicGroups()).concatMap(group -> {
                    try {
                        return getTemplate().getDatabaseClient().sql("INSERT INTO " + DYNAMIC_USER_GROUPS_TABLE + "(user_id, group_id) VALUES(:user, :group)")
                                .bind("user", item.getId())
                                .bind("group", group)
                                .fetch().rowsUpdated();
                    } catch (Exception e) {
                        LOGGER.error("An unexpected error has occurred", e);
                        return Mono.just(0);
                    }
                }).map(Number::longValue).reduce(Long::sum));
            }
        }

        final List<String> entitlements = item.getEntitlements();
        if (entitlements != null && !entitlements.isEmpty() && updateActions.updateEntitlements()) {
            actionFlow = actionFlow.then(Flux.fromIterable(entitlements).concatMap(entitlement ->
                            getTemplate().getDatabaseClient().sql("INSERT INTO user_entitlements(user_id, entitlement) VALUES(:user, :entitlement)")
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

        if (updateActions.updateIdentities()) {
            final List<UserIdentity> identities = item.getIdentities();
            if (identities != null && !identities.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(identities).concatMap(identity -> {
                DatabaseClient.GenericExecuteSpec insert = getTemplate().getDatabaseClient().sql(insertIdentitiesStatement).bind(FK_USER_ID, item.getId());
                insert = identity.getUserId() != null ? insert.bind(USER_COL_IDENTITY_ID, identity.getUserId()) : insert.bindNull(USER_COL_IDENTITY_ID, String.class);
                insert = identity.getUsername() != null ? insert.bind(USER_COL_USERNAME, identity.getUsername()) : insert.bindNull(USER_COL_USERNAME, String.class);
                insert = identity.getProviderId() != null ? insert.bind(USER_COL_PROVIDER_ID, identity.getProviderId()) : insert.bindNull(USER_COL_PROVIDER_ID, String.class);
                insert = addQuotedField(insert, USER_COL_LINKED_AT, dateConverter.convertTo(identity.getLinkedAt(), null), LocalDateTime.class);
                insert = identity.getAdditionalInformation() != null ? databaseDialectHelper.addJsonField(insert, USER_COL_ADDITIONAL_INFORMATION, identity.getAdditionalInformation()) : insert.bindNull(USER_COL_ADDITIONAL_INFORMATION, String.class);
                return insert.fetch().rowsUpdated();
            }).reduce(Long::sum));
        }
    }

        return actionFlow;
    }

    private Mono<Long> addJdbcRoles(Mono<Long> actionFlow, User item, List<String> roles, String roleTable) {
        if (roles != null && !roles.isEmpty()) {
            return actionFlow.then(Flux.fromIterable(roles).concatMap(role -> {
                try {
                    return getTemplate().getDatabaseClient().sql("INSERT INTO " + roleTable + "(user_id, role) VALUES(:user, :role)")
                            .bind("user", item.getId())
                            .bind("role", role)
                            .fetch().rowsUpdated();
                } catch (Exception e) {
                    LOGGER.error("An unexpected error has occurred", e);
                    return Mono.just(0);
                }
            }).map(Number::longValue).reduce(Long::sum));
        }
        return actionFlow;
    }

    private Stream<JdbcUser.Attribute> convertAttributes(User item, List<Attribute> attributes, String field) {
        if (attributes != null && !attributes.isEmpty()) {
            return attributes.stream().map(attr -> {
                JdbcUser.Attribute jdbcAttr = convertAttribute(attr);
                jdbcAttr.setUserId(item.getId());
                jdbcAttr.setUserField(field);
                return jdbcAttr;
            });
        }
        return Stream.empty();
    }

    private Mono<Long> deleteChildEntities(String userId, UpdateActions actions) {
        final Query criteria = Query.query(where(USER_ID).is(userId));
        var result = Mono.<Long>empty();
        if (actions.updateRole()) {
            Mono<Long> deleteRoles = getTemplate().delete(JdbcUser.Role.class).matching(criteria).all();
            result = result.then(deleteRoles);
        }
        if (actions.updateDynamicRole()) {
            Mono<Long> deleteDynamicRoles = getTemplate().delete(JdbcUser.DynamicRole.class).matching(criteria).all();
            result = result.then(deleteDynamicRoles);
        }
        if (actions.updateDynamicGroup()) {
            Mono<Long> deleteDynamicGroups = getTemplate().delete(JdbcUser.DynamicGroup.class).matching(criteria).all();
            result = result.then(deleteDynamicGroups);
        }
        if (actions.updateAddresses()) {
            Mono<Long> deleteAddresses = getTemplate().delete(JdbcUser.Address.class).matching(criteria).all();
            result = result.then(deleteAddresses);
        }
        if (actions.updateAttributes()) {
            Mono<Long> deleteAttributes = getTemplate().delete(JdbcUser.Attribute.class).matching(criteria).all();
            result = result.then(deleteAttributes);
        }
        if (actions.updateEntitlements()) {
            Mono<Long> deleteEntitlements = getTemplate().delete(JdbcUser.Entitlements.class).matching(criteria).all();
            result = result.then(deleteEntitlements);
        }
        if (actions.updateIdentities()) {
            Mono<Long> deleteIdentities = getTemplate().delete(JdbcUser.Identity.class).matching(criteria).all();
            result = result.then(deleteIdentities);
        }
        return result;
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
                        dynamicGroupRepository.findByUserId(user.getId()).map(JdbcUser.DynamicGroup::getGroup).toList().map(groups -> {
                            user.setDynamicGroups(groups);
                            return user;
                        }))
                .flatMap(user ->
                        entitlementRepository.findByUserId(user.getId()).map(JdbcUser.Entitlements::getEntitlement).toList().map(entitlements -> {
                            user.setEntitlements(entitlements);
                            return user;
                        }))
                .flatMap(user ->
                        addressesRepository.findByUserId(user.getId())
                                .map(this::convertAddress)
                                .toList().map(addresses -> {
                                    user.setAddresses(addresses);
                                    return user;
                                }))
                .flatMap(user ->
                        attributesRepository.findByUserId(user.getId())
                                .toList()
                                .map(attributes -> {
                                    Map<String, List<Attribute>> map = attributes.stream().collect(StreamUtils.toMultiMap(JdbcUser.Attribute::getUserField, this::convertAttribute));
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
                                }))
                .flatMap(user ->
                        identitiesRepository.findByUserId(user.getId())
                                .map(this::convertIdentity)
                                .toList()
                                .map(identities -> {
                                    user.setIdentities(identities);
                                    return user;
                                })
                );
    }

    private JdbcUser.Attribute convertAttribute(Attribute attribute) {
        var result = new JdbcUser.Attribute();
        result.setType(attribute.getType());
        result.setPrimary(attribute.isPrimary());
        result.setValue(attribute.getValue());
        return result;
    }

    private Attribute convertAttribute(JdbcUser.Attribute attribute) {
        var result = new Attribute();
        result.setType(attribute.getType());
        result.setPrimary(attribute.getPrimary());
        result.setValue(attribute.getValue());
        return result;
    }

    private Address convertAddress(JdbcUser.Address address) {
        var result = new Address();
        result.setType(address.getType());
        result.setCountry(address.getCountry());
        result.setPrimary(address.getPrimary());
        result.setFormatted(address.getFormatted());
        result.setLocality(address.getLocality());
        result.setPostalCode(address.getPostalCode());
        result.setRegion(address.getRegion());
        result.setStreetAddress(address.getStreetAddress());
        return result;
    }

    private UserIdentity convertIdentity(JdbcUser.Identity userIdentity) {
        var result = new UserIdentity();
        result.setUserId(userIdentity.getIdentityId());
        result.setUsername(userIdentity.getUsername());
        result.setProviderId(userIdentity.getProviderId());
        result.setLinkedAt(dateConverter.convertFrom(userIdentity.getLinkedAt(), null));
        result.setAdditionalInformation(mapToStringConverter.convertFrom(userIdentity.getAdditionalInformation(), null));
        return result;
    }

}
