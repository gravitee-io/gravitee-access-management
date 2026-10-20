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
 * @interface SAMLSettings
 */
export interface SAMLSettings {
    /**
     *
     * @type {string}
     * @memberof SAMLSettings
     */
    certificate?: string;
    /**
     *
     * @type {boolean}
     * @memberof SAMLSettings
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof SAMLSettings
     */
    entityId?: string;
}
/**
 * Check if a given object implements the SAMLSettings interface.
 */
export declare function instanceOfSAMLSettings(value: object): value is SAMLSettings;
export declare function SAMLSettingsFromJSON(json: any): SAMLSettings;
export declare function SAMLSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): SAMLSettings;
export declare function SAMLSettingsToJSON(json: any): SAMLSettings;
export declare function SAMLSettingsToJSONTyped(value?: SAMLSettings | null, ignoreDiscriminator?: boolean): any;
