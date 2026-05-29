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

/**
 * See <a href="https://www.oasis-open.org/committees/download.php/56786/sstc-saml-metadata-errata-2.0-wd-05-diff.pdf">2.4.3 Element <IDPSSODescriptor></a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SAMLSettings {

    /**
     * Enable or disable SAML 2.0 IdP support
     */
    private boolean enabled;
    /**
     * Entity ID — a URL or URN that uniquely identifies the IdP
     */
    private String entityId;
    /**
     * X.509 Public Key Certificate — the IdP's base-64 encoded public key certificate, which is used by the SP to verify SAML authorization responses
     */
    private String certificate;
    /**
     * Automation API key shadowing {@link #certificate}. Owned by the Automation API so it can losslessly
     * echo the human-readable certificate key even when the referenced certificate does not (yet) exist.
     * Ignored by the management API and the gateway, which read only {@link #certificate}; hidden from the
     * management OpenAPI (the Automation API surfaces it under {@code AutomationSamlSettings.certificate}).
     */
    @Schema(hidden = true)
    private String certificateKey;

    public SAMLSettings() {}

    public SAMLSettings(SAMLSettings other) {
        this.enabled = other.enabled;
        this.entityId = other.entityId;
        this.certificate = other.certificate;
        this.certificateKey = other.certificateKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public String getCertificateKey() {
        return certificateKey;
    }

    public void setCertificateKey(String certificateKey) {
        this.certificateKey = certificateKey;
    }
}
