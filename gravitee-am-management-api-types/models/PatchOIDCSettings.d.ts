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
import type { PatchCIMDSettings } from './PatchCIMDSettings';
import type { PatchCIBASettings } from './PatchCIBASettings';
import type { PatchSpiffeDomainSettings } from './PatchSpiffeDomainSettings';
import type { PatchDPoPSettings } from './PatchDPoPSettings';
import type { PatchClientRegistrationSettings } from './PatchClientRegistrationSettings';
import type { PatchSecurityProfileSettings } from './PatchSecurityProfileSettings';
/**
 *
 * @export
 * @interface PatchOIDCSettings
 */
export interface PatchOIDCSettings {
    /**
     *
     * @type {PatchCIBASettings}
     * @memberof PatchOIDCSettings
     */
    cibaSettings?: PatchCIBASettings;
    /**
     *
     * @type {PatchCIMDSettings}
     * @memberof PatchOIDCSettings
     */
    cimdSettings?: PatchCIMDSettings;
    /**
     *
     * @type {PatchClientRegistrationSettings}
     * @memberof PatchOIDCSettings
     */
    clientRegistrationSettings?: PatchClientRegistrationSettings;
    /**
     *
     * @type {PatchDPoPSettings}
     * @memberof PatchOIDCSettings
     */
    dpopSettings?: PatchDPoPSettings;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchOIDCSettings
     */
    postLogoutRedirectUris?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof PatchOIDCSettings
     */
    redirectUriStrictMatching?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchOIDCSettings
     */
    requestUris?: Array<string>;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchOIDCSettings
     */
    requiredPermissions?: Array<PatchOIDCSettingsRequiredPermissionsEnum>;
    /**
     *
     * @type {PatchSecurityProfileSettings}
     * @memberof PatchOIDCSettings
     */
    securityProfileSettings?: PatchSecurityProfileSettings;
    /**
     *
     * @type {PatchSpiffeDomainSettings}
     * @memberof PatchOIDCSettings
     */
    workloadIdentitySettings?: PatchSpiffeDomainSettings;
}
/**
 * @export
 */
export declare const PatchOIDCSettingsRequiredPermissionsEnum: {
    readonly Organization: "ORGANIZATION";
    readonly OrganizationSettings: "ORGANIZATION_SETTINGS";
    readonly OrganizationIdentityProvider: "ORGANIZATION_IDENTITY_PROVIDER";
    readonly OrganizationAudit: "ORGANIZATION_AUDIT";
    readonly OrganizationReporter: "ORGANIZATION_REPORTER";
    readonly OrganizationScope: "ORGANIZATION_SCOPE";
    readonly OrganizationUser: "ORGANIZATION_USER";
    readonly OrganizationGroup: "ORGANIZATION_GROUP";
    readonly OrganizationRole: "ORGANIZATION_ROLE";
    readonly OrganizationTag: "ORGANIZATION_TAG";
    readonly OrganizationEntrypoint: "ORGANIZATION_ENTRYPOINT";
    readonly OrganizationForm: "ORGANIZATION_FORM";
    readonly OrganizationMember: "ORGANIZATION_MEMBER";
    readonly Environment: "ENVIRONMENT";
    readonly DataPlane: "DATA_PLANE";
    readonly Domain: "DOMAIN";
    readonly DomainSettings: "DOMAIN_SETTINGS";
    readonly DomainForm: "DOMAIN_FORM";
    readonly DomainEmailTemplate: "DOMAIN_EMAIL_TEMPLATE";
    readonly DomainExtensionPoint: "DOMAIN_EXTENSION_POINT";
    readonly DomainIdentityProvider: "DOMAIN_IDENTITY_PROVIDER";
    readonly DomainAuthorizationEngine: "DOMAIN_AUTHORIZATION_ENGINE";
    readonly DomainAudit: "DOMAIN_AUDIT";
    readonly DomainCertificate: "DOMAIN_CERTIFICATE";
    readonly DomainUser: "DOMAIN_USER";
    readonly DomainUserDevice: "DOMAIN_USER_DEVICE";
    readonly DomainGroup: "DOMAIN_GROUP";
    readonly DomainRole: "DOMAIN_ROLE";
    readonly DomainScim: "DOMAIN_SCIM";
    readonly DomainScope: "DOMAIN_SCOPE";
    readonly DomainExtensionGrant: "DOMAIN_EXTENSION_GRANT";
    readonly DomainOpenid: "DOMAIN_OPENID";
    readonly DomainSaml: "DOMAIN_SAML";
    readonly DomainUma: "DOMAIN_UMA";
    readonly DomainUmaScope: "DOMAIN_UMA_SCOPE";
    readonly DomainReporter: "DOMAIN_REPORTER";
    readonly DomainMember: "DOMAIN_MEMBER";
    readonly DomainAnalytics: "DOMAIN_ANALYTICS";
    readonly DomainFactor: "DOMAIN_FACTOR";
    readonly DomainResource: "DOMAIN_RESOURCE";
    readonly DomainFlow: "DOMAIN_FLOW";
    readonly DomainAlert: "DOMAIN_ALERT";
    readonly DomainAlertNotifier: "DOMAIN_ALERT_NOTIFIER";
    readonly DomainBotDetection: "DOMAIN_BOT_DETECTION";
    readonly DomainDeviceIdentifier: "DOMAIN_DEVICE_IDENTIFIER";
    readonly DomainAuthdeviceNotifier: "DOMAIN_AUTHDEVICE_NOTIFIER";
    readonly DomainI18NDictionary: "DOMAIN_I18N_DICTIONARY";
    readonly DomainTheme: "DOMAIN_THEME";
    readonly DomainTrustDomain: "DOMAIN_TRUST_DOMAIN";
    readonly Application: "APPLICATION";
    readonly ApplicationSettings: "APPLICATION_SETTINGS";
    readonly ApplicationIdentityProvider: "APPLICATION_IDENTITY_PROVIDER";
    readonly ApplicationForm: "APPLICATION_FORM";
    readonly ApplicationEmailTemplate: "APPLICATION_EMAIL_TEMPLATE";
    readonly ApplicationOpenid: "APPLICATION_OPENID";
    readonly ApplicationSaml: "APPLICATION_SAML";
    readonly ApplicationCertificate: "APPLICATION_CERTIFICATE";
    readonly ApplicationMember: "APPLICATION_MEMBER";
    readonly ApplicationFactor: "APPLICATION_FACTOR";
    readonly ApplicationResource: "APPLICATION_RESOURCE";
    readonly ApplicationAnalytics: "APPLICATION_ANALYTICS";
    readonly ApplicationFlow: "APPLICATION_FLOW";
    readonly LicenseNotification: "LICENSE_NOTIFICATION";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
    readonly ProtectedResourceMember: "PROTECTED_RESOURCE_MEMBER";
    readonly ProtectedResourceSettings: "PROTECTED_RESOURCE_SETTINGS";
    readonly ProtectedResourceOauth: "PROTECTED_RESOURCE_OAUTH";
    readonly ProtectedResourceCertificate: "PROTECTED_RESOURCE_CERTIFICATE";
    readonly ProtectedResourceFlow: "PROTECTED_RESOURCE_FLOW";
    readonly Installation: "INSTALLATION";
};
export type PatchOIDCSettingsRequiredPermissionsEnum = typeof PatchOIDCSettingsRequiredPermissionsEnum[keyof typeof PatchOIDCSettingsRequiredPermissionsEnum];
/**
 * Check if a given object implements the PatchOIDCSettings interface.
 */
export declare function instanceOfPatchOIDCSettings(value: object): value is PatchOIDCSettings;
export declare function PatchOIDCSettingsFromJSON(json: any): PatchOIDCSettings;
export declare function PatchOIDCSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchOIDCSettings;
export declare function PatchOIDCSettingsToJSON(json: any): PatchOIDCSettings;
export declare function PatchOIDCSettingsToJSONTyped(value?: PatchOIDCSettings | null, ignoreDiscriminator?: boolean): any;
