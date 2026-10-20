/*
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
/**
 * WebAuthn (FIDO2) relying-party configuration governing passwordless and multi-factor authentication for the domain.
 * @export
 * @interface WebAuthnSettings
 */
export interface WebAuthnSettings {
    /**
     * Relying-party preference for attestation conveyance during credential creation. NONE requests no attestation, INDIRECT allows anonymized attestation, and DIRECT requests the authenticator's attestation statement.
     * @type {string}
     * @memberof WebAuthnSettings
     */
    attestationConveyancePreference?: WebAuthnSettingsAttestationConveyancePreferenceEnum;
    /**
     * Preferred authenticator attachment. PLATFORM selects authenticators bound to the device (such as a fingerprint reader); CROSS_PLATFORM selects roaming authenticators (such as a security key).
     * @type {string}
     * @memberof WebAuthnSettings
     */
    authenticatorAttachment?: WebAuthnSettingsAuthenticatorAttachmentEnum;
    /**
     * Trusted device-attestation X.509 certificates, keyed by name.
     * @type {{ [key: string]: any; }}
     * @memberof WebAuthnSettings
     */
    certificates?: {
        [key: string]: any;
    };
    /**
     * Whether to periodically re-verify that registered authenticators remain valid against the FIDO2 Metadata Service.
     * @type {boolean}
     * @memberof WebAuthnSettings
     */
    enforceAuthenticatorIntegrity?: boolean;
    /**
     * Maximum elapsed time, in seconds, since an authenticator was last verified before it is re-checked on the next passwordless login.
     * @type {number}
     * @memberof WebAuthnSettings
     */
    enforceAuthenticatorIntegrityMaxAge?: number;
    /**
     * Whether to reject registration of a credential already registered to a different user.
     * @type {boolean}
     * @memberof WebAuthnSettings
     */
    forceRegistration?: boolean;
    /**
     * Relying-party origin; must match the browser's window.location.origin during registration and authentication ceremonies.
     * @type {string}
     * @memberof WebAuthnSettings
     */
    origin?: string;
    /**
     * Relying-party identifier: a domain string that scopes credentials to this entity. A credential can only be used with the relying party it was registered against.
     * @type {string}
     * @memberof WebAuthnSettings
     */
    relyingPartyId?: string;
    /**
     * Human-readable relying-party name shown to users during ceremonies.
     * @type {string}
     * @memberof WebAuthnSettings
     */
    relyingPartyName?: string;
    /**
     * Whether the authenticator must create a client-side resident (discoverable) credential.
     * @type {boolean}
     * @memberof WebAuthnSettings
     */
    requireResidentKey?: boolean;
    /**
     * Relying-party requirement regarding user verification during a ceremony. REQUIRED enforces verification, PREFERRED requests it when available, and DISCOURAGED avoids it.
     * @type {string}
     * @memberof WebAuthnSettings
     */
    userVerification?: WebAuthnSettingsUserVerificationEnum;
}
/**
 * @export
 */
export declare const WebAuthnSettingsAttestationConveyancePreferenceEnum: {
    readonly None: "NONE";
    readonly Indirect: "INDIRECT";
    readonly Direct: "DIRECT";
};
export type WebAuthnSettingsAttestationConveyancePreferenceEnum = typeof WebAuthnSettingsAttestationConveyancePreferenceEnum[keyof typeof WebAuthnSettingsAttestationConveyancePreferenceEnum];
/**
 * @export
 */
export declare const WebAuthnSettingsAuthenticatorAttachmentEnum: {
    readonly CrossPlatform: "CROSS_PLATFORM";
    readonly Platform: "PLATFORM";
};
export type WebAuthnSettingsAuthenticatorAttachmentEnum = typeof WebAuthnSettingsAuthenticatorAttachmentEnum[keyof typeof WebAuthnSettingsAuthenticatorAttachmentEnum];
/**
 * @export
 */
export declare const WebAuthnSettingsUserVerificationEnum: {
    readonly Required: "REQUIRED";
    readonly Preferred: "PREFERRED";
    readonly Discouraged: "DISCOURAGED";
};
export type WebAuthnSettingsUserVerificationEnum = typeof WebAuthnSettingsUserVerificationEnum[keyof typeof WebAuthnSettingsUserVerificationEnum];
/**
 * Check if a given object implements the WebAuthnSettings interface.
 */
export declare function instanceOfWebAuthnSettings(value: object): value is WebAuthnSettings;
export declare function WebAuthnSettingsFromJSON(json: any): WebAuthnSettings;
export declare function WebAuthnSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): WebAuthnSettings;
export declare function WebAuthnSettingsToJSON(json: any): WebAuthnSettings;
export declare function WebAuthnSettingsToJSONTyped(value?: WebAuthnSettings | null, ignoreDiscriminator?: boolean): any;
