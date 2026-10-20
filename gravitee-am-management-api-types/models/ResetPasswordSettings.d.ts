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
 * Rules applied to a self-service password reset.
 * @export
 * @interface ResetPasswordSettings
 */
export interface ResetPasswordSettings {
    /**
     * Whether the user must supply their current password to set a new one.
     * @type {boolean}
     * @memberof ResetPasswordSettings
     */
    oldPasswordRequired?: boolean;
    /**
     * Lifetime, in seconds, of the password-reset token.
     * @type {number}
     * @memberof ResetPasswordSettings
     */
    tokenAge?: number;
}
/**
 * Check if a given object implements the ResetPasswordSettings interface.
 */
export declare function instanceOfResetPasswordSettings(value: object): value is ResetPasswordSettings;
export declare function ResetPasswordSettingsFromJSON(json: any): ResetPasswordSettings;
export declare function ResetPasswordSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): ResetPasswordSettings;
export declare function ResetPasswordSettingsToJSON(json: any): ResetPasswordSettings;
export declare function ResetPasswordSettingsToJSONTyped(value?: ResetPasswordSettings | null, ignoreDiscriminator?: boolean): any;
