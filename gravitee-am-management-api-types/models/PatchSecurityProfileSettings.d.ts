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
 * @interface PatchSecurityProfileSettings
 */
export interface PatchSecurityProfileSettings {
    /**
     *
     * @type {boolean}
     * @memberof PatchSecurityProfileSettings
     */
    enableFapiBrazil?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchSecurityProfileSettings
     */
    enablePlainFapi?: boolean;
}
/**
 * Check if a given object implements the PatchSecurityProfileSettings interface.
 */
export declare function instanceOfPatchSecurityProfileSettings(value: object): value is PatchSecurityProfileSettings;
export declare function PatchSecurityProfileSettingsFromJSON(json: any): PatchSecurityProfileSettings;
export declare function PatchSecurityProfileSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchSecurityProfileSettings;
export declare function PatchSecurityProfileSettingsToJSON(json: any): PatchSecurityProfileSettings;
export declare function PatchSecurityProfileSettingsToJSONTyped(value?: PatchSecurityProfileSettings | null, ignoreDiscriminator?: boolean): any;
