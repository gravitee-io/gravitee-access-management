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
package io.gravitee.am.repository.jdbc.management.api.spring.application;

import io.gravitee.am.repository.jdbc.management.api.model.JdbcApplication;
import io.reactivex.Flowable;
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
public interface SpringApplicationRepository extends RxJava2CrudRepository<JdbcApplication, String> {
    @Query("select count(a.id) from applications a where a.domain = :domain")
    Single<Long> countByDomain(@Param("domain") String domain);

    @Query("select * from applications a where a.domain = :domain")
    Flowable<JdbcApplication> findByDomain(@Param("domain") String domain);

    @Query("select * from applications a where a.certificate = :cert")
    Flowable<JdbcApplication> findByCertificate(@Param("cert") String certificate);

    @Query("SELECT a.* FROM applications a INNER JOIN application_factors f ON a.id = f.application_id where f.factor = :factor")
    Flowable<JdbcApplication> findAllByFactor(@Param("factor")String factor);

    @Query("SELECT a.* FROM applications a INNER JOIN application_grants g ON a.id = g.application_id where g.grant_type = :grant and a.domain = :domain")
    Flowable<JdbcApplication> findAllByDomainAndGrant(@Param("domain")String domain, @Param("grant")String grant);

    @Query("select * from applications a where a.id in ( :ids )")
    Flowable<JdbcApplication> findByIdIn(@Param("ids") List<String> ids);


}
