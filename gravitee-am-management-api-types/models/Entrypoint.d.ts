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
 * @interface Entrypoint
 */
export interface Entrypoint {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Entrypoint
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof Entrypoint
     */
    defaultEntrypoint?: boolean;
    /**
     *
     * @type {string}
     * @memberof Entrypoint
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof Entrypoint
     */
    environmentId?: string;
    /**
     *
     * @type {string}
     * @memberof Entrypoint
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof Entrypoint
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof Entrypoint
     */
    organizationId?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof Entrypoint
     */
    tags?: Array<string>;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Entrypoint
     */
    updatedAt?: number;
    /**
     *
     * @type {string}
     * @memberof Entrypoint
     */
    url?: string;
}
/**
 * Check if a given object implements the Entrypoint interface.
 */
export declare function instanceOfEntrypoint(value: object): value is Entrypoint;
export declare function EntrypointFromJSON(json: any): Entrypoint;
export declare function EntrypointFromJSONTyped(json: any, ignoreDiscriminator: boolean): Entrypoint;
export declare function EntrypointToJSON(json: any): Entrypoint;
export declare function EntrypointToJSONTyped(value?: Entrypoint | null, ignoreDiscriminator?: boolean): any;
