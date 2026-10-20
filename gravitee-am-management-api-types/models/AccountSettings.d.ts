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
import type { FormField } from './FormField';
/**
 *
 * @export
 * @interface AccountSettings
 */
export interface AccountSettings {
    /**
     *
     * @type {number}
     * @memberof AccountSettings
     */
    accountBlockedDuration?: number;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    autoLoginAfterRegistration?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    autoLoginAfterResetPassword?: boolean;
    /**
     *
     * @type {string}
     * @memberof AccountSettings
     */
    botDetectionPlugin?: string;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    completeRegistrationWhenResetPassword?: boolean;
    /**
     *
     * @type {string}
     * @memberof AccountSettings
     */
    defaultIdentityProviderForRegistration?: string;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    deletePasswordlessDevicesAfterResetPassword?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    dynamicUserRegistration?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    inherited?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    loginAttemptsDetectionEnabled?: boolean;
    /**
     *
     * @type {number}
     * @memberof AccountSettings
     */
    loginAttemptsResetTime?: number;
    /**
     *
     * @type {number}
     * @memberof AccountSettings
     */
    maxLoginAttempts?: number;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    mfaChallengeAttemptsDetectionEnabled?: boolean;
    /**
     *
     * @type {number}
     * @memberof AccountSettings
     */
    mfaChallengeAttemptsResetTime?: number;
    /**
     *
     * @type {number}
     * @memberof AccountSettings
     */
    mfaChallengeMaxAttempts?: number;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    mfaChallengeSendVerifyAlertEmail?: boolean;
    /**
     *
     * @type {string}
     * @memberof AccountSettings
     */
    redirectUriAfterRegistration?: string;
    /**
     *
     * @type {string}
     * @memberof AccountSettings
     */
    redirectUriAfterResetPassword?: string;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    rememberMe?: boolean;
    /**
     *
     * @type {number}
     * @memberof AccountSettings
     */
    rememberMeDuration?: number;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    resetPasswordConfirmIdentity?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    resetPasswordCustomForm?: boolean;
    /**
     *
     * @type {Array<FormField>}
     * @memberof AccountSettings
     */
    resetPasswordCustomFormFields?: Array<FormField>;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    resetPasswordInvalidateTokens?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    sendRecoverAccountEmail?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    sendVerifyRegistrationAccountEmail?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof AccountSettings
     */
    useBotDetection?: boolean;
}
/**
 * Check if a given object implements the AccountSettings interface.
 */
export declare function instanceOfAccountSettings(value: object): value is AccountSettings;
export declare function AccountSettingsFromJSON(json: any): AccountSettings;
export declare function AccountSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): AccountSettings;
export declare function AccountSettingsToJSON(json: any): AccountSettings;
export declare function AccountSettingsToJSONTyped(value?: AccountSettings | null, ignoreDiscriminator?: boolean): any;
