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

import io.gravitee.am.dataplane.jdbc.repository.model.JdbcCertificateCredential;
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
public interface SpringCertificateCredentialRepository extends RxJava3CrudRepository<JdbcCertificateCredential, String> {
    @Query("Select * from cert_credentials c where c.reference_id = :refId and c.reference_type = :refType and user_id = :userId")
    Flowable<JdbcCertificateCredential> findByUserId(@Param("refType") String referenceType, @Param("refId") String referenceId, @Param("userId") String userId);

    @Query("Select * from cert_credentials c where c.reference_id = :refId and c.reference_type = :refType and certificate_thumbprint = :thumbprint")
    Flowable<JdbcCertificateCredential> findByThumbprint(@Param("refType") String referenceType, @Param("refId") String referenceId, @Param("thumbprint") String thumbprint);

    @Query("""
            SELECT * FROM cert_credentials c WHERE 
                 c.reference_id = :refId AND
                 c.reference_type = :refType AND
                 c.certificate_subject_dn = :subjectDN AND
                 c.certificate_issuer_dn = :issuerDN AND
                 c.certificate_serial_number = :serialNumber
            """)
    Maybe<JdbcCertificateCredential> findBySubjectAndIssuerAndSerialNumber(@Param("refType") String referenceType,
                                                                           @Param("refId") String referenceId,
                                                                           @Param("subjectDN") String subjectDN,
                                                                           @Param("issuerDN") String issuerDN,
                                                                           @Param("serialNumber") String serialNumber);

    @Query("Select * from cert_credentials c where c.reference_id = :refId and c.reference_type = :refType and user_id = :userId and id = :id")
    Maybe<JdbcCertificateCredential> findByReferenceTypeAndReferenceIdAndUserIdAndId(@Param("refType") String referenceType, @Param("refId") String referenceId, @Param("userId") String userId, @Param("id") String id);
}

