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
 * @interface PatchLoginSettings
 */
export interface PatchLoginSettings {
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    certificateBasedAuthEnabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof PatchLoginSettings
     */
    certificateBasedAuthUrl?: string;
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    forgotPasswordEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    hideForm?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    identifierFirstEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    inherited?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    magicLinkAuthEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    passwordlessDeviceNamingEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    passwordlessEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    passwordlessEnforcePasswordEnabled?: boolean;
    /**
     *
     * @type {number}
     * @memberof PatchLoginSettings
     */
    passwordlessEnforcePasswordMaxAge?: number;
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    passwordlessRememberDeviceEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    registerEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    rememberMeEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchLoginSettings
     */
    resetPasswordOnExpiration?: boolean;
}
/**
 * Check if a given object implements the PatchLoginSettings interface.
 */
export declare function instanceOfPatchLoginSettings(value: object): value is PatchLoginSettings;
export declare function PatchLoginSettingsFromJSON(json: any): PatchLoginSettings;
export declare function PatchLoginSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchLoginSettings;
export declare function PatchLoginSettingsToJSON(json: any): PatchLoginSettings;
export declare function PatchLoginSettingsToJSONTyped(value?: PatchLoginSettings | null, ignoreDiscriminator?: boolean): any;
