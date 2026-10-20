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
 * @interface ApplicationFactorSettings
 */
export interface ApplicationFactorSettings {
    /**
     *
     * @type {string}
     * @memberof ApplicationFactorSettings
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationFactorSettings
     */
    selectionRule?: string;
}
/**
 * Check if a given object implements the ApplicationFactorSettings interface.
 */
export declare function instanceOfApplicationFactorSettings(value: object): value is ApplicationFactorSettings;
export declare function ApplicationFactorSettingsFromJSON(json: any): ApplicationFactorSettings;
export declare function ApplicationFactorSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationFactorSettings;
export declare function ApplicationFactorSettingsToJSON(json: any): ApplicationFactorSettings;
export declare function ApplicationFactorSettingsToJSONTyped(value?: ApplicationFactorSettings | null, ignoreDiscriminator?: boolean): any;
