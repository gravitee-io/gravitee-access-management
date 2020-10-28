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

import io.gravitee.am.repository.jdbc.management.api.model.JdbcAccessPolicy;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.springframework.data.domain.Pageable;
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
public interface SpringAccessPolicyRepository extends RxJava2CrudRepository<JdbcAccessPolicy, String> {
    @Query("Select count(*) from uma_access_policies u where u.domain_id = :domain")
    Single<Long> countByDomain(String domain);

    @Query("Select * from uma_access_policies u where u.domain_id = :domain")
    Flowable<JdbcAccessPolicy> findByDomain(@Param("domain") String domain, Pageable page);

    @Query("Select count(*) from uma_access_policies u where u.resource = :resource")
    Single<Long> countByResource(String resource);

    @Query("Select * from uma_access_policies u where u.domain_id = :domain and u.resource = :resource")
    Flowable<JdbcAccessPolicy> findByDomainAndResource(@Param("domain") String domain, @Param("resource") String resource);

    @Query("Select * from uma_access_policies u where u.resource in (:resources)")
    Flowable<JdbcAccessPolicy> findByResourceIn(@Param("resources")List<String> resources);
}
