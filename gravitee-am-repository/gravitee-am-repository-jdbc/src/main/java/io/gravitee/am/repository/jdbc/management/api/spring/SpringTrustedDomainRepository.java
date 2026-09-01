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

import io.gravitee.am.repository.jdbc.management.api.model.JdbcTrustedDomain;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.RxJava3CrudRepository;

public interface SpringTrustedDomainRepository extends RxJava3CrudRepository<JdbcTrustedDomain, String> {

    @Query("select * from trusted_domains where reference_type = :refType and reference_id = :refId")
    Flowable<JdbcTrustedDomain> findByReference(@Param("refType") String refType, @Param("refId") String refId);

    @Query("select * from trusted_domains where reference_type = :refType and reference_id = :refId and name = :name")
    Maybe<JdbcTrustedDomain> findByName(@Param("refType") String refType,
                                        @Param("refId") String refId,
                                        @Param("name") String name);

    @Query("select * from trusted_domains where reference_type = :refType and reference_id = :refId and domain_identifier = :domainIdentifier")
    Maybe<JdbcTrustedDomain> findByDomainIdentifier(@Param("refType") String refType,
                                                    @Param("refId") String refId,
                                                    @Param("domainIdentifier") String domainIdentifier);

    @Query("""
            select td.* from trusted_domains td
            join trusted_domains_spiffe spiffe on spiffe.trusted_domain_id = td.id
            where spiffe.reference_type = :refType and spiffe.reference_id = :refId and spiffe.spiffe_trust_domain = :spiffeTrustDomain
            """)
    Maybe<JdbcTrustedDomain> findBySpiffeTrustDomain(@Param("refType") String refType,
                                                     @Param("refId") String refId,
                                                     @Param("spiffeTrustDomain") String spiffeTrustDomain);
}
