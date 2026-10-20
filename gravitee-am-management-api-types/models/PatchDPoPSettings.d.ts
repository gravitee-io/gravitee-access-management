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
 * @interface PatchDPoPSettings
 */
export interface PatchDPoPSettings {
    /**
     *
     * @type {Array<string>}
     * @memberof PatchDPoPSettings
     */
    dpopSigningAlgorithms?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof PatchDPoPSettings
     */
    requireDpopForAll?: boolean;
}
/**
 * Check if a given object implements the PatchDPoPSettings interface.
 */
export declare function instanceOfPatchDPoPSettings(value: object): value is PatchDPoPSettings;
export declare function PatchDPoPSettingsFromJSON(json: any): PatchDPoPSettings;
export declare function PatchDPoPSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchDPoPSettings;
export declare function PatchDPoPSettingsToJSON(json: any): PatchDPoPSettings;
export declare function PatchDPoPSettingsToJSONTyped(value?: PatchDPoPSettings | null, ignoreDiscriminator?: boolean): any;
