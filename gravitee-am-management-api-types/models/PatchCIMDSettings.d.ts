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
 * @interface PatchCIMDSettings
 */
export interface PatchCIMDSettings {
    /**
     *
     * @type {boolean}
     * @memberof PatchCIMDSettings
     */
    allowPrivateIpAddress?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchCIMDSettings
     */
    allowUnsecuredHttpUri?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchCIMDSettings
     */
    allowedDomains?: Array<string>;
    /**
     *
     * @type {number}
     * @memberof PatchCIMDSettings
     */
    cacheMaxEntries?: number;
    /**
     *
     * @type {number}
     * @memberof PatchCIMDSettings
     */
    cacheTtlSeconds?: number;
    /**
     *
     * @type {boolean}
     * @memberof PatchCIMDSettings
     */
    enabled?: boolean;
    /**
     *
     * @type {number}
     * @memberof PatchCIMDSettings
     */
    fetchTimeoutMs?: number;
    /**
     *
     * @type {number}
     * @memberof PatchCIMDSettings
     */
    maxResponseSizeKb?: number;
    /**
     *
     * @type {boolean}
     * @memberof PatchCIMDSettings
     */
    revokeOnDocumentChange?: boolean;
    /**
     *
     * @type {string}
     * @memberof PatchCIMDSettings
     */
    templateId?: string;
}
/**
 * Check if a given object implements the PatchCIMDSettings interface.
 */
export declare function instanceOfPatchCIMDSettings(value: object): value is PatchCIMDSettings;
export declare function PatchCIMDSettingsFromJSON(json: any): PatchCIMDSettings;
export declare function PatchCIMDSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchCIMDSettings;
export declare function PatchCIMDSettingsToJSON(json: any): PatchCIMDSettings;
export declare function PatchCIMDSettingsToJSONTyped(value?: PatchCIMDSettings | null, ignoreDiscriminator?: boolean): any;
