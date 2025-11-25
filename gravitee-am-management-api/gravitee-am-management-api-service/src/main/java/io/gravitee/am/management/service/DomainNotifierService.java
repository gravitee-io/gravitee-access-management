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
package io.gravitee.am.management.service;

import io.gravitee.am.model.Certificate;
import io.reactivex.rxjava3.core.Completable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DomainNotifierService {

    void registerCertificateExpiration(Certificate certificate);

    void unregisterCertificateExpiration(String domainId, String certificateId);

<<<<<<< HEAD:gravitee-am-management-api/gravitee-am-management-api-service/src/main/java/io/gravitee/am/management/service/DomainNotifierService.java
    Completable deleteCertificateExpirationAcknowledgement(String certificateId);
=======
    /**
     * Delete all WebAuthn credentials for a user.
     *
     * @param domain the domain
     * @param userId the user ID
     * @return completable
     */
    Completable deleteByUserId(Domain domain, String userId);

>>>>>>> 4b5ae7d12 (fix: am-6085 delete WebAuthn credentials on user deletion):gravitee-am-management-api/gravitee-am-management-api-service/src/main/java/io/gravitee/am/management/service/dataplane/CredentialManagementService.java
}
