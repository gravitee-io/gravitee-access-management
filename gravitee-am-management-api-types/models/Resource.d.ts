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
 * @interface Resource
 */
export interface Resource {
    /**
     *
     * @type {string}
     * @memberof Resource
     */
    clientId?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Resource
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof Resource
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof Resource
     */
    domain?: string;
    /**
     *
     * @type {string}
     * @memberof Resource
     */
    iconUri?: string;
    /**
     *
     * @type {string}
     * @memberof Resource
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof Resource
     */
    name?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof Resource
     */
    resourceScopes?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof Resource
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Resource
     */
    updatedAt?: number;
    /**
     *
     * @type {string}
     * @memberof Resource
     */
    userId?: string;
}
/**
 * Check if a given object implements the Resource interface.
 */
export declare function instanceOfResource(value: object): value is Resource;
export declare function ResourceFromJSON(json: any): Resource;
export declare function ResourceFromJSONTyped(json: any, ignoreDiscriminator: boolean): Resource;
export declare function ResourceToJSON(json: any): Resource;
export declare function ResourceToJSONTyped(value?: Resource | null, ignoreDiscriminator?: boolean): any;
