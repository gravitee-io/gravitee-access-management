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
 * @interface PatchCIBASettings
 */
export interface PatchCIBASettings {
    /**
     *
     * @type {number}
     * @memberof PatchCIBASettings
     */
    authReqExpiry?: number;
    /**
     *
     * @type {number}
     * @memberof PatchCIBASettings
     */
    bindingMessageLength?: number;
    /**
     *
     * @type {Array<CIBASettingNotifier>}
     * @memberof PatchCIBASettings
     */
    deviceNotifiers?: Array<CIBASettingNotifier>;
    /**
     *
     * @type {boolean}
     * @memberof PatchCIBASettings
     */
    enabled?: boolean;
    /**
     *
     * @type {number}
     * @memberof PatchCIBASettings
     */
    tokenReqInterval?: number;
}
/**
 * Check if a given object implements the PatchCIBASettings interface.
 */
export declare function instanceOfPatchCIBASettings(value: object): value is PatchCIBASettings;
export declare function PatchCIBASettingsFromJSON(json: any): PatchCIBASettings;
export declare function PatchCIBASettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchCIBASettings;
export declare function PatchCIBASettingsToJSON(json: any): PatchCIBASettings;
export declare function PatchCIBASettingsToJSONTyped(value?: PatchCIBASettings | null, ignoreDiscriminator?: boolean): any;
