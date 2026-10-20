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
 * @interface EnrollSettings
 */
export interface EnrollSettings {
    /**
     *
     * @type {boolean}
     * @memberof EnrollSettings
     */
    active?: boolean;
    /**
     *
     * @type {string}
     * @memberof EnrollSettings
     */
    enrollmentRule?: string;
    /**
     *
     * @type {boolean}
     * @memberof EnrollSettings
     */
    enrollmentSkipActive?: boolean;
    /**
     *
     * @type {string}
     * @memberof EnrollSettings
     */
    enrollmentSkipRule?: string;
    /**
     *
     * @type {boolean}
     * @memberof EnrollSettings
     */
    forceEnrollment?: boolean;
    /**
     *
     * @type {number}
     * @memberof EnrollSettings
     */
    skipTimeSeconds?: number;
    /**
     *
     * @type {string}
     * @memberof EnrollSettings
     */
    type?: EnrollSettingsTypeEnum;
}
/**
 * @export
 */
export declare const EnrollSettingsTypeEnum: {
    readonly Optional: "OPTIONAL";
    readonly Required: "REQUIRED";
    readonly Conditional: "CONDITIONAL";
};
export type EnrollSettingsTypeEnum = typeof EnrollSettingsTypeEnum[keyof typeof EnrollSettingsTypeEnum];
/**
 * Check if a given object implements the EnrollSettings interface.
 */
export declare function instanceOfEnrollSettings(value: object): value is EnrollSettings;
export declare function EnrollSettingsFromJSON(json: any): EnrollSettings;
export declare function EnrollSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): EnrollSettings;
export declare function EnrollSettingsToJSON(json: any): EnrollSettings;
export declare function EnrollSettingsToJSONTyped(value?: EnrollSettings | null, ignoreDiscriminator?: boolean): any;
