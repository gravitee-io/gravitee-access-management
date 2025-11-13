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
package io.gravitee.am.service.reporter.builder.management;

import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.model.CertificateCredential;
import io.gravitee.am.model.Reference;

/**
 * @author GraviteeSource Team
 */
public class CertificateCredentialAuditBuilder extends ManagementAuditBuilder<CertificateCredentialAuditBuilder> {

    public CertificateCredentialAuditBuilder() {
        super();
    }

    public CertificateCredentialAuditBuilder certificateCredential(CertificateCredential credential) {
        if (credential != null) {
            if (EventType.CREDENTIAL_CREATED.equals(getType()) || EventType.CREDENTIAL_UPDATED.equals(getType())) {
                setNewValue(credential);
            }
            reference(new Reference(credential.getReferenceType(), credential.getReferenceId()));
            // Use certificate thumbprint as alternative ID (similar to credentialId for WebAuthn)
            setTarget(credential.getId(), EntityType.CREDENTIAL, credential.getCertificateThumbprint(), credential.getCertificateSubjectDN(), credential.getReferenceType(), credential.getReferenceId());
        }
        return this;
    }
}

