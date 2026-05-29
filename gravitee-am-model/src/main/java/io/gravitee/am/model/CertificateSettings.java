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
package io.gravitee.am.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Certificate settings for a domain.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CertificateSettings {

    /**
     * The fallback certificate ID to use when no specific certificate is configured.
     */
    private String fallbackCertificate;

    /**
     * Automation API key shadowing {@link #fallbackCertificate}. Owned by the Automation API so it can
     * losslessly echo the human-readable certificate key even when the referenced certificate does not
     * (yet) exist. Ignored by the management API and the gateway, which read only {@link #fallbackCertificate};
     * hidden from the management OpenAPI (the Automation API surfaces it under
     * {@code AutomationCertificateSettings.fallbackCertificate}).
     */
    @Schema(hidden = true)
    private String fallbackCertificateKey;

    public CertificateSettings(CertificateSettings other) {
        this.fallbackCertificate = other.fallbackCertificate;
        this.fallbackCertificateKey = other.fallbackCertificateKey;
    }
}
