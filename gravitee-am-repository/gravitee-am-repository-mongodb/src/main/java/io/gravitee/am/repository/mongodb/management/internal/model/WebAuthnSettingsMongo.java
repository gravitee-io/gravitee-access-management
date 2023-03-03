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
package io.gravitee.am.repository.mongodb.management.internal.model;

import org.bson.Document;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnSettingsMongo {

    private String origin;
    private String relyingPartyId;
    private String relyingPartyName;
    private boolean requireResidentKey;
    private String userVerification;
    private String authenticatorAttachment;
    private String attestationConveyancePreference;
    private boolean forceRegistration;
    private Document certificates;
    private boolean enforceAuthenticatorIntegrity;
    private Integer enforceAuthenticatorIntegrityMaxAge;

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getRelyingPartyId() {
        return relyingPartyId;
    }

    public void setRelyingPartyId(String relyingPartyId) {
        this.relyingPartyId = relyingPartyId;
    }

    public String getRelyingPartyName() {
        return relyingPartyName;
    }

    public void setRelyingPartyName(String relyingPartyName) {
        this.relyingPartyName = relyingPartyName;
    }

    public boolean isRequireResidentKey() {
        return requireResidentKey;
    }

    public void setRequireResidentKey(boolean requireResidentKey) {
        this.requireResidentKey = requireResidentKey;
    }

    public String getUserVerification() {
        return userVerification;
    }

    public void setUserVerification(String userVerification) {
        this.userVerification = userVerification;
    }

    public String getAuthenticatorAttachment() {
        return authenticatorAttachment;
    }

    public void setAuthenticatorAttachment(String authenticatorAttachment) {
        this.authenticatorAttachment = authenticatorAttachment;
    }

    public String getAttestationConveyancePreference() {
        return attestationConveyancePreference;
    }

    public void setAttestationConveyancePreference(String attestationConveyancePreference) {
        this.attestationConveyancePreference = attestationConveyancePreference;
    }

    public boolean isForceRegistration() {
        return forceRegistration;
    }

    public void setForceRegistration(boolean forceRegistration) {
        this.forceRegistration = forceRegistration;
    }

    public Document getCertificates() {
        return certificates;
    }

    public void setCertificates(Document certificates) {
        this.certificates = certificates;
    }

    public boolean isEnforceAuthenticatorIntegrity() {
        return enforceAuthenticatorIntegrity;
    }

    public void setEnforceAuthenticatorIntegrity(boolean enforceAuthenticatorIntegrity) {
        this.enforceAuthenticatorIntegrity = enforceAuthenticatorIntegrity;
    }

    public Integer getEnforceAuthenticatorIntegrityMaxAge() {
        return enforceAuthenticatorIntegrityMaxAge;
    }

    public void setEnforceAuthenticatorIntegrityMaxAge(Integer enforceAuthenticatorIntegrityMaxAge) {
        this.enforceAuthenticatorIntegrityMaxAge = enforceAuthenticatorIntegrityMaxAge;
    }
}
