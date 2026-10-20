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
 * @interface ApplicationAdvancedSettings
 */
export interface ApplicationAdvancedSettings {
    /**
     *
     * @type {boolean}
     * @memberof ApplicationAdvancedSettings
     */
    flowsInherited?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationAdvancedSettings
     */
    skipConsent?: boolean;
}
/**
 * Check if a given object implements the ApplicationAdvancedSettings interface.
 */
export declare function instanceOfApplicationAdvancedSettings(value: object): value is ApplicationAdvancedSettings;
export declare function ApplicationAdvancedSettingsFromJSON(json: any): ApplicationAdvancedSettings;
export declare function ApplicationAdvancedSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationAdvancedSettings;
export declare function ApplicationAdvancedSettingsToJSON(json: any): ApplicationAdvancedSettings;
export declare function ApplicationAdvancedSettingsToJSONTyped(value?: ApplicationAdvancedSettings | null, ignoreDiscriminator?: boolean): any;
