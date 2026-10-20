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
import type { CIMDSettings } from './CIMDSettings';
import type { CIBASettings } from './CIBASettings';
import type { ClientRegistrationSettings } from './ClientRegistrationSettings';
import type { SecurityProfileSettings } from './SecurityProfileSettings';
import type { DPoPSettings } from './DPoPSettings';
import type { SpiffeDomainSettings } from './SpiffeDomainSettings';
/**
 * OpenID Connect configuration for the domain, including dynamic client registration, security profile, and logout settings.
 * @export
 * @interface OIDCSettings
 */
export interface OIDCSettings {
    /**
     *
     * @type {CIBASettings}
     * @memberof OIDCSettings
     */
    cibaSettings?: CIBASettings;
    /**
     *
     * @type {CIMDSettings}
     * @memberof OIDCSettings
     */
    cimdSettings?: CIMDSettings;
    /**
     *
     * @type {ClientRegistrationSettings}
     * @memberof OIDCSettings
     */
    clientRegistrationSettings?: ClientRegistrationSettings;
    /**
     *
     * @type {DPoPSettings}
     * @memberof OIDCSettings
     */
    dpopSettings?: DPoPSettings;
    /**
     * URLs to which a relying party may request the user be redirected after logout, via the post_logout_redirect_uri parameter.
     * @type {Array<string>}
     * @memberof OIDCSettings
     */
    postLogoutRedirectUris?: Array<string>;
    /**
     * Whether redirect_uri values are matched strictly during OIDC flows.
     * @type {boolean}
     * @memberof OIDCSettings
     */
    redirectUriStrictMatching?: boolean;
    /**
     * URLs that restrict the permitted values of the request_uri parameter.
     * @type {Array<string>}
     * @memberof OIDCSettings
     */
    requestUris?: Array<string>;
    /**
     *
     * @type {SecurityProfileSettings}
     * @memberof OIDCSettings
     */
    securityProfileSettings?: SecurityProfileSettings;
    /**
     *
     * @type {SpiffeDomainSettings}
     * @memberof OIDCSettings
     */
    workloadIdentitySettings?: SpiffeDomainSettings;
}
/**
 * Check if a given object implements the OIDCSettings interface.
 */
export declare function instanceOfOIDCSettings(value: object): value is OIDCSettings;
export declare function OIDCSettingsFromJSON(json: any): OIDCSettings;
export declare function OIDCSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): OIDCSettings;
export declare function OIDCSettingsToJSON(json: any): OIDCSettings;
export declare function OIDCSettingsToJSONTyped(value?: OIDCSettings | null, ignoreDiscriminator?: boolean): any;
