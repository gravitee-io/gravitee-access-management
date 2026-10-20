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
 * Controls the legacy X-XSS-Protection response header.
 * @export
 * @interface XssProtectionSettings
 */
export interface XssProtectionSettings {
    /**
     * Value of the X-XSS-Protection header.
     * @type {string}
     * @memberof XssProtectionSettings
     */
    action?: string;
    /**
     * Whether X-XSS-Protection is enabled for the domain when not inherited.
     * @type {boolean}
     * @memberof XssProtectionSettings
     */
    enabled?: boolean;
    /**
     * Whether X-XSS-Protection settings are inherited from the gateway defaults (gravitee.yml). When null, legacy behaviour applies: enabled=true overrides and enabled=false inherits.
     * @type {boolean}
     * @memberof XssProtectionSettings
     */
    inherited?: boolean;
}
/**
 * Check if a given object implements the XssProtectionSettings interface.
 */
export declare function instanceOfXssProtectionSettings(value: object): value is XssProtectionSettings;
export declare function XssProtectionSettingsFromJSON(json: any): XssProtectionSettings;
export declare function XssProtectionSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): XssProtectionSettings;
export declare function XssProtectionSettingsToJSON(json: any): XssProtectionSettings;
export declare function XssProtectionSettingsToJSONTyped(value?: XssProtectionSettings | null, ignoreDiscriminator?: boolean): any;
