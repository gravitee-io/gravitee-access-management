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

import io.gravitee.am.repository.jdbc.management.api.model.JdbcAuthorizationEngine;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.RxJava3CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * @author GraviteeSource Team
 */
@Repository
public interface SpringAuthorizationEngineRepository extends RxJava3CrudRepository<JdbcAuthorizationEngine, String> {
    @Query("select * from authorization_engines a where a.reference_type = :refType and a.reference_id = :refId")
    Flowable<JdbcAuthorizationEngine> findAll(@Param("refType") String referenceType, @Param("refId") String referenceId);

    @Query("select * from authorization_engines a where a.reference_type = :refType and a.reference_id = :refId and a.id = :id")
    Maybe<JdbcAuthorizationEngine> findById(@Param("refType") String referenceType, @Param("refId") String referenceId, @Param("id") String id);

    @Query("select * from authorization_engines a where a.reference_type = :refType and a.reference_id = :refId and a.type = :type")
    Maybe<JdbcAuthorizationEngine> findByType(@Param("refType") String referenceType, @Param("refId") String referenceId, @Param("type") String type);
}
