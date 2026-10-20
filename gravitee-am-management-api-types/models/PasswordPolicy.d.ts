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
 * @interface PasswordPolicy
 */
export interface PasswordPolicy {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof PasswordPolicy
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof PasswordPolicy
     */
    defaultPolicy?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PasswordPolicy
     */
    excludePasswordsInDictionary?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PasswordPolicy
     */
    excludeUserProfileInfoInPassword?: boolean;
    /**
     *
     * @type {number}
     * @memberof PasswordPolicy
     */
    expiryDuration?: number;
    /**
     *
     * @type {string}
     * @memberof PasswordPolicy
     */
    id?: string;
    /**
     *
     * @type {boolean}
     * @memberof PasswordPolicy
     */
    includeNumbers?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PasswordPolicy
     */
    includeSpecialCharacters?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PasswordPolicy
     */
    lettersInMixedCase?: boolean;
    /**
     *
     * @type {number}
     * @memberof PasswordPolicy
     */
    maxConsecutiveLetters?: number;
    /**
     *
     * @type {number}
     * @memberof PasswordPolicy
     */
    maxLength?: number;
    /**
     *
     * @type {number}
     * @memberof PasswordPolicy
     */
    minLength?: number;
    /**
     *
     * @type {string}
     * @memberof PasswordPolicy
     */
    name?: string;
    /**
     *
     * @type {number}
     * @memberof PasswordPolicy
     */
    oldPasswords?: number;
    /**
     *
     * @type {boolean}
     * @memberof PasswordPolicy
     */
    passwordHistoryEnabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof PasswordPolicy
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof PasswordPolicy
     */
    referenceType?: PasswordPolicyReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof PasswordPolicy
     */
    regex?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof PasswordPolicy
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const PasswordPolicyReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type PasswordPolicyReferenceTypeEnum = typeof PasswordPolicyReferenceTypeEnum[keyof typeof PasswordPolicyReferenceTypeEnum];
/**
 * Check if a given object implements the PasswordPolicy interface.
 */
export declare function instanceOfPasswordPolicy(value: object): value is PasswordPolicy;
export declare function PasswordPolicyFromJSON(json: any): PasswordPolicy;
export declare function PasswordPolicyFromJSONTyped(json: any, ignoreDiscriminator: boolean): PasswordPolicy;
export declare function PasswordPolicyToJSON(json: any): PasswordPolicy;
export declare function PasswordPolicyToJSONTyped(value?: PasswordPolicy | null, ignoreDiscriminator?: boolean): any;
