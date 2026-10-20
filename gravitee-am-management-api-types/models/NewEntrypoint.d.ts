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
 * @interface NewEntrypoint
 */
export interface NewEntrypoint {
    /**
     *
     * @type {string}
     * @memberof NewEntrypoint
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof NewEntrypoint
     */
    environmentId?: string;
    /**
     *
     * @type {string}
     * @memberof NewEntrypoint
     */
    name: string;
    /**
     *
     * @type {Array<string>}
     * @memberof NewEntrypoint
     */
    tags: Array<string>;
    /**
     *
     * @type {string}
     * @memberof NewEntrypoint
     */
    url: string;
}
/**
 * Check if a given object implements the NewEntrypoint interface.
 */
export declare function instanceOfNewEntrypoint(value: object): value is NewEntrypoint;
export declare function NewEntrypointFromJSON(json: any): NewEntrypoint;
export declare function NewEntrypointFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewEntrypoint;
export declare function NewEntrypointToJSON(json: any): NewEntrypoint;
export declare function NewEntrypointToJSONTyped(value?: NewEntrypoint | null, ignoreDiscriminator?: boolean): any;
