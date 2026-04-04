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

import io.gravitee.am.common.saml2.Binding;
import io.gravitee.am.model.oidc.Client;
import java.util.ArrayList;
import java.util.List;

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
    /**
     * SP requires that the SAML Response must be signed
     */
    private boolean wantResponseSigned = true;
    /**
     * SP requires that the SAML Assertions must be signed
     */
    private boolean wantAssertionsSigned;
    /**
     * SP requires that the SAML Assertions must be encrypted
     */
    private boolean wantAssertionsEncrypted;

    /**
     * Key transport encryption algorithm for encrypted assertions (XML Encryption {@code EncryptedKey}), e.g. RSA-OAEP URI.
     */
    private String keyTransportEncryptionAlgorithm;
    /**
     * Data encryption algorithm for encrypted assertions (XML Encryption {@code EncryptedData}), e.g. AES-256-CBC URI.
     */
    private String dataEncryptionAlgorithm;

    /**
     * SP preferred response binding
     */
    private String responseBinding = Binding.INITIAL_REQUEST;

    /**
     * EL expression resolved to produce the {@code <NameID>} value in the SAML response.
     * When null/empty the default behavior applies: the internal user ID is used, or the
     * user's email when the SP requests EMAIL format.
     */
    private String nameIdMapping;

    /**
     * Custom SAML assertion attribute mappings. When non-empty, replaces the default fixed
     * attribute set. Each entry maps a SAML attribute name to an EL expression.
     */
    private List<SAMLAssertionAttribute> assertionAttributes;

    public ApplicationSAMLSettings() {
    }

    public ApplicationSAMLSettings(ApplicationSAMLSettings other) {
        this.entityId = other.entityId;
        this.attributeConsumeServiceUrl = other.attributeConsumeServiceUrl;
        this.singleLogoutServiceUrl = other.singleLogoutServiceUrl;
        this.certificate = other.certificate;
        this.wantResponseSigned = other.wantResponseSigned;
        this.wantAssertionsSigned = other.wantAssertionsSigned;
        this.wantAssertionsEncrypted = other.wantAssertionsEncrypted;
        this.keyTransportEncryptionAlgorithm = other.keyTransportEncryptionAlgorithm;
        this.dataEncryptionAlgorithm = other.dataEncryptionAlgorithm;
        this.responseBinding = other.responseBinding;
        this.nameIdMapping = other.nameIdMapping;
        this.assertionAttributes = other.assertionAttributes != null ? new ArrayList<>(other.assertionAttributes) : null;
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

    public boolean isWantResponseSigned() {
        return wantResponseSigned;
    }

    public void setWantResponseSigned(boolean wantResponseSigned) {
        this.wantResponseSigned = wantResponseSigned;
    }

    public boolean isWantAssertionsSigned() {
        return wantAssertionsSigned;
    }

    public void setWantAssertionsSigned(boolean wantAssertionsSigned) {
        this.wantAssertionsSigned = wantAssertionsSigned;
    }

    public boolean isWantAssertionsEncrypted() {
        return wantAssertionsEncrypted;
    }

    public void setWantAssertionsEncrypted(boolean wantAssertionsEncrypted) {
        this.wantAssertionsEncrypted = wantAssertionsEncrypted;
    }

    public String getKeyTransportEncryptionAlgorithm() {
        return keyTransportEncryptionAlgorithm;
    }

    public void setKeyTransportEncryptionAlgorithm(String keyTransportEncryptionAlgorithm) {
        this.keyTransportEncryptionAlgorithm = keyTransportEncryptionAlgorithm;
    }

    public String getDataEncryptionAlgorithm() {
        return dataEncryptionAlgorithm;
    }

    public void setDataEncryptionAlgorithm(String dataEncryptionAlgorithm) {
        this.dataEncryptionAlgorithm = dataEncryptionAlgorithm;
    }

    public String getResponseBinding() {
        return responseBinding;
    }

    public void setResponseBinding(String responseBinding) {
        this.responseBinding = responseBinding;
    }

    public String getNameIdMapping() {
        return nameIdMapping;
    }

    public void setNameIdMapping(String nameIdMapping) {
        this.nameIdMapping = nameIdMapping;
    }

    public List<SAMLAssertionAttribute> getAssertionAttributes() {
        return assertionAttributes;
    }

    public void setAssertionAttributes(List<SAMLAssertionAttribute> assertionAttributes) {
        this.assertionAttributes = assertionAttributes;
    }

    public void copyTo(Client client) {
        client.setEntityId(this.entityId);
        client.setAttributeConsumeServiceUrl(this.attributeConsumeServiceUrl);
        client.setSingleLogoutServiceUrl(singleLogoutServiceUrl);
        client.setSamlCertificate(this.certificate);
        client.setWantResponseSigned(this.wantResponseSigned);
        client.setWantAssertionsSigned(this.wantAssertionsSigned);
        client.setWantAssertionsEncrypted(this.wantAssertionsEncrypted);
        client.setKeyTransportEncryptionAlgorithm(this.keyTransportEncryptionAlgorithm);
        client.setDataEncryptionAlgorithm(this.dataEncryptionAlgorithm);
        client.setResponseBinding(this.responseBinding);
        client.setNameIdMapping(this.nameIdMapping);
        client.setAssertionAttributes(this.assertionAttributes);
    }
}
