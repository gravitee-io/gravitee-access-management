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
 * Configuration of the domain's SCIM 2.0 provisioning endpoints.
 * @export
 * @interface SCIMSettings
 */
export interface SCIMSettings {
    /**
     * Whether the SCIM provisioning API is enabled for the domain.
     * @type {boolean}
     * @memberof SCIMSettings
     */
    enabled?: boolean;
    /**
     * Whether an identity provider is selected for SCIM-provisioned users using a selection rule.
     * @type {boolean}
     * @memberof SCIMSettings
     */
    idpSelectionEnabled?: boolean;
    /**
     * Expression that selects the identity provider for a SCIM-provisioned user.
     * @type {string}
     * @memberof SCIMSettings
     */
    idpSelectionRule?: string;
}
/**
 * Check if a given object implements the SCIMSettings interface.
 */
export declare function instanceOfSCIMSettings(value: object): value is SCIMSettings;
export declare function SCIMSettingsFromJSON(json: any): SCIMSettings;
export declare function SCIMSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): SCIMSettings;
export declare function SCIMSettingsToJSON(json: any): SCIMSettings;
export declare function SCIMSettingsToJSONTyped(value?: SCIMSettings | null, ignoreDiscriminator?: boolean): any;
