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
 * @interface UpdateEntrypoint
 */
export interface UpdateEntrypoint {
    /**
     *
     * @type {string}
     * @memberof UpdateEntrypoint
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateEntrypoint
     */
    name: string;
    /**
     *
     * @type {Array<string>}
     * @memberof UpdateEntrypoint
     */
    tags: Array<string>;
    /**
     *
     * @type {string}
     * @memberof UpdateEntrypoint
     */
    url: string;
}
/**
 * Check if a given object implements the UpdateEntrypoint interface.
 */
export declare function instanceOfUpdateEntrypoint(value: object): value is UpdateEntrypoint;
export declare function UpdateEntrypointFromJSON(json: any): UpdateEntrypoint;
export declare function UpdateEntrypointFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateEntrypoint;
export declare function UpdateEntrypointToJSON(json: any): UpdateEntrypoint;
export declare function UpdateEntrypointToJSONTyped(value?: UpdateEntrypoint | null, ignoreDiscriminator?: boolean): any;
