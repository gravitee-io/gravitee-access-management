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

import io.gravitee.am.repository.jdbc.management.api.model.JdbcExtensionGrant;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.RxJava2CrudRepository;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface SpringExtensionGrantRepository extends RxJava2CrudRepository<JdbcExtensionGrant, String> {
    @Query("select * from extension_grants e where e.domain = :domain")
    Flowable<JdbcExtensionGrant> findByDomain(@Param("domain") String domain);

    @Query("select * from extension_grants e where e.domain = :domain and e.name = :name")
    Maybe<JdbcExtensionGrant> findByDomainAndName(@Param("domain") String domain, @Param("name") String name);
}
