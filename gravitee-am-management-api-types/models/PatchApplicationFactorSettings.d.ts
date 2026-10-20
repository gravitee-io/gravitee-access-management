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
 * @interface PatchApplicationFactorSettings
 */
export interface PatchApplicationFactorSettings {
    /**
     *
     * @type {string}
     * @memberof PatchApplicationFactorSettings
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationFactorSettings
     */
    selectionRule?: string;
}
/**
 * Check if a given object implements the PatchApplicationFactorSettings interface.
 */
export declare function instanceOfPatchApplicationFactorSettings(value: object): value is PatchApplicationFactorSettings;
export declare function PatchApplicationFactorSettingsFromJSON(json: any): PatchApplicationFactorSettings;
export declare function PatchApplicationFactorSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchApplicationFactorSettings;
export declare function PatchApplicationFactorSettingsToJSON(json: any): PatchApplicationFactorSettings;
export declare function PatchApplicationFactorSettingsToJSONTyped(value?: PatchApplicationFactorSettings | null, ignoreDiscriminator?: boolean): any;
