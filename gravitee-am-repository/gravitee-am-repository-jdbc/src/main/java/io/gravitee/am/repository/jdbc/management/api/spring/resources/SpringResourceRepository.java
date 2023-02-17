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
package io.gravitee.am.repository.jdbc.management.api.spring.resources;

import io.gravitee.am.repository.jdbc.management.api.model.JdbcResource;
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
public interface SpringResourceRepository extends RxJava3CrudRepository<JdbcResource, String> {

    @Query("select count(u.id) from uma_resource_set u where u.domain = :domain")
    Single<Long> countByDomain(@Param("domain") String domain);

    @Query("select * from uma_resource_set u where u.id in (:ids)")
    Flowable<JdbcResource> findByIdIn(@Param("ids") List<String> resources);

    @Query("select * from uma_resource_set u where u.domain = :domain and u.client_id = :client and u.user_id = :uid")
    Flowable<JdbcResource> findByDomainAndClientAndUser(@Param("domain") String domain, @Param("client") String client,
                                                        @Param("uid") String user);

    @Query("select * from uma_resource_set u where u.domain = :domain and u.client_id = :client and u.id in (:ids)")
    Flowable<JdbcResource> findByDomainAndClientAndResources(@Param("domain") String domain, @Param("client") String client,
                                                             @Param("ids") List<String> resources);

    @Query("select * from uma_resource_set u where u.domain = :domain and u.client_id = :client and u.user_id = :uid and u.id = :rid")
    Maybe<JdbcResource> findByDomainAndClientAndUserIdAndResource(@Param("domain") String domain, @Param("client") String client,
                                                                  @Param("uid") String user, @Param("rid") String resource);
}
