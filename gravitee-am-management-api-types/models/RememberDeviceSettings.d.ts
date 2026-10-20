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
 * @interface RememberDeviceSettings
 */
export interface RememberDeviceSettings {
    /**
     *
     * @type {boolean}
     * @memberof RememberDeviceSettings
     */
    active?: boolean;
    /**
     *
     * @type {string}
     * @memberof RememberDeviceSettings
     */
    deviceIdentifierId?: string;
    /**
     *
     * @type {number}
     * @memberof RememberDeviceSettings
     */
    expirationTimeSeconds?: number;
    /**
     *
     * @type {boolean}
     * @memberof RememberDeviceSettings
     */
    skipChallengeWhenRememberDevice?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof RememberDeviceSettings
     */
    skipRememberDevice?: boolean;
}
/**
 * Check if a given object implements the RememberDeviceSettings interface.
 */
export declare function instanceOfRememberDeviceSettings(value: object): value is RememberDeviceSettings;
export declare function RememberDeviceSettingsFromJSON(json: any): RememberDeviceSettings;
export declare function RememberDeviceSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): RememberDeviceSettings;
export declare function RememberDeviceSettingsToJSON(json: any): RememberDeviceSettings;
export declare function RememberDeviceSettingsToJSONTyped(value?: RememberDeviceSettings | null, ignoreDiscriminator?: boolean): any;
