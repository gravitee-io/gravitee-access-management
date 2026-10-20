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
 * Demonstrating Proof-of-Possession (RFC 9449) configuration for the domain.
 * @export
 * @interface DPoPSettings
 */
export interface DPoPSettings {
    /**
     * Allowlist of DPoP (RFC 9449) proof signing algorithms accepted at the token endpoint, drawn from the base supported set (ES256/384/512, RS256/384/512). When null the full base set is accepted. Persisting an empty set is rejected.
     * @type {Array<string>}
     * @memberof DPoPSettings
     */
    dpopSigningAlgorithms?: Array<string>;
    /**
     * Domain-wide floor (RFC 9449) requiring every application to present a DPoP proof at the token endpoint. When true it cannot be overridden by an application whose own dpop_bound_access_tokens flag is off.
     * @type {boolean}
     * @memberof DPoPSettings
     */
    requireDpopForAll?: boolean;
}
/**
 * Check if a given object implements the DPoPSettings interface.
 */
export declare function instanceOfDPoPSettings(value: object): value is DPoPSettings;
export declare function DPoPSettingsFromJSON(json: any): DPoPSettings;
export declare function DPoPSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): DPoPSettings;
export declare function DPoPSettingsToJSON(json: any): DPoPSettings;
export declare function DPoPSettingsToJSONTyped(value?: DPoPSettings | null, ignoreDiscriminator?: boolean): any;
