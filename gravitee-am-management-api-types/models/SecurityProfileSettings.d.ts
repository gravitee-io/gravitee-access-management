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
 * Financial-grade API (FAPI) security profile configuration for the domain.
 * @export
 * @interface SecurityProfileSettings
 */
export interface SecurityProfileSettings {
    /**
     * Whether the Open Banking Brasil Financial-grade API security profile (version 1.0) is applied.
     * @type {boolean}
     * @memberof SecurityProfileSettings
     */
    enableFapiBrazil?: boolean;
    /**
     * Whether the standard Financial-grade API security profile (version 1.0) is applied.
     * @type {boolean}
     * @memberof SecurityProfileSettings
     */
    enablePlainFapi?: boolean;
}
/**
 * Check if a given object implements the SecurityProfileSettings interface.
 */
export declare function instanceOfSecurityProfileSettings(value: object): value is SecurityProfileSettings;
export declare function SecurityProfileSettingsFromJSON(json: any): SecurityProfileSettings;
export declare function SecurityProfileSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): SecurityProfileSettings;
export declare function SecurityProfileSettingsToJSON(json: any): SecurityProfileSettings;
export declare function SecurityProfileSettingsToJSONTyped(value?: SecurityProfileSettings | null, ignoreDiscriminator?: boolean): any;
