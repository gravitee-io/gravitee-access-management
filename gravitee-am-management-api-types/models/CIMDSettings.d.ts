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
 * @interface CIMDSettings
 */
export interface CIMDSettings {
    /**
     *
     * @type {boolean}
     * @memberof CIMDSettings
     */
    allowPrivateIpAddress?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof CIMDSettings
     */
    allowUnsecuredHttpUri?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof CIMDSettings
     */
    allowedDomains?: Array<string>;
    /**
     *
     * @type {number}
     * @memberof CIMDSettings
     */
    cacheMaxEntries?: number;
    /**
     *
     * @type {number}
     * @memberof CIMDSettings
     */
    cacheTtlSeconds?: number;
    /**
     *
     * @type {boolean}
     * @memberof CIMDSettings
     */
    enabled?: boolean;
    /**
     *
     * @type {number}
     * @memberof CIMDSettings
     */
    fetchTimeoutMs?: number;
    /**
     *
     * @type {number}
     * @memberof CIMDSettings
     */
    maxResponseSizeKb?: number;
    /**
     *
     * @type {boolean}
     * @memberof CIMDSettings
     */
    revokeOnDocumentChange?: boolean;
    /**
     *
     * @type {string}
     * @memberof CIMDSettings
     */
    templateId?: string;
}
/**
 * Check if a given object implements the CIMDSettings interface.
 */
export declare function instanceOfCIMDSettings(value: object): value is CIMDSettings;
export declare function CIMDSettingsFromJSON(json: any): CIMDSettings;
export declare function CIMDSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): CIMDSettings;
export declare function CIMDSettingsToJSON(json: any): CIMDSettings;
export declare function CIMDSettingsToJSONTyped(value?: CIMDSettings | null, ignoreDiscriminator?: boolean): any;
