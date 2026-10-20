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
 * @interface SpiffeDomainSettings
 */
export interface SpiffeDomainSettings {
    /**
     * Deprecated: moved to keyRetrievalSettings.allowPrivateIpAddress.
     * @type {boolean}
     * @memberof SpiffeDomainSettings
     * @deprecated
     */
    allowPrivateIpAddress?: boolean;
    /**
     * Deprecated: moved to keyRetrievalSettings.allowUnsecuredHttpUri.
     * @type {boolean}
     * @memberof SpiffeDomainSettings
     * @deprecated
     */
    allowUnsecuredHttpUri?: boolean;
    /**
     * Deprecated: moved to keyRetrievalSettings.cacheMaxEntries.
     * @type {number}
     * @memberof SpiffeDomainSettings
     * @deprecated
     */
    cacheMaxEntries?: number;
    /**
     * Deprecated: moved to keyRetrievalSettings.cacheTtlSeconds.
     * @type {number}
     * @memberof SpiffeDomainSettings
     * @deprecated
     */
    cacheTtlSeconds?: number;
    /**
     * Allowed clock skew, in seconds, when validating JWT temporal claims.
     * @type {number}
     * @memberof SpiffeDomainSettings
     */
    clockSkewSeconds?: number;
    /**
     * Default allowlist of signature algorithms accepted for SPIFFE JWT validation.
     * @type {Array<string>}
     * @memberof SpiffeDomainSettings
     */
    defaultAllowedAlgorithms?: Array<string>;
    /**
     * Whether SPIFFE workload identity support is enabled for the domain.
     * @type {boolean}
     * @memberof SpiffeDomainSettings
     */
    enabled?: boolean;
    /**
     * Deprecated: moved to keyRetrievalSettings.fetchTimeoutMs.
     * @type {number}
     * @memberof SpiffeDomainSettings
     * @deprecated
     */
    fetchTimeoutMs?: number;
    /**
     * Maximum accepted JWT lifetime, in seconds, computed as exp minus iat.
     * @type {number}
     * @memberof SpiffeDomainSettings
     */
    maxJwtLifetimeSeconds?: number;
    /**
     * Deprecated: moved to keyRetrievalSettings.maxResponseSizeKb.
     * @type {number}
     * @memberof SpiffeDomainSettings
     * @deprecated
     */
    maxResponseSizeKb?: number;
}
/**
 * Check if a given object implements the SpiffeDomainSettings interface.
 */
export declare function instanceOfSpiffeDomainSettings(value: object): value is SpiffeDomainSettings;
export declare function SpiffeDomainSettingsFromJSON(json: any): SpiffeDomainSettings;
export declare function SpiffeDomainSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): SpiffeDomainSettings;
export declare function SpiffeDomainSettingsToJSON(json: any): SpiffeDomainSettings;
export declare function SpiffeDomainSettingsToJSONTyped(value?: SpiffeDomainSettings | null, ignoreDiscriminator?: boolean): any;
