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
 * @interface PatchSpiffeDomainSettings
 */
export interface PatchSpiffeDomainSettings {
    /**
     * Deprecated: moved to keyRetrievalSettings.allowPrivateIpAddress.
     * @type {boolean}
     * @memberof PatchSpiffeDomainSettings
     * @deprecated
     */
    allowPrivateIpAddress?: boolean;
    /**
     * Deprecated: moved to keyRetrievalSettings.allowUnsecuredHttpUri.
     * @type {boolean}
     * @memberof PatchSpiffeDomainSettings
     * @deprecated
     */
    allowUnsecuredHttpUri?: boolean;
    /**
     * Deprecated: moved to keyRetrievalSettings.cacheMaxEntries.
     * @type {number}
     * @memberof PatchSpiffeDomainSettings
     * @deprecated
     */
    cacheMaxEntries?: number;
    /**
     * Deprecated: moved to keyRetrievalSettings.cacheTtlSeconds.
     * @type {number}
     * @memberof PatchSpiffeDomainSettings
     * @deprecated
     */
    cacheTtlSeconds?: number;
    /**
     *
     * @type {number}
     * @memberof PatchSpiffeDomainSettings
     */
    clockSkewSeconds?: number;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchSpiffeDomainSettings
     */
    defaultAllowedAlgorithms?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof PatchSpiffeDomainSettings
     */
    enabled?: boolean;
    /**
     * Deprecated: moved to keyRetrievalSettings.fetchTimeoutMs.
     * @type {number}
     * @memberof PatchSpiffeDomainSettings
     * @deprecated
     */
    fetchTimeoutMs?: number;
    /**
     *
     * @type {number}
     * @memberof PatchSpiffeDomainSettings
     */
    maxJwtLifetimeSeconds?: number;
    /**
     * Deprecated: moved to keyRetrievalSettings.maxResponseSizeKb.
     * @type {number}
     * @memberof PatchSpiffeDomainSettings
     * @deprecated
     */
    maxResponseSizeKb?: number;
}
/**
 * Check if a given object implements the PatchSpiffeDomainSettings interface.
 */
export declare function instanceOfPatchSpiffeDomainSettings(value: object): value is PatchSpiffeDomainSettings;
export declare function PatchSpiffeDomainSettingsFromJSON(json: any): PatchSpiffeDomainSettings;
export declare function PatchSpiffeDomainSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchSpiffeDomainSettings;
export declare function PatchSpiffeDomainSettingsToJSON(json: any): PatchSpiffeDomainSettings;
export declare function PatchSpiffeDomainSettingsToJSONTyped(value?: PatchSpiffeDomainSettings | null, ignoreDiscriminator?: boolean): any;
