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
package io.gravitee.am.model.application;

import io.gravitee.am.model.oidc.Client;

/**
 * See <a href="https://www.oasis-open.org/committees/download.php/56786/sstc-saml-metadata-errata-2.0-wd-05-diff.pdf">2.4.4 Element <SPSSODescriptor></a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationSAMLSettings {

    /**
     * Entity ID — a URL or URN that uniquely identifies the SP
     */
    private String entityId;
    /**
     * Attribute Consume Service URL — the SP endpoint where the IdP should direct SAML responses
     */
    private String attributeConsumeServiceUrl;
    /**
     * Single Logout Service URL — the SP endpoint where the IdP should redirect to after performing single logout
     */
    private String singleLogoutServiceUrl;

    /**
     * X.509 Public Key Certificate — the SP's base-64 encoded public key certificate, which is used by the IdP to verify SAML authorization requests
     */
    private String certificate;

    public ApplicationSAMLSettings() {
    }

    public ApplicationSAMLSettings(ApplicationSAMLSettings other) {
        this.entityId = other.entityId;
        this.attributeConsumeServiceUrl = other.attributeConsumeServiceUrl;
        this.singleLogoutServiceUrl = other.singleLogoutServiceUrl;
        this.certificate = other.certificate;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getAttributeConsumeServiceUrl() {
        return attributeConsumeServiceUrl;
    }

    public void setAttributeConsumeServiceUrl(String attributeConsumeServiceUrl) {
        this.attributeConsumeServiceUrl = attributeConsumeServiceUrl;
    }

    public String getSingleLogoutServiceUrl() {
        return singleLogoutServiceUrl;
    }

    public void setSingleLogoutServiceUrl(String singleLogoutServiceUrl) {
        this.singleLogoutServiceUrl = singleLogoutServiceUrl;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public void copyTo(Client client) {
        client.setEntityId(this.entityId);
        client.setAttributeConsumeServiceUrl(this.attributeConsumeServiceUrl);
        client.setSingleLogoutServiceUrl(singleLogoutServiceUrl);
        client.setSamlCertificate(this.certificate);
    }
}
