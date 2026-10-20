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
 * @interface PatchKeyRetrievalSettings
 */
export interface PatchKeyRetrievalSettings {
    /**
     *
     * @type {boolean}
     * @memberof PatchKeyRetrievalSettings
     */
    allowPrivateIpAddress?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchKeyRetrievalSettings
     */
    allowUnsecuredHttpUri?: boolean;
    /**
     *
     * @type {number}
     * @memberof PatchKeyRetrievalSettings
     */
    cacheMaxEntries?: number;
    /**
     *
     * @type {number}
     * @memberof PatchKeyRetrievalSettings
     */
    cacheTtlSeconds?: number;
    /**
     *
     * @type {number}
     * @memberof PatchKeyRetrievalSettings
     */
    fetchTimeoutMs?: number;
    /**
     *
     * @type {number}
     * @memberof PatchKeyRetrievalSettings
     */
    maxResponseSizeKb?: number;
}
/**
 * Check if a given object implements the PatchKeyRetrievalSettings interface.
 */
export declare function instanceOfPatchKeyRetrievalSettings(value: object): value is PatchKeyRetrievalSettings;
export declare function PatchKeyRetrievalSettingsFromJSON(json: any): PatchKeyRetrievalSettings;
export declare function PatchKeyRetrievalSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchKeyRetrievalSettings;
export declare function PatchKeyRetrievalSettingsToJSON(json: any): PatchKeyRetrievalSettings;
export declare function PatchKeyRetrievalSettingsToJSONTyped(value?: PatchKeyRetrievalSettings | null, ignoreDiscriminator?: boolean): any;
