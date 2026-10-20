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
import type { ChallengeSettings } from './ChallengeSettings';
import type { RememberDeviceSettings } from './RememberDeviceSettings';
import type { StepUpAuthenticationSettings } from './StepUpAuthenticationSettings';
import type { EnrollSettings } from './EnrollSettings';
import type { EnrollmentSettings } from './EnrollmentSettings';
import type { FactorSettings } from './FactorSettings';
/**
 *
 * @export
 * @interface MFASettings
 */
export interface MFASettings {
    /**
     *
     * @type {string}
     * @memberof MFASettings
     */
    adaptiveAuthenticationRule?: string;
    /**
     *
     * @type {ChallengeSettings}
     * @memberof MFASettings
     */
    challenge?: ChallengeSettings;
    /**
     *
     * @type {EnrollSettings}
     * @memberof MFASettings
     */
    enroll?: EnrollSettings;
    /**
     *
     * @type {EnrollmentSettings}
     * @memberof MFASettings
     */
    enrollment?: EnrollmentSettings;
    /**
     *
     * @type {FactorSettings}
     * @memberof MFASettings
     */
    factor?: FactorSettings;
    /**
     *
     * @type {string}
     * @memberof MFASettings
     */
    loginRule?: string;
    /**
     *
     * @type {RememberDeviceSettings}
     * @memberof MFASettings
     */
    rememberDevice?: RememberDeviceSettings;
    /**
     *
     * @type {StepUpAuthenticationSettings}
     * @memberof MFASettings
     */
    stepUpAuthentication?: StepUpAuthenticationSettings;
    /**
     *
     * @type {string}
     * @memberof MFASettings
     */
    stepUpAuthenticationRule?: string;
}
/**
 * Check if a given object implements the MFASettings interface.
 */
export declare function instanceOfMFASettings(value: object): value is MFASettings;
export declare function MFASettingsFromJSON(json: any): MFASettings;
export declare function MFASettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): MFASettings;
export declare function MFASettingsToJSON(json: any): MFASettings;
export declare function MFASettingsToJSONTyped(value?: MFASettings | null, ignoreDiscriminator?: boolean): any;
