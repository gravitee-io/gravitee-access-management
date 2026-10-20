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
 * @interface PasswordPolicyEntity
 */
export interface PasswordPolicyEntity {
    /**
     *
     * @type {string}
     * @memberof PasswordPolicyEntity
     */
    id?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof PasswordPolicyEntity
     */
    idpsNames?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof PasswordPolicyEntity
     */
    isDefault?: boolean;
    /**
     *
     * @type {string}
     * @memberof PasswordPolicyEntity
     */
    name?: string;
}
/**
 * Check if a given object implements the PasswordPolicyEntity interface.
 */
export declare function instanceOfPasswordPolicyEntity(value: object): value is PasswordPolicyEntity;
export declare function PasswordPolicyEntityFromJSON(json: any): PasswordPolicyEntity;
export declare function PasswordPolicyEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): PasswordPolicyEntity;
export declare function PasswordPolicyEntityToJSON(json: any): PasswordPolicyEntity;
export declare function PasswordPolicyEntityToJSONTyped(value?: PasswordPolicyEntity | null, ignoreDiscriminator?: boolean): any;
