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
package io.gravitee.am.repository.jdbc.management.api.spring.group;

import io.gravitee.am.repository.jdbc.management.api.model.JdbcGroup;
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
public interface SpringGroupRepository extends RxJava2CrudRepository<JdbcGroup, String> {
    @Query("SELECT * FROM groups g INNER JOIN group_members m ON g.id = m.group_id where m.member = :mid")
    Flowable<JdbcGroup> findAllByMember(@Param("mid")String member);

    @Query("SELECT * FROM groups g where g.reference_id = :refId and g.reference_type = :refType")
    Flowable<JdbcGroup> findAllByReference(@Param("refType")String referenceType, @Param("refId")String referenceId);

    @Query("SELECT count(*) FROM groups g where g.reference_id = :refId and g.reference_type = :refType")
    Single<Long> countByReference(@Param("refType")String referenceType, @Param("refId")String referenceId);

    @Query("SELECT * FROM groups g where g.id in (:ids)")
    Flowable<JdbcGroup> findByIdIn(@Param("ids") List<String> ids);

    @Query("SELECT * FROM groups g where g.reference_id = :refId and g.reference_type = :refType and name = :name")
    Maybe<JdbcGroup> findByName(@Param("refType")String referenceType, @Param("refId")String referenceId, @Param("name") String name);

    @Query("SELECT * FROM groups g where g.reference_id = :refId and g.reference_type = :refType and id = :id")
    Maybe<JdbcGroup> findById(@Param("refType")String referenceType, @Param("refId")String referenceId, @Param("id") String id);
}
