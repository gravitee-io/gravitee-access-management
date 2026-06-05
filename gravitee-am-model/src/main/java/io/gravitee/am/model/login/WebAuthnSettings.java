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
package io.gravitee.am.model.login;

import io.gravitee.am.common.webauthn.AttestationConveyancePreference;
import io.gravitee.am.common.webauthn.AuthenticatorAttachment;
import io.gravitee.am.common.webauthn.UserVerification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Schema(title = "WebAuthn settings", description = "WebAuthn (FIDO2) relying-party configuration governing " +
        "passwordless and multi-factor authentication for the domain.")
public class WebAuthnSettings {

    @Schema(description = "Relying-party origin; must match the browser's window.location.origin during " +
            "registration and authentication ceremonies.",
            example = "https://auth.example.com")
    private String origin;

    @Schema(description = "Relying-party identifier: a domain string that scopes credentials to this entity. A " +
            "credential can only be used with the relying party it was registered against.",
            example = "auth.example.com")
    private String relyingPartyId;

    @Schema(description = "Human-readable relying-party name shown to users during ceremonies.",
            example = "Example Inc.")
    private String relyingPartyName;

    @Schema(description = "Whether the authenticator must create a client-side resident (discoverable) credential.",
            defaultValue = "false")
    private boolean requireResidentKey;

    @Schema(description = "Relying-party requirement regarding user verification during a ceremony. " +
            "REQUIRED enforces verification, PREFERRED requests it when available, and DISCOURAGED avoids it.",
            defaultValue = "PREFERRED")
    private UserVerification userVerification = UserVerification.PREFERRED;

    @Schema(description = "Preferred authenticator attachment. PLATFORM selects authenticators bound to the " +
            "device (such as a fingerprint reader); CROSS_PLATFORM selects roaming authenticators (such as a " +
            "security key).")
    private AuthenticatorAttachment authenticatorAttachment;

    @Schema(description = "Relying-party preference for attestation conveyance during credential creation. " +
            "NONE requests no attestation, INDIRECT allows anonymized attestation, and DIRECT requests the " +
            "authenticator's attestation statement.",
            defaultValue = "NONE")
    private AttestationConveyancePreference attestationConveyancePreference = AttestationConveyancePreference.NONE;

    @Schema(description = "Whether to reject registration of a credential already registered to a different user.",
            defaultValue = "false")
    private boolean forceRegistration;

    @Schema(description = "Trusted device-attestation X.509 certificates, keyed by name.")
    private Map<String, Object> certificates;

    @Schema(description = "Whether to periodically re-verify that registered authenticators remain valid against " +
            "the FIDO2 Metadata Service.", defaultValue = "false")
    private boolean enforceAuthenticatorIntegrity;

    @Schema(description = "Maximum elapsed time, in seconds, since an authenticator was last verified before it " +
            "is re-checked on the next passwordless login.")
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

    public UserVerification getUserVerification() {
        return userVerification;
    }

    public void setUserVerification(UserVerification userVerification) {
        this.userVerification = userVerification;
    }

    public AuthenticatorAttachment getAuthenticatorAttachment() {
        return authenticatorAttachment;
    }

    public void setAuthenticatorAttachment(AuthenticatorAttachment authenticatorAttachment) {
        this.authenticatorAttachment = authenticatorAttachment;
    }

    public AttestationConveyancePreference getAttestationConveyancePreference() {
        return attestationConveyancePreference;
    }

    public void setAttestationConveyancePreference(AttestationConveyancePreference attestationConveyancePreference) {
        this.attestationConveyancePreference = attestationConveyancePreference;
    }

    public boolean isForceRegistration() {
        return forceRegistration;
    }

    public void setForceRegistration(boolean forceRegistration) {
        this.forceRegistration = forceRegistration;
    }

    public Map<String, Object> getCertificates() {
        return certificates;
    }

    public void setCertificates(Map<String, Object> certificates) {
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
