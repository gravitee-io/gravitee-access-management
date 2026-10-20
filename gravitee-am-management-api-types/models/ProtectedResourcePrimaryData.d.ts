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
import type { ApplicationSettings } from './ApplicationSettings';
import type { ProtectedResourceFeature } from './ProtectedResourceFeature';
import type { ApplicationSecretSettings } from './ApplicationSecretSettings';
/**
 *
 * @export
 * @interface ProtectedResourcePrimaryData
 */
export interface ProtectedResourcePrimaryData {
    /**
     *
     * @type {string}
     * @memberof ProtectedResourcePrimaryData
     */
    certificate?: string;
    /**
     *
     * @type {string}
     * @memberof ProtectedResourcePrimaryData
     */
    clientId?: string;
    /**
     *
     * @type {string}
     * @memberof ProtectedResourcePrimaryData
     */
    description?: string;
    /**
     *
     * @type {Array<ProtectedResourceFeature>}
     * @memberof ProtectedResourcePrimaryData
     */
    features?: Array<ProtectedResourceFeature>;
    /**
     *
     * @type {string}
     * @memberof ProtectedResourcePrimaryData
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof ProtectedResourcePrimaryData
     */
    name?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof ProtectedResourcePrimaryData
     */
    resourceIdentifiers?: Array<string>;
    /**
     *
     * @type {Array<ApplicationSecretSettings>}
     * @memberof ProtectedResourcePrimaryData
     */
    secretSettings?: Array<ApplicationSecretSettings>;
    /**
     *
     * @type {ApplicationSettings}
     * @memberof ProtectedResourcePrimaryData
     */
    settings?: ApplicationSettings;
    /**
     *
     * @type {string}
     * @memberof ProtectedResourcePrimaryData
     */
    type?: ProtectedResourcePrimaryDataTypeEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ProtectedResourcePrimaryData
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const ProtectedResourcePrimaryDataTypeEnum: {
    readonly McpServer: "MCP_SERVER";
};
export type ProtectedResourcePrimaryDataTypeEnum = typeof ProtectedResourcePrimaryDataTypeEnum[keyof typeof ProtectedResourcePrimaryDataTypeEnum];
/**
 * Check if a given object implements the ProtectedResourcePrimaryData interface.
 */
export declare function instanceOfProtectedResourcePrimaryData(value: object): value is ProtectedResourcePrimaryData;
export declare function ProtectedResourcePrimaryDataFromJSON(json: any): ProtectedResourcePrimaryData;
export declare function ProtectedResourcePrimaryDataFromJSONTyped(json: any, ignoreDiscriminator: boolean): ProtectedResourcePrimaryData;
export declare function ProtectedResourcePrimaryDataToJSON(json: any): ProtectedResourcePrimaryData;
export declare function ProtectedResourcePrimaryDataToJSONTyped(value?: ProtectedResourcePrimaryData | null, ignoreDiscriminator?: boolean): any;
