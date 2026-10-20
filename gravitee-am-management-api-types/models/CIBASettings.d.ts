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
import type { CIBASettingNotifier } from './CIBASettingNotifier';
/**
 *
 * @export
 * @interface CIBASettings
 */
export interface CIBASettings {
    /**
     *
     * @type {number}
     * @memberof CIBASettings
     */
    authReqExpiry?: number;
    /**
     *
     * @type {number}
     * @memberof CIBASettings
     */
    bindingMessageLength?: number;
    /**
     *
     * @type {Array<CIBASettingNotifier>}
     * @memberof CIBASettings
     */
    deviceNotifiers?: Array<CIBASettingNotifier>;
    /**
     *
     * @type {boolean}
     * @memberof CIBASettings
     */
    enabled?: boolean;
    /**
     *
     * @type {number}
     * @memberof CIBASettings
     */
    tokenReqInterval?: number;
}
/**
 * Check if a given object implements the CIBASettings interface.
 */
export declare function instanceOfCIBASettings(value: object): value is CIBASettings;
export declare function CIBASettingsFromJSON(json: any): CIBASettings;
export declare function CIBASettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): CIBASettings;
export declare function CIBASettingsToJSON(json: any): CIBASettings;
export declare function CIBASettingsToJSONTyped(value?: CIBASettings | null, ignoreDiscriminator?: boolean): any;
