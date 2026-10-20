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
 * @interface ResourceEntity
 */
export interface ResourceEntity {
    /**
     *
     * @type {string}
     * @memberof ResourceEntity
     */
    clientId?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ResourceEntity
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof ResourceEntity
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof ResourceEntity
     */
    domain?: string;
    /**
     *
     * @type {string}
     * @memberof ResourceEntity
     */
    iconUri?: string;
    /**
     *
     * @type {string}
     * @memberof ResourceEntity
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof ResourceEntity
     */
    name?: string;
    /**
     *
     * @type {number}
     * @memberof ResourceEntity
     */
    policies?: number;
    /**
     *
     * @type {Array<string>}
     * @memberof ResourceEntity
     */
    resourceScopes?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof ResourceEntity
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ResourceEntity
     */
    updatedAt?: number;
    /**
     *
     * @type {string}
     * @memberof ResourceEntity
     */
    userDisplayName?: string;
    /**
     *
     * @type {string}
     * @memberof ResourceEntity
     */
    userId?: string;
}
/**
 * Check if a given object implements the ResourceEntity interface.
 */
export declare function instanceOfResourceEntity(value: object): value is ResourceEntity;
export declare function ResourceEntityFromJSON(json: any): ResourceEntity;
export declare function ResourceEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): ResourceEntity;
export declare function ResourceEntityToJSON(json: any): ResourceEntity;
export declare function ResourceEntityToJSONTyped(value?: ResourceEntity | null, ignoreDiscriminator?: boolean): any;
