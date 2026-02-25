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

import io.gravitee.am.repository.jdbc.management.api.model.JdbcAuthorizationPolicyVersion;
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
public interface SpringAuthorizationPolicyVersionRepository extends RxJava3CrudRepository<JdbcAuthorizationPolicyVersion, String> {
    @Query("select * from authorization_policy_versions where policy_id = :policyId order by version desc")
    Flowable<JdbcAuthorizationPolicyVersion> findByPolicyId(@Param("policyId") String policyId);

    @Query("select * from authorization_policy_versions where policy_id = :policyId and version = :version")
    Maybe<JdbcAuthorizationPolicyVersion> findByPolicyIdAndVersion(@Param("policyId") String policyId, @Param("version") int version);
}
