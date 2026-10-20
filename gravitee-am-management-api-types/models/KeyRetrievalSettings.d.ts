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
 * Fetch, SSRF and cache limits applied to every trusted domain in the security domain.
 * @export
 * @interface KeyRetrievalSettings
 */
export interface KeyRetrievalSettings {
    /**
     * Whether key material can be fetched from private IP addresses.
     * @type {boolean}
     * @memberof KeyRetrievalSettings
     */
    allowPrivateIpAddress?: boolean;
    /**
     * Whether key material can be fetched over unsecured HTTP URIs.
     * @type {boolean}
     * @memberof KeyRetrievalSettings
     */
    allowUnsecuredHttpUri?: boolean;
    /**
     * Maximum number of key material entries retained in the cache.
     * @type {number}
     * @memberof KeyRetrievalSettings
     */
    cacheMaxEntries?: number;
    /**
     * Time-to-live, in seconds, for cached key material.
     * @type {number}
     * @memberof KeyRetrievalSettings
     */
    cacheTtlSeconds?: number;
    /**
     * Timeout, in milliseconds, for fetching key material.
     * @type {number}
     * @memberof KeyRetrievalSettings
     */
    fetchTimeoutMs?: number;
    /**
     * Maximum key material response size, in kilobytes.
     * @type {number}
     * @memberof KeyRetrievalSettings
     */
    maxResponseSizeKb?: number;
}
/**
 * Check if a given object implements the KeyRetrievalSettings interface.
 */
export declare function instanceOfKeyRetrievalSettings(value: object): value is KeyRetrievalSettings;
export declare function KeyRetrievalSettingsFromJSON(json: any): KeyRetrievalSettings;
export declare function KeyRetrievalSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): KeyRetrievalSettings;
export declare function KeyRetrievalSettingsToJSON(json: any): KeyRetrievalSettings;
export declare function KeyRetrievalSettingsToJSONTyped(value?: KeyRetrievalSettings | null, ignoreDiscriminator?: boolean): any;
