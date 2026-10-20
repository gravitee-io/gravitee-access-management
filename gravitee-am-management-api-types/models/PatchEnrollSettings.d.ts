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
 * @interface PatchEnrollSettings
 */
export interface PatchEnrollSettings {
    /**
     *
     * @type {boolean}
     * @memberof PatchEnrollSettings
     */
    active?: boolean;
    /**
     *
     * @type {string}
     * @memberof PatchEnrollSettings
     */
    enrollmentRule?: string;
    /**
     *
     * @type {boolean}
     * @memberof PatchEnrollSettings
     */
    enrollmentSkipActive?: boolean;
    /**
     *
     * @type {string}
     * @memberof PatchEnrollSettings
     */
    enrollmentSkipRule?: string;
    /**
     *
     * @type {boolean}
     * @memberof PatchEnrollSettings
     */
    forceEnrollment?: boolean;
    /**
     *
     * @type {number}
     * @memberof PatchEnrollSettings
     */
    skipTimeSeconds?: number;
    /**
     *
     * @type {string}
     * @memberof PatchEnrollSettings
     */
    type?: PatchEnrollSettingsTypeEnum;
}
/**
 * @export
 */
export declare const PatchEnrollSettingsTypeEnum: {
    readonly Optional: "OPTIONAL";
    readonly Required: "REQUIRED";
    readonly Conditional: "CONDITIONAL";
};
export type PatchEnrollSettingsTypeEnum = typeof PatchEnrollSettingsTypeEnum[keyof typeof PatchEnrollSettingsTypeEnum];
/**
 * Check if a given object implements the PatchEnrollSettings interface.
 */
export declare function instanceOfPatchEnrollSettings(value: object): value is PatchEnrollSettings;
export declare function PatchEnrollSettingsFromJSON(json: any): PatchEnrollSettings;
export declare function PatchEnrollSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchEnrollSettings;
export declare function PatchEnrollSettingsToJSON(json: any): PatchEnrollSettings;
export declare function PatchEnrollSettingsToJSONTyped(value?: PatchEnrollSettings | null, ignoreDiscriminator?: boolean): any;
