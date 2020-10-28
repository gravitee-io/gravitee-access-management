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
package io.gravitee.am.repository.jdbc.management.api.spring;

import io.gravitee.am.repository.jdbc.management.api.model.JdbcMembership;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.RxJava2CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public interface SpringMembershipRepository extends RxJava2CrudRepository<JdbcMembership, String> {

    @Query("select * from memberships m where m.reference_id = :refId and m.reference_type = :refType")
    Flowable<JdbcMembership> findByReference(@Param("refId") String referenceId,@Param("refType") String referenceType);

    @Query("select * from memberships m where m.reference_id = :refId and m.reference_type = :refType" +
            " and m.member_id = :mid and m.member_type = :mtype")
    Maybe<JdbcMembership> findByReferenceAndMember(@Param("refId") String referenceId, @Param("refType") String referenceType,
                                                   @Param("mid") String memberId, @Param("mtype") String memeberType);
}
