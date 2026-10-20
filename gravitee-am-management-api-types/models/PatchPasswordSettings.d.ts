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
 * @interface PatchPasswordSettings
 */
export interface PatchPasswordSettings {
    /**
     *
     * @type {boolean}
     * @memberof PatchPasswordSettings
     */
    excludePasswordsInDictionary?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchPasswordSettings
     */
    excludeUserProfileInfoInPassword?: boolean;
    /**
     *
     * @type {number}
     * @memberof PatchPasswordSettings
     */
    expiryDuration?: number;
    /**
     *
     * @type {boolean}
     * @memberof PatchPasswordSettings
     */
    includeNumbers?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchPasswordSettings
     */
    includeSpecialCharacters?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchPasswordSettings
     */
    inherited?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchPasswordSettings
     */
    lettersInMixedCase?: boolean;
    /**
     *
     * @type {number}
     * @memberof PatchPasswordSettings
     */
    maxConsecutiveLetters?: number;
    /**
     *
     * @type {number}
     * @memberof PatchPasswordSettings
     */
    maxLength?: number;
    /**
     *
     * @type {number}
     * @memberof PatchPasswordSettings
     */
    minLength?: number;
    /**
     *
     * @type {number}
     * @memberof PatchPasswordSettings
     */
    oldPasswords?: number;
    /**
     *
     * @type {boolean}
     * @memberof PatchPasswordSettings
     */
    passwordHistoryEnabled?: boolean;
}
/**
 * Check if a given object implements the PatchPasswordSettings interface.
 */
export declare function instanceOfPatchPasswordSettings(value: object): value is PatchPasswordSettings;
export declare function PatchPasswordSettingsFromJSON(json: any): PatchPasswordSettings;
export declare function PatchPasswordSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchPasswordSettings;
export declare function PatchPasswordSettingsToJSON(json: any): PatchPasswordSettings;
export declare function PatchPasswordSettingsToJSONTyped(value?: PatchPasswordSettings | null, ignoreDiscriminator?: boolean): any;
