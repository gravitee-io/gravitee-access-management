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
 * @interface UpdatePasswordPolicy
 */
export interface UpdatePasswordPolicy {
    /**
     *
     * @type {boolean}
     * @memberof UpdatePasswordPolicy
     */
    defaultPolicy?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof UpdatePasswordPolicy
     */
    excludePasswordsInDictionary?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof UpdatePasswordPolicy
     */
    excludeUserProfileInfoInPassword?: boolean;
    /**
     *
     * @type {number}
     * @memberof UpdatePasswordPolicy
     */
    expiryDuration?: number;
    /**
     *
     * @type {boolean}
     * @memberof UpdatePasswordPolicy
     */
    includeNumbers?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof UpdatePasswordPolicy
     */
    includeSpecialCharacters?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof UpdatePasswordPolicy
     */
    lettersInMixedCase?: boolean;
    /**
     *
     * @type {number}
     * @memberof UpdatePasswordPolicy
     */
    maxConsecutiveLetters?: number;
    /**
     *
     * @type {number}
     * @memberof UpdatePasswordPolicy
     */
    maxLength?: number;
    /**
     *
     * @type {number}
     * @memberof UpdatePasswordPolicy
     */
    minLength?: number;
    /**
     *
     * @type {string}
     * @memberof UpdatePasswordPolicy
     */
    name: string;
    /**
     *
     * @type {number}
     * @memberof UpdatePasswordPolicy
     */
    oldPasswords?: number;
    /**
     *
     * @type {boolean}
     * @memberof UpdatePasswordPolicy
     */
    passwordHistoryEnabled?: boolean;
}
/**
 * Check if a given object implements the UpdatePasswordPolicy interface.
 */
export declare function instanceOfUpdatePasswordPolicy(value: object): value is UpdatePasswordPolicy;
export declare function UpdatePasswordPolicyFromJSON(json: any): UpdatePasswordPolicy;
export declare function UpdatePasswordPolicyFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdatePasswordPolicy;
export declare function UpdatePasswordPolicyToJSON(json: any): UpdatePasswordPolicy;
export declare function UpdatePasswordPolicyToJSONTyped(value?: UpdatePasswordPolicy | null, ignoreDiscriminator?: boolean): any;
