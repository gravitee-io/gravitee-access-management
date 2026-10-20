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
 * @interface ExtensionGrant
 */
export interface ExtensionGrant {
    /**
     *
     * @type {string}
     * @memberof ExtensionGrant
     */
    configuration?: string;
    /**
     *
     * @type {boolean}
     * @memberof ExtensionGrant
     */
    createUser?: boolean;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ExtensionGrant
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof ExtensionGrant
     */
    domain?: string;
    /**
     *
     * @type {string}
     * @memberof ExtensionGrant
     */
    grantType?: string;
    /**
     *
     * @type {string}
     * @memberof ExtensionGrant
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof ExtensionGrant
     */
    identityProvider?: string;
    /**
     *
     * @type {string}
     * @memberof ExtensionGrant
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof ExtensionGrant
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ExtensionGrant
     */
    updatedAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof ExtensionGrant
     */
    userExists?: boolean;
}
/**
 * Check if a given object implements the ExtensionGrant interface.
 */
export declare function instanceOfExtensionGrant(value: object): value is ExtensionGrant;
export declare function ExtensionGrantFromJSON(json: any): ExtensionGrant;
export declare function ExtensionGrantFromJSONTyped(json: any, ignoreDiscriminator: boolean): ExtensionGrant;
export declare function ExtensionGrantToJSON(json: any): ExtensionGrant;
export declare function ExtensionGrantToJSONTyped(value?: ExtensionGrant | null, ignoreDiscriminator?: boolean): any;
