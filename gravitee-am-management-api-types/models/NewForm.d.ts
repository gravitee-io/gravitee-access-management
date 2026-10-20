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
 *
 * @export
 * @interface NewForm
 */
export interface NewForm {
    /**
     *
     * @type {string}
     * @memberof NewForm
     */
    assets?: string;
    /**
     *
     * @type {string}
     * @memberof NewForm
     */
    content: string;
    /**
     *
     * @type {boolean}
     * @memberof NewForm
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewForm
     */
    template: NewFormTemplateEnum;
}
/**
 * @export
 */
export declare const NewFormTemplateEnum: {
    readonly Login: "LOGIN";
    readonly Registration: "REGISTRATION";
    readonly RegistrationConfirmation: "REGISTRATION_CONFIRMATION";
    readonly RegistrationVerify: "REGISTRATION_VERIFY";
    readonly ForgotPassword: "FORGOT_PASSWORD";
    readonly ResetPassword: "RESET_PASSWORD";
    readonly Oauth2UserConsent: "OAUTH2_USER_CONSENT";
    readonly MfaEnroll: "MFA_ENROLL";
    readonly MfaChallenge: "MFA_CHALLENGE";
    readonly MfaChallengeAlternatives: "MFA_CHALLENGE_ALTERNATIVES";
    readonly MfaRecoveryCode: "MFA_RECOVERY_CODE";
    readonly BlockedAccount: "BLOCKED_ACCOUNT";
    readonly CompleteProfile: "COMPLETE_PROFILE";
    readonly WebauthnRegister: "WEBAUTHN_REGISTER";
    readonly WebauthnRegisterSuccess: "WEBAUTHN_REGISTER_SUCCESS";
    readonly WebauthnLogin: "WEBAUTHN_LOGIN";
    readonly CbaLogin: "CBA_LOGIN";
    readonly MagicLinkLogin: "MAGIC_LINK_LOGIN";
    readonly MagicLink: "MAGIC_LINK";
    readonly IdentifierFirstLogin: "IDENTIFIER_FIRST_LOGIN";
    readonly Error: "ERROR";
    readonly CertificateExpiration: "CERTIFICATE_EXPIRATION";
    readonly ClientSecretExpiration: "CLIENT_SECRET_EXPIRATION";
    readonly VerifyAttempt: "VERIFY_ATTEMPT";
};
export type NewFormTemplateEnum = typeof NewFormTemplateEnum[keyof typeof NewFormTemplateEnum];
/**
 * Check if a given object implements the NewForm interface.
 */
export declare function instanceOfNewForm(value: object): value is NewForm;
export declare function NewFormFromJSON(json: any): NewForm;
export declare function NewFormFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewForm;
export declare function NewFormToJSON(json: any): NewForm;
export declare function NewFormToJSONTyped(value?: NewForm | null, ignoreDiscriminator?: boolean): any;
