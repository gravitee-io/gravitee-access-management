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
import type { PatchEnrollSettings } from './PatchEnrollSettings';
import type { PatchRememberDeviceSettings } from './PatchRememberDeviceSettings';
import type { PatchChallengeSettings } from './PatchChallengeSettings';
import type { PatchEnrollmentSettings } from './PatchEnrollmentSettings';
import type { PatchFactorSettings } from './PatchFactorSettings';
import type { PatchStepUpAuthentication } from './PatchStepUpAuthentication';
/**
 *
 * @export
 * @interface PatchMFASettings
 */
export interface PatchMFASettings {
    /**
     *
     * @type {string}
     * @memberof PatchMFASettings
     */
    adaptiveAuthenticationRule?: string;
    /**
     *
     * @type {PatchChallengeSettings}
     * @memberof PatchMFASettings
     */
    challenge?: PatchChallengeSettings;
    /**
     *
     * @type {PatchEnrollSettings}
     * @memberof PatchMFASettings
     */
    enroll?: PatchEnrollSettings;
    /**
     *
     * @type {PatchEnrollmentSettings}
     * @memberof PatchMFASettings
     */
    enrollment?: PatchEnrollmentSettings;
    /**
     *
     * @type {PatchFactorSettings}
     * @memberof PatchMFASettings
     */
    factor?: PatchFactorSettings;
    /**
     *
     * @type {string}
     * @memberof PatchMFASettings
     */
    loginRule?: string;
    /**
     *
     * @type {PatchRememberDeviceSettings}
     * @memberof PatchMFASettings
     */
    rememberDevice?: PatchRememberDeviceSettings;
    /**
     *
     * @type {PatchStepUpAuthentication}
     * @memberof PatchMFASettings
     */
    stepUpAuthentication?: PatchStepUpAuthentication;
    /**
     *
     * @type {string}
     * @memberof PatchMFASettings
     */
    stepUpAuthenticationRule?: string;
}
/**
 * Check if a given object implements the PatchMFASettings interface.
 */
export declare function instanceOfPatchMFASettings(value: object): value is PatchMFASettings;
export declare function PatchMFASettingsFromJSON(json: any): PatchMFASettings;
export declare function PatchMFASettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchMFASettings;
export declare function PatchMFASettingsToJSON(json: any): PatchMFASettings;
export declare function PatchMFASettingsToJSONTyped(value?: PatchMFASettings | null, ignoreDiscriminator?: boolean): any;
