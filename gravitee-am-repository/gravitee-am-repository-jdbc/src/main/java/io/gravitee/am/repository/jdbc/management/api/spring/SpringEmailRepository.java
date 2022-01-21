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

import io.gravitee.am.repository.jdbc.management.api.model.JdbcEmail;
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
public interface SpringEmailRepository extends RxJava2CrudRepository<JdbcEmail, String> {
    @Query("select * from emails e where e.reference_id = :refId and e.reference_type = :refType and id = :id")
    Maybe<JdbcEmail> findById(@Param("refId")String refId, @Param("refType") String refType, @Param("id")String id);

    @Query("select * from emails e where e.reference_id = :refId and e.reference_type = :refType")
    Flowable<JdbcEmail> findAllByReference(@Param("refId")String refId, @Param("refType") String refType);

    @Query("select * from emails e where e.reference_id = :refId and e.reference_type = :refType and " +
            "client = :cli")
    Flowable<JdbcEmail> findAllByReferenceAndClient(@Param("refId")String refId, @Param("refType") String refType,
                                                    @Param("cli")String client);

    @Query("select * from emails e where e.reference_id = :refId and e.reference_type = :refType and " +
            "client = :cli and template = :tpl")
    Maybe<JdbcEmail> findByClientAndTemplate(@Param("refId")String refId, @Param("refType") String refType,
                                                    @Param("cli")String client, @Param("tpl") String template);

    @Query("select * from emails e where e.reference_id = :refId and e.reference_type = :refType and e.template = :tpl and e.client IS NULL")
    Maybe<JdbcEmail> findByTemplate(@Param("refId")String refId, @Param("refType") String refType,
                                    @Param("tpl") String template);

}
