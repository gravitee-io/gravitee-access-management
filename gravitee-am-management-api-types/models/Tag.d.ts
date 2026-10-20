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
 * @interface Tag
 */
export interface Tag {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Tag
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof Tag
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof Tag
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof Tag
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof Tag
     */
    organizationId?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Tag
     */
    updatedAt?: number;
}
/**
 * Check if a given object implements the Tag interface.
 */
export declare function instanceOfTag(value: object): value is Tag;
export declare function TagFromJSON(json: any): Tag;
export declare function TagFromJSONTyped(json: any, ignoreDiscriminator: boolean): Tag;
export declare function TagToJSON(json: any): Tag;
export declare function TagToJSONTyped(value?: Tag | null, ignoreDiscriminator?: boolean): any;
