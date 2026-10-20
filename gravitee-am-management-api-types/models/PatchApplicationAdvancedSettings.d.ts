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
 * @interface PatchApplicationAdvancedSettings
 */
export interface PatchApplicationAdvancedSettings {
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationAdvancedSettings
     */
    flowsInherited?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationAdvancedSettings
     */
    skipConsent?: boolean;
}
/**
 * Check if a given object implements the PatchApplicationAdvancedSettings interface.
 */
export declare function instanceOfPatchApplicationAdvancedSettings(value: object): value is PatchApplicationAdvancedSettings;
export declare function PatchApplicationAdvancedSettingsFromJSON(json: any): PatchApplicationAdvancedSettings;
export declare function PatchApplicationAdvancedSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchApplicationAdvancedSettings;
export declare function PatchApplicationAdvancedSettingsToJSON(json: any): PatchApplicationAdvancedSettings;
export declare function PatchApplicationAdvancedSettingsToJSONTyped(value?: PatchApplicationAdvancedSettings | null, ignoreDiscriminator?: boolean): any;
