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
 * @interface AccessPolicy
 */
export interface AccessPolicy {
    /**
     *
     * @type {string}
     * @memberof AccessPolicy
     */
    condition?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AccessPolicy
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof AccessPolicy
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof AccessPolicy
     */
    domain?: string;
    /**
     *
     * @type {boolean}
     * @memberof AccessPolicy
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof AccessPolicy
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof AccessPolicy
     */
    name?: string;
    /**
     *
     * @type {number}
     * @memberof AccessPolicy
     */
    order?: number;
    /**
     *
     * @type {string}
     * @memberof AccessPolicy
     */
    resource?: string;
    /**
     *
     * @type {string}
     * @memberof AccessPolicy
     */
    type?: AccessPolicyTypeEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AccessPolicy
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const AccessPolicyTypeEnum: {
    readonly Groovy: "GROOVY";
};
export type AccessPolicyTypeEnum = typeof AccessPolicyTypeEnum[keyof typeof AccessPolicyTypeEnum];
/**
 * Check if a given object implements the AccessPolicy interface.
 */
export declare function instanceOfAccessPolicy(value: object): value is AccessPolicy;
export declare function AccessPolicyFromJSON(json: any): AccessPolicy;
export declare function AccessPolicyFromJSONTyped(json: any, ignoreDiscriminator: boolean): AccessPolicy;
export declare function AccessPolicyToJSON(json: any): AccessPolicy;
export declare function AccessPolicyToJSONTyped(value?: AccessPolicy | null, ignoreDiscriminator?: boolean): any;
