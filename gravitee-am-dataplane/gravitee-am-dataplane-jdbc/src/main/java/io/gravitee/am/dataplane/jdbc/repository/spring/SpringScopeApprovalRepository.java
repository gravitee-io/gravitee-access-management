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
package io.gravitee.am.dataplane.jdbc.repository.spring;

import io.gravitee.am.dataplane.jdbc.repository.model.JdbcScopeApproval;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.RxJava3CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Repository
public interface SpringScopeApprovalRepository extends RxJava3CrudRepository<JdbcScopeApproval, String> {

    @Query("Select * from scope_approvals s where domain = :domain and user_id = :user and client_id = :client")
    Flowable<JdbcScopeApproval> findByDomainAndUserAndClient(@Param("domain") String domain, @Param("user") String user,
                                                             @Param("client") String client);

    @Query("Select * from scope_approvals s where domain = :domain and user_id = :user")
    Flowable<JdbcScopeApproval> findByDomainAndUser(@Param("domain") String domain, @Param("user") String user);

    @Query("Select * from scope_approvals s where domain = :domain and user_id = :user and client_id = :client and scope = :scope")
    Maybe<JdbcScopeApproval> findByDomainAndUserAndClientAndScope(@Param("domain") String domain, @Param("user") String user,
                                                                  @Param("client") String client, @Param("scope") String scope);


}
