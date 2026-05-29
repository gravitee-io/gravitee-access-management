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
package io.gravitee.am.management.handlers.automation.mapper;

import io.gravitee.am.management.handlers.automation.model.AutomationCertificate;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.service.model.AutomationNewCertificate;
import io.gravitee.am.service.model.UpdateCertificate;

/**
 * Maps between the shared {@link Certificate} model and the {@link AutomationCertificate} projection.
 *
 * @author GraviteeSource Team
 */
public final class AutomationCertificateMapper {

    private AutomationCertificateMapper() {
    }

    public static AutomationCertificate toAutomationCertificate(Certificate certificate) {
        AutomationCertificate out = new AutomationCertificate();
        out.setAutomationKey(certificate.getAutomationKey());
        out.setName(certificate.getName());
        out.setType(certificate.getType());
        out.setConfiguration(certificate.getConfiguration());
        out.setSystem(certificate.isSystem());
        out.setCreatedAt(certificate.getCreatedAt());
        out.setUpdatedAt(certificate.getUpdatedAt());
        out.setExpiresAt(certificate.getExpiresAt());
        return out;
    }

    /**
     * Build the create payload for a declared certificate. The deterministic id is set by the caller
     * (the upsert path) before the certificate is persisted.
     */
    public static AutomationNewCertificate toNewCertificate(AutomationCertificate definition) {
        AutomationNewCertificate newCertificate = new AutomationNewCertificate();
        newCertificate.setAutomationKey(definition.getAutomationKey());
        newCertificate.setName(definition.getName());
        newCertificate.setType(definition.getType());
        newCertificate.setConfiguration(definition.getConfiguration());
        return newCertificate;
    }

    public static UpdateCertificate toUpdateCertificate(AutomationCertificate definition) {
        UpdateCertificate updateCertificate = new UpdateCertificate();
        updateCertificate.setName(definition.getName());
        updateCertificate.setType(definition.getType());
        updateCertificate.setConfiguration(definition.getConfiguration());
        return updateCertificate;
    }
}
