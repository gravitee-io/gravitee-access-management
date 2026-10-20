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
 * @interface NewPasswordPolicy
 */
export interface NewPasswordPolicy {
    /**
     *
     * @type {boolean}
     * @memberof NewPasswordPolicy
     */
    excludePasswordsInDictionary?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof NewPasswordPolicy
     */
    excludeUserProfileInfoInPassword?: boolean;
    /**
     *
     * @type {number}
     * @memberof NewPasswordPolicy
     */
    expiryDuration?: number;
    /**
     *
     * @type {boolean}
     * @memberof NewPasswordPolicy
     */
    includeNumbers?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof NewPasswordPolicy
     */
    includeSpecialCharacters?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof NewPasswordPolicy
     */
    lettersInMixedCase?: boolean;
    /**
     *
     * @type {number}
     * @memberof NewPasswordPolicy
     */
    maxConsecutiveLetters?: number;
    /**
     *
     * @type {number}
     * @memberof NewPasswordPolicy
     */
    maxLength?: number;
    /**
     *
     * @type {number}
     * @memberof NewPasswordPolicy
     */
    minLength?: number;
    /**
     *
     * @type {string}
     * @memberof NewPasswordPolicy
     */
    name: string;
    /**
     *
     * @type {number}
     * @memberof NewPasswordPolicy
     */
    oldPasswords?: number;
    /**
     *
     * @type {boolean}
     * @memberof NewPasswordPolicy
     */
    passwordHistoryEnabled?: boolean;
}
/**
 * Check if a given object implements the NewPasswordPolicy interface.
 */
export declare function instanceOfNewPasswordPolicy(value: object): value is NewPasswordPolicy;
export declare function NewPasswordPolicyFromJSON(json: any): NewPasswordPolicy;
export declare function NewPasswordPolicyFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewPasswordPolicy;
export declare function NewPasswordPolicyToJSON(json: any): NewPasswordPolicy;
export declare function NewPasswordPolicyToJSONTyped(value?: NewPasswordPolicy | null, ignoreDiscriminator?: boolean): any;
