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
import type { AccountSettings } from './AccountSettings';
import type { ApplicationOAuthSettings } from './ApplicationOAuthSettings';
import type { ApplicationAdvancedSettings } from './ApplicationAdvancedSettings';
import type { ApplicationSAMLSettings } from './ApplicationSAMLSettings';
import type { CookieSettings } from './CookieSettings';
import type { RiskAssessmentSettings } from './RiskAssessmentSettings';
import type { MFASettings } from './MFASettings';
import type { LoginSettings } from './LoginSettings';
import type { SecretExpirationSettings } from './SecretExpirationSettings';
import type { SpiffeApplicationSettings } from './SpiffeApplicationSettings';
/**
 *
 * @export
 * @interface ApplicationSettings
 */
export interface ApplicationSettings {
    /**
     *
     * @type {AccountSettings}
     * @memberof ApplicationSettings
     */
    account?: AccountSettings;
    /**
     *
     * @type {ApplicationAdvancedSettings}
     * @memberof ApplicationSettings
     */
    advanced?: ApplicationAdvancedSettings;
    /**
     *
     * @type {CookieSettings}
     * @memberof ApplicationSettings
     */
    cookieSettings?: CookieSettings;
    /**
     *
     * @type {LoginSettings}
     * @memberof ApplicationSettings
     */
    login?: LoginSettings;
    /**
     *
     * @type {MFASettings}
     * @memberof ApplicationSettings
     */
    mfa?: MFASettings;
    /**
     *
     * @type {ApplicationOAuthSettings}
     * @memberof ApplicationSettings
     */
    oauth?: ApplicationOAuthSettings;
    /**
     *
     * @type {RiskAssessmentSettings}
     * @memberof ApplicationSettings
     */
    riskAssessment?: RiskAssessmentSettings;
    /**
     *
     * @type {ApplicationSAMLSettings}
     * @memberof ApplicationSettings
     */
    saml?: ApplicationSAMLSettings;
    /**
     *
     * @type {SecretExpirationSettings}
     * @memberof ApplicationSettings
     */
    secretExpirationSettings?: SecretExpirationSettings;
    /**
     *
     * @type {SpiffeApplicationSettings}
     * @memberof ApplicationSettings
     */
    workloadIdentitySettings?: SpiffeApplicationSettings;
}
/**
 * Check if a given object implements the ApplicationSettings interface.
 */
export declare function instanceOfApplicationSettings(value: object): value is ApplicationSettings;
export declare function ApplicationSettingsFromJSON(json: any): ApplicationSettings;
export declare function ApplicationSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationSettings;
export declare function ApplicationSettingsToJSON(json: any): ApplicationSettings;
export declare function ApplicationSettingsToJSONTyped(value?: ApplicationSettings | null, ignoreDiscriminator?: boolean): any;
