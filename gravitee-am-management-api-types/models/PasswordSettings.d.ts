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
 * Password policy applied to users of the domain: complexity requirements, expiry, and history.
 * @export
 * @interface PasswordSettings
 */
export interface PasswordSettings {
    /**
     * Whether passwords found in a common-password dictionary are rejected.
     * @type {boolean}
     * @memberof PasswordSettings
     */
    excludePasswordsInDictionary?: boolean;
    /**
     * Whether passwords containing the user's profile information are rejected.
     * @type {boolean}
     * @memberof PasswordSettings
     */
    excludeUserProfileInfoInPassword?: boolean;
    /**
     * Number of days after which a password expires and must be changed.
     * @type {number}
     * @memberof PasswordSettings
     */
    expiryDuration?: number;
    /**
     * Whether a password must contain at least one number.
     * @type {boolean}
     * @memberof PasswordSettings
     */
    includeNumbers?: boolean;
    /**
     * Whether a password must contain at least one special character.
     * @type {boolean}
     * @memberof PasswordSettings
     */
    includeSpecialCharacters?: boolean;
    /**
     * Whether these password settings are inherited from a parent scope rather than defined here. When true, the other fields are ignored.
     * @type {boolean}
     * @memberof PasswordSettings
     */
    inherited?: boolean;
    /**
     * Whether a password must contain both uppercase and lowercase letters.
     * @type {boolean}
     * @memberof PasswordSettings
     */
    lettersInMixedCase?: boolean;
    /**
     * Maximum number of identical consecutive characters allowed in a password.
     * @type {number}
     * @memberof PasswordSettings
     */
    maxConsecutiveLetters?: number;
    /**
     * Maximum number of characters a password may contain.
     * @type {number}
     * @memberof PasswordSettings
     */
    maxLength?: number;
    /**
     * Minimum number of characters a password must contain.
     * @type {number}
     * @memberof PasswordSettings
     */
    minLength?: number;
    /**
     * Number of previous passwords retained in history and barred from reuse.
     * @type {number}
     * @memberof PasswordSettings
     */
    oldPasswords?: number;
    /**
     * Whether password history is enforced to prevent reuse of recent passwords.
     * @type {boolean}
     * @memberof PasswordSettings
     */
    passwordHistoryEnabled?: boolean;
}
/**
 * Check if a given object implements the PasswordSettings interface.
 */
export declare function instanceOfPasswordSettings(value: object): value is PasswordSettings;
export declare function PasswordSettingsFromJSON(json: any): PasswordSettings;
export declare function PasswordSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PasswordSettings;
export declare function PasswordSettingsToJSON(json: any): PasswordSettings;
export declare function PasswordSettingsToJSONTyped(value?: PasswordSettings | null, ignoreDiscriminator?: boolean): any;
