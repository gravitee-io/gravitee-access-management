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
package io.gravitee.am.repository.jdbc.management.api.spring.user;

import io.gravitee.am.repository.jdbc.management.api.model.JdbcUser;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.RxJava2CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public interface SpringUserRepository extends RxJava2CrudRepository<JdbcUser, String> {

    @Query("select count(u.id) from users u where u.reference_type = :refType and u.reference_id = :refId")
    Single<Long> countByReference(@Param("refType")String refType, @Param("refId") String refId);

    @Query("select count(u.id) from users u where u.reference_type = :refType and u.reference_id = :refId and u.client = :client")
    Single<Long> countByClient(@Param("refType") String refType, @Param("refId") String refId, @Param("client") String client);

    @Query("select * from users u where u.reference_type = :refType and u.reference_id = :refId and u.id = :id")
    Maybe<JdbcUser> findById(@Param("refType")String refType, @Param("refId") String refId, @Param("id") String id);

    @Query("select * from users u where u.reference_type = :refType and u.reference_id = :refId and u.external_id = :id and u.source = :src")
    Maybe<JdbcUser> findByExternalIdAndSource(@Param("refType")String refType, @Param("refId") String refId, @Param("id") String externalId, @Param("src") String source);

    @Query("select * from users u where u.reference_type = :refType and u.reference_id = :refId and UPPER(u.username) = UPPER(:name) and u.source = :src")
    Maybe<JdbcUser> findByUsernameAndSource(@Param("refType")String refType, @Param("refId") String refId, @Param("name") String username, @Param("src") String source);

    @Query("select * from users u where u.reference_type = :refType and u.reference_id = :refId and UPPER(u.username) = UPPER(:name)")
    Maybe<JdbcUser> findByUsername(@Param("refType")String refType, @Param("refId") String refId, @Param("name") String username);

    @Query("select * from users u where u.id in (:ids)")
    Flowable<JdbcUser> findByIdIn(@Param("ids") List<String> ids);

    @Query("select * from users u where u.reference_type = :refType and u.reference_id = :refId")
    Flowable<JdbcUser> findByReference(@Param("refType")String refType, @Param("refId") String refId);

    @Query("select count(u.id) from users u where u.reference_type = :refType and u.reference_id = :refId and u.account_non_locked = :nl and (u.account_locked_until > :lockedUntil or u.account_locked_until is null)")
    Single<Long> countLockedUser(@Param("refType")String refType, @Param("refId") String refId, @Param("nl") boolean notLocked, @Param("lockedUntil")LocalDateTime lockedUntil);

    @Query("select count(u.id) from users u where u.reference_type = :refType and u.reference_id = :refId and u.client = :client and u.account_non_locked = :nl and (u.account_locked_until > :lockedUntil or u.account_locked_until is null)")
    Single<Long> countLockedUserByClient(@Param("refType") String refType, @Param("refId") String refId, @Param("client") String client, @Param("nl") boolean notLocked, @Param("lockedUntil") LocalDateTime lockedUntil);

    @Query("select count(u.id) from users u where u.reference_type = :refType and u.reference_id = :refId and u.enabled = :en")
    Single<Long> countDisabledUser(@Param("refType")String refType, @Param("refId") String refId, @Param("en") boolean enabled);

    @Query("select count(u.id) from users u where u.reference_type = :refType and u.reference_id = :refId and u.client = :client and u.enabled = :en")
    Single<Long> countDisabledUserByClient(@Param("refType") String refType, @Param("refId") String refId, @Param("client") String client, @Param("en") boolean enabled);

    @Query("select count(u.id) from users u where u.reference_type = :refType and u.reference_id = :refId and u.logged_at < :threshold")
    Single<Long> countInactiveUser(@Param("refType")String refType, @Param("refId") String refId, @Param("threshold")LocalDateTime threshold);

    @Query("select count(u.id) from users u where u.reference_type = :refType and u.reference_id = :refId and u.client = :client and u.logged_at < :threshold")
    Single<Long> countInactiveUserByClient(@Param("refType") String refType, @Param("refId") String refId, @Param("client") String client, @Param("threshold") LocalDateTime threshold);

    @Query("select count(u.id) from users u where u.reference_type = :refType and u.reference_id = :refId and u.pre_registration = :pre")
    Single<Long> countPreRegisteredUser(@Param("refType")String refType, @Param("refId") String refId, @Param("pre") boolean preRegister);

    @Query("select count(u.id) from users u where u.reference_type = :refType and u.reference_id = :refId and u.pre_registration = :pre and u.registration_completed = :compl")
    Single<Long> countRegistrationCompletedUser(@Param("refType")String refType, @Param("refId") String refId, @Param("pre") boolean preRegister, @Param("compl") boolean completed);
}
