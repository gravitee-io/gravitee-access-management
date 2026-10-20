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
import type { ResetPasswordSettings } from './ResetPasswordSettings';
/**
 * Controls whether end users can manage their own account (for example, reset their password) and the rules that apply.
 * @export
 * @interface SelfServiceAccountManagementSettings
 */
export interface SelfServiceAccountManagementSettings {
    /**
     * Whether self-service account management is enabled for end users.
     * @type {boolean}
     * @memberof SelfServiceAccountManagementSettings
     */
    enabled?: boolean;
    /**
     *
     * @type {ResetPasswordSettings}
     * @memberof SelfServiceAccountManagementSettings
     */
    resetPassword?: ResetPasswordSettings;
}
/**
 * Check if a given object implements the SelfServiceAccountManagementSettings interface.
 */
export declare function instanceOfSelfServiceAccountManagementSettings(value: object): value is SelfServiceAccountManagementSettings;
export declare function SelfServiceAccountManagementSettingsFromJSON(json: any): SelfServiceAccountManagementSettings;
export declare function SelfServiceAccountManagementSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): SelfServiceAccountManagementSettings;
export declare function SelfServiceAccountManagementSettingsToJSON(json: any): SelfServiceAccountManagementSettings;
export declare function SelfServiceAccountManagementSettingsToJSONTyped(value?: SelfServiceAccountManagementSettings | null, ignoreDiscriminator?: boolean): any;
