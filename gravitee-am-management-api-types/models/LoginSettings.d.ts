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
 * Configuration of the domain's login flow and the features offered on the sign-in page.
 * @export
 * @interface LoginSettings
 */
export interface LoginSettings {
    /**
     * Whether certificate-based authentication is offered.
     * @type {boolean}
     * @memberof LoginSettings
     */
    certificateBasedAuthEnabled?: boolean;
    /**
     * URL used for certificate-based authentication.
     * @type {string}
     * @memberof LoginSettings
     */
    certificateBasedAuthUrl?: string;
    /**
     * Whether users can initiate a forgot-password flow from the login page.
     * @type {boolean}
     * @memberof LoginSettings
     */
    forgotPasswordEnabled?: boolean;
    /**
     * Whether the login form is hidden (for example when only social or identifier-first login is offered).
     * @type {boolean}
     * @memberof LoginSettings
     */
    hideForm?: boolean;
    /**
     * Whether identifier-first login is enabled, prompting for the username before the password.
     * @type {boolean}
     * @memberof LoginSettings
     */
    identifierFirstEnabled?: boolean;
    /**
     * Whether these login settings are inherited from a parent scope rather than defined here. When true, the other fields are ignored.
     * @type {boolean}
     * @memberof LoginSettings
     */
    inherited?: boolean;
    /**
     * Whether magic-link authentication is offered.
     * @type {boolean}
     * @memberof LoginSettings
     */
    magicLinkAuthEnabled?: boolean;
    /**
     * Whether users can name their passwordless devices.
     * @type {boolean}
     * @memberof LoginSettings
     */
    passwordlessDeviceNamingEnabled?: boolean;
    /**
     * Whether passwordless (WebAuthn) authentication is offered.
     * @type {boolean}
     * @memberof LoginSettings
     */
    passwordlessEnabled?: boolean;
    /**
     * Whether a password is still required alongside passwordless authentication.
     * @type {boolean}
     * @memberof LoginSettings
     */
    passwordlessEnforcePasswordEnabled?: boolean;
    /**
     * Period, in seconds, after which the user's credentials must be re-entered to keep using passwordless authentication.
     * @type {number}
     * @memberof LoginSettings
     */
    passwordlessEnforcePasswordMaxAge?: number;
    /**
     * Whether a passwordless device can be remembered to skip future challenges.
     * @type {boolean}
     * @memberof LoginSettings
     */
    passwordlessRememberDeviceEnabled?: boolean;
    /**
     * Whether users can self-register from the login page.
     * @type {boolean}
     * @memberof LoginSettings
     */
    registerEnabled?: boolean;
    /**
     * Whether the login page offers a remember-me option.
     * @type {boolean}
     * @memberof LoginSettings
     */
    rememberMeEnabled?: boolean;
    /**
     * Whether the user is forced to reset their password once it expires.
     * @type {boolean}
     * @memberof LoginSettings
     */
    resetPasswordOnExpiration?: boolean;
}
/**
 * Check if a given object implements the LoginSettings interface.
 */
export declare function instanceOfLoginSettings(value: object): value is LoginSettings;
export declare function LoginSettingsFromJSON(json: any): LoginSettings;
export declare function LoginSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): LoginSettings;
export declare function LoginSettingsToJSON(json: any): LoginSettings;
export declare function LoginSettingsToJSONTyped(value?: LoginSettings | null, ignoreDiscriminator?: boolean): any;
