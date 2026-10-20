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
 * Configuration of the domain's User-Managed Access (UMA 2.0) authorization features.
 * @export
 * @interface UMASettings
 */
export interface UMASettings {
    /**
     * Whether User-Managed Access is enabled for the domain.
     * @type {boolean}
     * @memberof UMASettings
     */
    enabled?: boolean;
}
/**
 * Check if a given object implements the UMASettings interface.
 */
export declare function instanceOfUMASettings(value: object): value is UMASettings;
export declare function UMASettingsFromJSON(json: any): UMASettings;
export declare function UMASettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): UMASettings;
export declare function UMASettingsToJSON(json: any): UMASettings;
export declare function UMASettingsToJSONTyped(value?: UMASettings | null, ignoreDiscriminator?: boolean): any;
