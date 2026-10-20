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
 * @interface PatchRememberDeviceSettings
 */
export interface PatchRememberDeviceSettings {
    /**
     *
     * @type {boolean}
     * @memberof PatchRememberDeviceSettings
     */
    active?: boolean;
    /**
     *
     * @type {string}
     * @memberof PatchRememberDeviceSettings
     */
    deviceIdentifierId?: string;
    /**
     *
     * @type {number}
     * @memberof PatchRememberDeviceSettings
     */
    expirationTimeSeconds?: number;
    /**
     *
     * @type {boolean}
     * @memberof PatchRememberDeviceSettings
     */
    skipRememberDevice?: boolean;
}
/**
 * Check if a given object implements the PatchRememberDeviceSettings interface.
 */
export declare function instanceOfPatchRememberDeviceSettings(value: object): value is PatchRememberDeviceSettings;
export declare function PatchRememberDeviceSettingsFromJSON(json: any): PatchRememberDeviceSettings;
export declare function PatchRememberDeviceSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchRememberDeviceSettings;
export declare function PatchRememberDeviceSettingsToJSON(json: any): PatchRememberDeviceSettings;
export declare function PatchRememberDeviceSettingsToJSONTyped(value?: PatchRememberDeviceSettings | null, ignoreDiscriminator?: boolean): any;
