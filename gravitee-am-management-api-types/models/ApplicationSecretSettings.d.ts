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
 * @interface ApplicationSecretSettings
 */
export interface ApplicationSecretSettings {
    /**
     *
     * @type {string}
     * @memberof ApplicationSecretSettings
     */
    algorithm?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationSecretSettings
     */
    id?: string;
}
/**
 * Check if a given object implements the ApplicationSecretSettings interface.
 */
export declare function instanceOfApplicationSecretSettings(value: object): value is ApplicationSecretSettings;
export declare function ApplicationSecretSettingsFromJSON(json: any): ApplicationSecretSettings;
export declare function ApplicationSecretSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationSecretSettings;
export declare function ApplicationSecretSettingsToJSON(json: any): ApplicationSecretSettings;
export declare function ApplicationSecretSettingsToJSONTyped(value?: ApplicationSecretSettings | null, ignoreDiscriminator?: boolean): any;
