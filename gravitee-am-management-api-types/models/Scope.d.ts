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
 * @interface Scope
 */
export interface Scope {
    /**
     *
     * @type {Array<string>}
     * @memberof Scope
     */
    claims?: Array<string>;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Scope
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof Scope
     */
    description?: string;
    /**
     *
     * @type {boolean}
     * @memberof Scope
     */
    discovery?: boolean;
    /**
     *
     * @type {string}
     * @memberof Scope
     */
    domain?: string;
    /**
     *
     * @type {number}
     * @memberof Scope
     */
    expiresIn?: number;
    /**
     *
     * @type {string}
     * @memberof Scope
     */
    iconUri?: string;
    /**
     *
     * @type {string}
     * @memberof Scope
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof Scope
     */
    key?: string;
    /**
     *
     * @type {string}
     * @memberof Scope
     */
    name?: string;
    /**
     *
     * @type {boolean}
     * @memberof Scope
     */
    parameterized?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof Scope
     */
    system?: boolean;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Scope
     */
    updatedAt?: number;
}
/**
 * Check if a given object implements the Scope interface.
 */
export declare function instanceOfScope(value: object): value is Scope;
export declare function ScopeFromJSON(json: any): Scope;
export declare function ScopeFromJSONTyped(json: any, ignoreDiscriminator: boolean): Scope;
export declare function ScopeToJSON(json: any): Scope;
export declare function ScopeToJSONTyped(value?: Scope | null, ignoreDiscriminator?: boolean): any;
