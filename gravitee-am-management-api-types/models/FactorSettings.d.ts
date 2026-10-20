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
import type { ApplicationFactorSettings } from './ApplicationFactorSettings';
/**
 *
 * @export
 * @interface FactorSettings
 */
export interface FactorSettings {
    /**
     *
     * @type {Array<ApplicationFactorSettings>}
     * @memberof FactorSettings
     */
    applicationFactors?: Array<ApplicationFactorSettings>;
    /**
     *
     * @type {string}
     * @memberof FactorSettings
     */
    defaultFactorId?: string;
}
/**
 * Check if a given object implements the FactorSettings interface.
 */
export declare function instanceOfFactorSettings(value: object): value is FactorSettings;
export declare function FactorSettingsFromJSON(json: any): FactorSettings;
export declare function FactorSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): FactorSettings;
export declare function FactorSettingsToJSON(json: any): FactorSettings;
export declare function FactorSettingsToJSONTyped(value?: FactorSettings | null, ignoreDiscriminator?: boolean): any;
