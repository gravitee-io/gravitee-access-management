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

import io.gravitee.am.repository.jdbc.management.api.model.JdbcOrganizationUser;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.RxJava3CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public interface SpringOrganizationUserRepository extends RxJava3CrudRepository<JdbcOrganizationUser, String> {

    @Query("select count(u.id) from organization_users u where u.reference_type = :refType and u.reference_id = :refId")
    Single<Long> countByReference(@Param("refType")String refType, @Param("refId") String refId);

    @Query("select * from organization_users u where u.reference_type = :refType and u.reference_id = :refId and u.id = :id")
    Maybe<JdbcOrganizationUser> findById(@Param("refType")String refType, @Param("refId") String refId, @Param("id") String id);

    @Query("select * from organization_users u where u.reference_type = :refType and u.reference_id = :refId and u.external_id = :id and u.source = :src")
    Maybe<JdbcOrganizationUser> findByExternalIdAndSource(@Param("refType")String refType, @Param("refId") String refId, @Param("id") String externalId, @Param("src") String source);

    @Query("select * from organization_users u where u.reference_type = :refType and u.reference_id = :refId and UPPER(u.username) = UPPER(:name) and u.source = :src")
    Maybe<JdbcOrganizationUser> findByUsernameAndSource(@Param("refType")String refType, @Param("refId") String refId, @Param("name") String username, @Param("src") String source);

    @Query("select * from organization_users u where u.id in (:ids)")
    Flowable<JdbcOrganizationUser> findByIdIn(@Param("ids") List<String> ids);

    @Query("select * from organization_users u where u.reference_type = :refType and u.reference_id = :refId")
    Flowable<JdbcOrganizationUser> findByReference(@Param("refType")String refType, @Param("refId") String refId);
}
