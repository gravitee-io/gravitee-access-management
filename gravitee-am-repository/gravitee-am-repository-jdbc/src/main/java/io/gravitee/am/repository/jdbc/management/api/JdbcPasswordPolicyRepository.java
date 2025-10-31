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

import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.jdbc.management.AbstractJdbcRepository;
import io.gravitee.am.repository.jdbc.management.api.model.JdbcPasswordPolicy;
import io.gravitee.am.repository.jdbc.management.api.spring.SpringPasswordPolicyRepository;
import io.gravitee.am.repository.management.api.PasswordPolicyRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;

import static io.gravitee.am.common.utils.RandomString.generate;
import static org.springframework.data.relational.core.query.Criteria.where;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToCompletable;
import static reactor.adapter.rxjava.RxJava3Adapter.monoToSingle;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public class JdbcPasswordPolicyRepository extends AbstractJdbcRepository implements PasswordPolicyRepository, InitializingBean {

    private static final String ID = "id";
    private static final String REFERENCE_ID = "reference_id";
    private static final String REFERENCE_TYPE = "reference_type";
    private static final String CREATED_AT = "created_at";
    private static final String UPDATED_AT = "updated_at";
    private static final String MIN_LENGTH = "min_length";
    private static final String MAX_LENGTH = "max_length";
    private static final String INCL_NUMBERS = "incl_numbers";
    private static final String INCL_SPECIAL_CHARS = "incl_special_chars";
    private static final String LETTERS_MIXED_CASE = "letters_mixed_case";
    private static final String MAX_CONSECUTIVE_LETTER = "max_consecutive_letters";
    private static final String EXCLUDE_PWD_IN_DICTIONARY = "exclude_pwd_in_dict";
    private static final String EXCLUDE_USER_INFO_IN_PWD = "exclude_user_info_in_pwd";
    private static final String EXPIRY_DURATION = "expiry_duration";
    private static final String PWD_HISTORY_ENABLED = "password_history_enabled";
    private static final String OLD_PASSWORD = "old_passwords";
    private static final String DEFAULT_POLICY = "default_policy";

    private String insertStatement;
    private String updateStatement;

    private static final List<String> columns = java.util.List.of(
            ID,
            REFERENCE_TYPE,
            REFERENCE_ID,
            CREATED_AT,
            UPDATED_AT,
            MIN_LENGTH,
            MAX_LENGTH,
            INCL_NUMBERS,
            INCL_SPECIAL_CHARS,
            LETTERS_MIXED_CASE,
            MAX_CONSECUTIVE_LETTER,
            EXCLUDE_PWD_IN_DICTIONARY,
            EXCLUDE_USER_INFO_IN_PWD,
            EXPIRY_DURATION,
            PWD_HISTORY_ENABLED,
            OLD_PASSWORD,
            DEFAULT_POLICY
    );

    @Autowired
    private SpringPasswordPolicyRepository passwordPolicyRepository;

    @Override
    public void afterPropertiesSet() throws Exception {
        insertStatement = createInsertStatement("password_histories", columns);
        updateStatement = createUpdateStatement("password_histories", columns, List.of(ID));
    }

    @Override
    public Maybe<PasswordPolicy> findById(String id) {
        LOGGER.debug("Find password policy with id {}", id);
        return passwordPolicyRepository.findById(id).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<PasswordPolicy> create(PasswordPolicy item) {
        item.setId(item.getId() == null ? generate() : item.getId());
        LOGGER.debug("Create password policy with id {}", item.getId());

        return monoToSingle(getTemplate().insert(toJdbcEntity(item))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<PasswordPolicy> update(PasswordPolicy item) {
        LOGGER.debug("Update password policy with id {}", item.getId());
        return monoToSingle(getTemplate().update(toJdbcEntity(item))).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        LOGGER.debug("delete password policy with id {}", id);
        return passwordPolicyRepository.deleteById(id)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<PasswordPolicy> findByReference(ReferenceType referenceType, String referenceId) {
        return passwordPolicyRepository.findByReference(referenceId, referenceType.name()).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<PasswordPolicy> findByReferenceAndId(ReferenceType referenceType, String referenceId, String id) {
        return passwordPolicyRepository.findByReference(referenceId, referenceType.name(), id).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<PasswordPolicy> findByDefaultPolicy(ReferenceType referenceType, String referenceId) {
        return passwordPolicyRepository.findByDefaultPolicy(referenceId, referenceType.name(), Boolean.TRUE).map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("delete password policy by reference {} / {}", referenceType.name(), referenceId);
        return monoToCompletable(getTemplate().delete(JdbcPasswordPolicy.class)
                .matching(Query.query(where(REFERENCE_ID).is(referenceId).and(where(REFERENCE_TYPE).is(referenceType.name()))))
                .all())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<PasswordPolicy> findByOldest(ReferenceType referenceType, String referenceId) {
        return passwordPolicyRepository.findByReference(referenceId, referenceType.name())
                .sorted(Comparator.comparing(JdbcPasswordPolicy::getCreatedAt))
                .firstElement()
                .map(this::toEntity)
                .observeOn(Schedulers.computation());
    }

    protected PasswordPolicy toEntity(JdbcPasswordPolicy entity) {
        return mapper.map(entity, PasswordPolicy.class);
    }

    protected JdbcPasswordPolicy toJdbcEntity(PasswordPolicy entity) {
        return mapper.map(entity, JdbcPasswordPolicy.class);
    }

}
