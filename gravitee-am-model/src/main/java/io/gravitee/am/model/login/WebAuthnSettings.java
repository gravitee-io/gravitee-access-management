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

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnSettings {

    /**
     * This value needs to match `window.location.origin` evaluated by
     * the User Agent during registration and authentication ceremonies.
     */
    private String origin;

    /**
     * A valid domain string that identifies the WebAuthn Relying Party on whose behalf a given registration or authentication ceremony is being performed.
     * A public key credential can only be used for authentication with the same entity (as identified by RP ID) it was registered with.
     *
     * See <a href=https://www.w3.org/TR/webauthn/#relying-party"></a>
     */
    private String relyingPartyId;

    /**
     * Relying Party name for display purposes
     */
    private String relyingPartyName;

    /**
     * This member describes the Relying Party's requirements regarding resident credentials.
     * If the parameter is set to true, the authenticator MUST create a client-side-resident public key credential source when creating a public key credential.
     */
    private boolean requireResidentKey;

    /**
     * UserVerification, of type UserVerificationRequirement, defaulting to "preferred"
     * This member describes the Relying Party's requirements regarding user verification for the create() operation.
     * Eligible authenticators are filtered to only those capable of satisfying this requirement.
     */
    private UserVerification userVerification = UserVerification.PREFERRED;

    /**
     * Clients can communicate with authenticators using a variety of mechanisms.
     * For example, a client MAY use a client device-specific API to communicate with an authenticator which is physically bound to a client device.
     * On the other hand, a client can use a variety of standardized cross-platform transport protocols such as Bluetooth to discover and communicate with cross-platform attached authenticators.
     * We refer to authenticators that are part of the client device as platform authenticators, while those that are reachable via cross-platform transport protocols are referred to as roaming authenticators.
     */
    private AuthenticatorAttachment authenticatorAttachment;

    /**
     * WebAuthn Relying Parties may use AttestationConveyancePreference to specify their preference regarding attestation conveyance during credential generation.
     */
    private AttestationConveyancePreference attestationConveyancePreference = AttestationConveyancePreference.NONE;

    /**
     * Check that the credentialId is not yet registered to any other user.
     * If registration is requested for a credential that is already registered to a different user,
     * the Relying Party SHOULD fail this registration ceremony, or it MAY decide to accept the registration.
     */
    private boolean forceRegistration;

    /**
     * Device attestations X509 Certificates
     */
    private Map<String, Object> certificates;

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
}
