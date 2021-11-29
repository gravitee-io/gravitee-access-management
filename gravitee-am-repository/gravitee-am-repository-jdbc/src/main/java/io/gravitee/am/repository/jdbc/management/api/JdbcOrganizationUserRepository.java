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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.repository.jdbc.common.dialect.ScimUserSearch;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcOrganizationUser;
import io.gravitee.am.repository.jdbc.management.api.spring.user.*;
import io.gravitee.am.repository.management.api.OrganizationUserRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Update;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.data.util.StreamUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Stream.concat;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.CriteriaDefinition.from;
import static reactor.adapter.rxjava.RxJava2Adapter.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcOrganizationUserRepository extends AbstractJdbcRepository implements OrganizationUserRepository {
    private static final String ATTRIBUTE_USER_FIELD_EMAIL = "email";
    private static final String ATTRIBUTE_USER_FIELD_PHOTO = "photo";
    private static final String ATTRIBUTE_USER_FIELD_IM = "im";
    private static final String ATTRIBUTE_USER_FIELD_PHONE = "phoneNumber";

    private static short CONCURRENT_FLATMAP = 1;

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
    public Flowable<User> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("findByReference({})", referenceId);
        return userRepository.findByReference(referenceType.name(), referenceId)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toFlowable());
    }

    @Override
    public Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        LOGGER.debug("findAll({}, {}, {}, {})", referenceType, referenceId, page, size);
        return fluxToFlowable(dbClient.select()
                .from(JdbcOrganizationUser.class)
                .matching(from(where("reference_id").is(referenceId)
                        .and(where("reference_type").is(referenceType.name()))))
                .orderBy(Sort.Order.asc("id"))
                .page(PageRequest.of(page, size))
                .as(JdbcOrganizationUser.class).all())
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toFlowable(), CONCURRENT_FLATMAP)
                .toList()
                .flatMap(content -> userRepository.countByReference(referenceType.name(), referenceId)
                        .map((count) -> new Page<User>(content, page, count)));
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size) {
        LOGGER.debug("search({}, {}, {}, {}, {})", referenceType, referenceId, query, page, size);

        boolean wildcardSearch = query.contains("*");
        String wildcardValue = query.replaceAll("\\*+", "%");

        String search = this.databaseDialectHelper.buildSearchUserQuery(wildcardSearch, page, size, true);
        String count = this.databaseDialectHelper.buildCountUserQuery(wildcardSearch, true);

        return fluxToFlowable(dbClient.execute(search)
                .bind("value", wildcardSearch ? wildcardValue : query)
                .bind("refId", referenceId)
                .bind("refType", referenceType.name())
                .as(JdbcOrganizationUser.class)
                .fetch().all())
                .map(this::toEntity)
                .flatMap(app -> completeUser(app).toFlowable(), CONCURRENT_FLATMAP) // single thread to keep order
                .toList()
                .flatMap(data -> monoToSingle(dbClient.execute(count)
                        .bind("value", wildcardSearch ? wildcardValue : query)
                        .bind("refId", referenceId)
                        .bind("refType", referenceType.name())
                        .as(Long.class)
                        .fetch().first())
                        .map(total -> new Page<User>(data, page, total)));
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria criteria, int page, int size) {
        LOGGER.debug("search({}, {}, {}, {}, {})", referenceType, referenceId, criteria, page, size);
        Criteria referenceClause = where("reference_id").is(referenceId).and(where("reference_type").is(referenceType.name()));

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(" FROM organization_users WHERE reference_id = :refId AND reference_type = :refType AND ");
        ScimUserSearch search = this.databaseDialectHelper.prepareScimSearchUserQuery(queryBuilder, criteria, page, size);

        // execute query
        DatabaseClient.GenericExecuteSpec executeSelect = dbClient.execute(search.getSelectQuery());
        executeSelect = executeSelect.bind("refType", referenceType.name()).bind("refId", referenceId);
        for (Map.Entry<String, Object> entry : search.getBinding().entrySet()) {
            executeSelect = executeSelect.bind(entry.getKey(), entry.getValue());
        }
        Flux<JdbcOrganizationUser> userFlux = executeSelect.as(JdbcOrganizationUser.class).fetch().all();

        // execute count to provide total in the Page
        DatabaseClient.GenericExecuteSpec executeCount = dbClient.execute(search.getCountQuery());
        executeCount = executeCount.bind("refType", referenceType.name()).bind("refId", referenceId);
        for (Map.Entry<String, Object> entry : search.getBinding().entrySet()) {
            executeCount = executeCount.bind(entry.getKey(), entry.getValue());
        }
        Mono<Long> userCount = executeCount.as(Long.class).fetch().one();

        return fluxToFlowable(userFlux)
                .map(this::toEntity)
                .flatMap(user -> completeUser(user).toFlowable())
                .toList()
                .flatMap(list -> monoToSingle(userCount).map(total -> new Page<User>(list, page, total)));
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
        DatabaseClient.GenericInsertSpec<Map<String, Object>> insertSpec = dbClient.insert().into("organization_users");

        // doesn't use the class introspection to handle json objects
        insertSpec = addQuotedField(insertSpec, "id", item.getId(), String.class);
        insertSpec = addQuotedField(insertSpec, "external_id", item.getExternalId(), String.class);
        insertSpec = addQuotedField(insertSpec, "username", item.getUsername(), String.class);
        insertSpec = addQuotedField(insertSpec, "password", item.getPassword(), String.class);
        insertSpec = addQuotedField(insertSpec, "email", item.getEmail(), String.class);
        insertSpec = addQuotedField(insertSpec, "display_name", item.getDisplayName(), String.class);
        insertSpec = addQuotedField(insertSpec, "nick_name", item.getNickName(), String.class);
        insertSpec = addQuotedField(insertSpec, "first_name", item.getFirstName(), String.class);
        insertSpec = addQuotedField(insertSpec, "last_name", item.getLastName(), String.class);
        insertSpec = addQuotedField(insertSpec, "title", item.getTitle(), String.class);
        insertSpec = addQuotedField(insertSpec, "type", item.getType(), String.class);
        insertSpec = addQuotedField(insertSpec, "preferred_language", item.getPreferredLanguage(), String.class);
        insertSpec = addQuotedField(insertSpec, "account_non_expired", item.isAccountNonExpired(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, "account_locked_at", dateConverter.convertTo(item.getAccountLockedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, "account_locked_until", dateConverter.convertTo(item.getAccountLockedUntil(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, "account_non_locked", item.isAccountNonLocked(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, "credentials_non_expired", item.isCredentialsNonExpired(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, "enabled", item.isEnabled(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, "internal", item.isInternal(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, "pre_registration", item.isPreRegistration(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, "registration_completed", item.isRegistrationCompleted(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, "newsletter", item.isNewsletter(), Boolean.class);
        insertSpec = addQuotedField(insertSpec, "registration_user_uri", item.getRegistrationUserUri(), String.class);
        insertSpec = addQuotedField(insertSpec, "registration_access_token", item.getRegistrationAccessToken(), String.class);
        insertSpec = addQuotedField(insertSpec, "reference_type", item.getReferenceType(), String.class);
        insertSpec = addQuotedField(insertSpec, "reference_id", item.getReferenceId(), String.class);
        insertSpec = addQuotedField(insertSpec, "source", item.getSource(), String.class);
        insertSpec = addQuotedField(insertSpec, "client", item.getClient(), String.class);
        insertSpec = addQuotedField(insertSpec, "logins_count", item.getLoginsCount(), Integer.class);
        insertSpec = addQuotedField(insertSpec, "logged_at", dateConverter.convertTo(item.getLoggedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, "created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        insertSpec = addQuotedField(insertSpec, "updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, "x509_certificates", item.getX509Certificates());
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, "factors", item.getFactors());
        insertSpec = databaseDialectHelper.addJsonField(insertSpec, "additional_information", item.getAdditionalInformation());

        Mono<Integer> insertAction = insertSpec.fetch().rowsUpdated();

        insertAction = persistChildEntities(insertAction, item);

        return monoToSingle(insertAction.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Single<User> update(User item) {
        LOGGER.debug("Update User with id {}", item.getId());

        TransactionalOperator trx = TransactionalOperator.create(tm);
        final DatabaseClient.GenericUpdateSpec updateSpec = dbClient.update().table("organization_users");
        // doesn't use the class introspection to handle json objects
        Map<SqlIdentifier, Object> updateFields = new HashMap<>();
        updateFields = addQuotedField(updateFields, "id", item.getId(), String.class);
        updateFields = addQuotedField(updateFields, "external_id", item.getExternalId(), String.class);
        updateFields = addQuotedField(updateFields, "username", item.getUsername(), String.class);
        updateFields = addQuotedField(updateFields, "password", item.getPassword(), String.class);
        updateFields = addQuotedField(updateFields, "email", item.getEmail(), String.class);
        updateFields = addQuotedField(updateFields, "display_name", item.getDisplayName(), String.class);
        updateFields = addQuotedField(updateFields, "nick_name", item.getNickName(), String.class);
        updateFields = addQuotedField(updateFields, "first_name", item.getFirstName(), String.class);
        updateFields = addQuotedField(updateFields, "last_name", item.getLastName(), String.class);
        updateFields = addQuotedField(updateFields, "title", item.getTitle(), String.class);
        updateFields = addQuotedField(updateFields, "type", item.getType(), String.class);
        updateFields = addQuotedField(updateFields, "preferred_language", item.getPreferredLanguage(), String.class);
        updateFields = addQuotedField(updateFields, "account_non_expired", item.isAccountNonExpired(), Boolean.class);
        updateFields = addQuotedField(updateFields, "account_locked_at", dateConverter.convertTo(item.getAccountLockedAt(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields, "account_locked_until", dateConverter.convertTo(item.getAccountLockedUntil(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields, "account_non_locked", item.isAccountNonLocked(), Boolean.class);
        updateFields = addQuotedField(updateFields, "credentials_non_expired", item.isCredentialsNonExpired(), Boolean.class);
        updateFields = addQuotedField(updateFields, "enabled", item.isEnabled(), Boolean.class);
        updateFields = addQuotedField(updateFields, "internal", item.isInternal(), Boolean.class);
        updateFields = addQuotedField(updateFields, "pre_registration", item.isPreRegistration(), Boolean.class);
        updateFields = addQuotedField(updateFields, "registration_completed", item.isRegistrationCompleted(), Boolean.class);
        updateFields = addQuotedField(updateFields, "newsletter", item.isNewsletter(), Boolean.class);
        updateFields = addQuotedField(updateFields, "registration_user_uri", item.getRegistrationUserUri(), String.class);
        updateFields = addQuotedField(updateFields, "registration_access_token", item.getRegistrationAccessToken(), String.class);
        updateFields = addQuotedField(updateFields, "reference_type", item.getReferenceType(), String.class);
        updateFields = addQuotedField(updateFields, "reference_id", item.getReferenceId(), String.class);
        updateFields = addQuotedField(updateFields, "source", item.getSource(), String.class);
        updateFields = addQuotedField(updateFields, "client", item.getClient(), String.class);
        updateFields = addQuotedField(updateFields, "logins_count", item.getLoginsCount(), Integer.class);
        updateFields = addQuotedField(updateFields, "logged_at", dateConverter.convertTo(item.getLoggedAt(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields, "created_at", dateConverter.convertTo(item.getCreatedAt(), null), LocalDateTime.class);
        updateFields = addQuotedField(updateFields, "updated_at", dateConverter.convertTo(item.getUpdatedAt(), null), LocalDateTime.class);
        updateFields = databaseDialectHelper.addJsonField(updateFields, "x509_certificates", item.getX509Certificates());
        updateFields = databaseDialectHelper.addJsonField(updateFields, "factors", item.getFactors());
        updateFields = databaseDialectHelper.addJsonField(updateFields, "additional_information", item.getAdditionalInformation());

        Mono<Integer> updateAction = updateSpec.using(Update.from(updateFields)).matching(from(where("id").is(item.getId()))).fetch().rowsUpdated();

        updateAction = deleteChildEntities(item.getId()).then(updateAction);
        updateAction = persistChildEntities(updateAction, item);

        return monoToSingle(updateAction.as(trx::transactional))
                .flatMap((i) -> this.findById(item.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete({})", id);
        TransactionalOperator trx = TransactionalOperator.create(tm);
        Mono<Integer> delete = dbClient.delete().from(JdbcOrganizationUser.class).matching(from(where("id").is(id))).fetch().rowsUpdated();

        return monoToCompletable(delete.then(deleteChildEntities(id)).as(trx::transactional));
    }

    private Mono<Integer> persistChildEntities(Mono<Integer> actionFlow, User item) {
        final List<Address> addresses = item.getAddresses();
        if (addresses != null && !addresses.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(addresses).concatMap(address -> {
                JdbcOrganizationUser.Address jdbcAddr = mapper.map(address, JdbcOrganizationUser.Address.class);
                jdbcAddr.setUserId(item.getId());
                return dbClient.insert().into(JdbcOrganizationUser.Address.class).using(jdbcAddr).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        actionFlow = addJdbcRoles(actionFlow, item, item.getRoles(), JdbcOrganizationUser.Role.class);
        actionFlow = addJdbcRoles(actionFlow, item, item.getDynamicRoles(), JdbcOrganizationUser.DynamicRole.class);

        final List<String> entitlements = item.getEntitlements();
        if (entitlements != null && !entitlements.isEmpty()) {
            actionFlow = actionFlow.then(Flux.fromIterable(entitlements).concatMap(entitlement -> {
                JdbcOrganizationUser.Entitlements jdbcEntitlement = new JdbcOrganizationUser.Entitlements();
                jdbcEntitlement.setUserId(item.getId());
                jdbcEntitlement.setEntitlement(entitlement);
                return dbClient.insert().into(JdbcOrganizationUser.Entitlements.class).using(jdbcEntitlement).fetch().rowsUpdated();
            }).reduce(Integer::sum));
        }

        Optional<Mono<Integer>> attributes = concat(concat(concat(convertAttributes(item, item.getEmails(), ATTRIBUTE_USER_FIELD_EMAIL),
                                convertAttributes(item, item.getPhoneNumbers(), ATTRIBUTE_USER_FIELD_PHONE)),
                        convertAttributes(item, item.getIms(), ATTRIBUTE_USER_FIELD_IM)),
                convertAttributes(item, item.getPhotos(), ATTRIBUTE_USER_FIELD_PHOTO))
                .map(jdbcAttr -> dbClient.insert().into(JdbcOrganizationUser.Attribute.class).using(jdbcAttr).fetch().rowsUpdated())
                .reduce(Mono::then);
        if (attributes.isPresent()) {
            actionFlow = actionFlow.then(attributes.get());
        }

        return actionFlow;
    }

    private <T extends JdbcOrganizationUser.AbstractRole> Mono<Integer> addJdbcRoles(
            Mono<Integer> actionFlow, User item, List<String> roles, Class<T> clazz) {
        if (roles != null && !roles.isEmpty()) {
            return actionFlow.then(Flux.fromIterable(roles).concatMap(role -> {
                try {
                    T jdbcRole = clazz.getConstructor().newInstance();
                    jdbcRole.setUserId(item.getId());
                    jdbcRole.setRole(role);
                    return dbClient.insert().into(clazz).using(jdbcRole).fetch().rowsUpdated();
                } catch (Exception e) {
                    LOGGER.error("An unexpected error has occurred", e);
                    return Mono.just(0);
                }
            }).reduce(Integer::sum));
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

    private Mono<Integer> deleteChildEntities(String userId) {
        Mono<Integer> deleteRoles = dbClient.delete().from(JdbcOrganizationUser.Role.class).matching(from(where("user_id").is(userId))).fetch().rowsUpdated();
        Mono<Integer> deleteRolesDynamicRoles = dbClient.delete().from(JdbcOrganizationUser.DynamicRole.class).matching(from(where("user_id").is(userId))).fetch().rowsUpdated();
        Mono<Integer> deleteAddresses = dbClient.delete().from(JdbcOrganizationUser.Address.class).matching(from(where("user_id").is(userId))).fetch().rowsUpdated();
        Mono<Integer> deleteAttributes = dbClient.delete().from(JdbcOrganizationUser.Attribute.class).matching(from(where("user_id").is(userId))).fetch().rowsUpdated();
        Mono<Integer> deleteEntitlements = dbClient.delete().from(JdbcOrganizationUser.Entitlements.class).matching(from(where("user_id").is(userId))).fetch().rowsUpdated();
        return deleteRoles.then(deleteRolesDynamicRoles).then(deleteAddresses).then(deleteAttributes).then(deleteEntitlements);
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
