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
 * @interface PatchEnrollmentSettings
 */
export interface PatchEnrollmentSettings {
    /**
     *
     * @type {boolean}
     * @memberof PatchEnrollmentSettings
     */
    forceEnrollment?: boolean;
    /**
     *
     * @type {number}
     * @memberof PatchEnrollmentSettings
     */
    skipTimeSeconds?: number;
}
/**
 * Check if a given object implements the PatchEnrollmentSettings interface.
 */
export declare function instanceOfPatchEnrollmentSettings(value: object): value is PatchEnrollmentSettings;
export declare function PatchEnrollmentSettingsFromJSON(json: any): PatchEnrollmentSettings;
export declare function PatchEnrollmentSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchEnrollmentSettings;
export declare function PatchEnrollmentSettingsToJSON(json: any): PatchEnrollmentSettings;
export declare function PatchEnrollmentSettingsToJSONTyped(value?: PatchEnrollmentSettings | null, ignoreDiscriminator?: boolean): any;
