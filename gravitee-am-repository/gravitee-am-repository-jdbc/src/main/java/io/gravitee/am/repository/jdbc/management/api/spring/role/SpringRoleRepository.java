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
package io.gravitee.am.repository.jdbc.management.api.spring.role;

import io.gravitee.am.repository.jdbc.management.api.model.JdbcRole;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.RxJava2CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public interface SpringRoleRepository extends RxJava2CrudRepository<JdbcRole, String> {

    @Query("select count(r.id) from roles r where r.reference_type = :refType and r.reference_id = :refId")
    Single<Long> countByReference(@Param("refType")String refType, @Param("refId") String refId);

    @Query("select * from roles r where r.reference_type = :refType and r.reference_id = :refId")
    Flowable<JdbcRole> findByReference(@Param("refType") String refType, @Param("refId") String refId);

    @Query("select * from roles r where r.id in (:rid)")
    Flowable<JdbcRole> findByIdIn(@Param("rid") List<String> roles);

    @Query("select * from roles r where r.reference_type = :refType and r.reference_id = :refId and r.id = :rid")
    Maybe<JdbcRole> findById(@Param("refType") String refType, @Param("refId") String refId, @Param("rid")String role);

    @Query("select * from roles r where r.reference_type = :refType and r.reference_id = :refId and r.name = :name and r.assignable_type = :assignable")
    Maybe<JdbcRole> findByNameAndAssignableType(@Param("refType") String refType, @Param("refId") String refId, @Param("name")String name, @Param("assignable")String assignableType);

    @Query("select * from roles r where r.reference_type = :refType and r.reference_id = :refId and r.name in (:names) and r.assignable_type = :assignable")
    Flowable<JdbcRole> findByNamesAndAssignableType(@Param("refType") String refType, @Param("refId") String refId, @Param("names")List<String> names, @Param("assignable")String assignableType);
}
