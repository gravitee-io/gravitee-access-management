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
import type { PatchApplicationFactorSettings } from './PatchApplicationFactorSettings';
/**
 *
 * @export
 * @interface PatchFactorSettings
 */
export interface PatchFactorSettings {
    /**
     *
     * @type {Array<PatchApplicationFactorSettings>}
     * @memberof PatchFactorSettings
     */
    applicationFactors?: Array<PatchApplicationFactorSettings>;
    /**
     *
     * @type {string}
     * @memberof PatchFactorSettings
     */
    defaultFactorId?: string;
}
/**
 * Check if a given object implements the PatchFactorSettings interface.
 */
export declare function instanceOfPatchFactorSettings(value: object): value is PatchFactorSettings;
export declare function PatchFactorSettingsFromJSON(json: any): PatchFactorSettings;
export declare function PatchFactorSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchFactorSettings;
export declare function PatchFactorSettingsToJSON(json: any): PatchFactorSettings;
export declare function PatchFactorSettingsToJSONTyped(value?: PatchFactorSettings | null, ignoreDiscriminator?: boolean): any;
