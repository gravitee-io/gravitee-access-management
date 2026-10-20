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
import type { SCIMSettings } from './SCIMSettings';
import type { CertificateSettings } from './CertificateSettings';
import type { PatchPasswordSettings } from './PatchPasswordSettings';
import type { CorsSettings } from './CorsSettings';
import type { SecretExpirationSettings } from './SecretExpirationSettings';
import type { PatchKeyRetrievalSettings } from './PatchKeyRetrievalSettings';
import type { PatchSAMLSettings } from './PatchSAMLSettings';
import type { WebAuthnSettings } from './WebAuthnSettings';
import type { UMASettings } from './UMASettings';
import type { AccountSettings } from './AccountSettings';
import type { PatchLoginSettings } from './PatchLoginSettings';
import type { PatchOIDCSettings } from './PatchOIDCSettings';
import type { SelfServiceAccountManagementSettings } from './SelfServiceAccountManagementSettings';
import type { TokenExchangeSettings } from './TokenExchangeSettings';
import type { WebProtectionSettings } from './WebProtectionSettings';
import type { VirtualHost } from './VirtualHost';
/**
 *
 * @export
 * @interface PatchDomain
 */
export interface PatchDomain {
    /**
     *
     * @type {AccountSettings}
     * @memberof PatchDomain
     */
    accountSettings?: AccountSettings;
    /**
     *
     * @type {boolean}
     * @memberof PatchDomain
     */
    alertEnabled?: boolean;
    /**
     *
     * @type {CertificateSettings}
     * @memberof PatchDomain
     */
    certificateSettings?: CertificateSettings;
    /**
     *
     * @type {CorsSettings}
     * @memberof PatchDomain
     */
    corsSettings?: CorsSettings;
    /**
     *
     * @type {string}
     * @memberof PatchDomain
     */
    dataPlaneId?: string;
    /**
     *
     * @type {string}
     * @memberof PatchDomain
     */
    description?: string;
    /**
     *
     * @type {boolean}
     * @memberof PatchDomain
     */
    enabled?: boolean;
    /**
     *
     * @type {PatchKeyRetrievalSettings}
     * @memberof PatchDomain
     */
    keyRetrievalSettings?: PatchKeyRetrievalSettings;
    /**
     *
     * @type {PatchLoginSettings}
     * @memberof PatchDomain
     */
    loginSettings?: PatchLoginSettings;
    /**
     *
     * @type {boolean}
     * @memberof PatchDomain
     */
    master?: boolean;
    /**
     *
     * @type {string}
     * @memberof PatchDomain
     */
    name?: string;
    /**
     *
     * @type {PatchOIDCSettings}
     * @memberof PatchDomain
     */
    oidc?: PatchOIDCSettings;
    /**
     *
     * @type {PatchPasswordSettings}
     * @memberof PatchDomain
     */
    passwordSettings?: PatchPasswordSettings;
    /**
     *
     * @type {string}
     * @memberof PatchDomain
     */
    path?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchDomain
     */
    requiredPermissions?: Array<PatchDomainRequiredPermissionsEnum>;
    /**
     *
     * @type {PatchSAMLSettings}
     * @memberof PatchDomain
     */
    saml?: PatchSAMLSettings;
    /**
     *
     * @type {SCIMSettings}
     * @memberof PatchDomain
     */
    scim?: SCIMSettings;
    /**
     *
     * @type {SecretExpirationSettings}
     * @memberof PatchDomain
     */
    secretSettings?: SecretExpirationSettings;
    /**
     *
     * @type {SelfServiceAccountManagementSettings}
     * @memberof PatchDomain
     */
    selfServiceAccountManagementSettings?: SelfServiceAccountManagementSettings;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchDomain
     */
    tags?: Array<string>;
    /**
     *
     * @type {TokenExchangeSettings}
     * @memberof PatchDomain
     */
    tokenExchangeSettings?: TokenExchangeSettings;
    /**
     *
     * @type {UMASettings}
     * @memberof PatchDomain
     */
    uma?: UMASettings;
    /**
     *
     * @type {boolean}
     * @memberof PatchDomain
     */
    vhostMode?: boolean;
    /**
     *
     * @type {Array<VirtualHost>}
     * @memberof PatchDomain
     */
    vhosts?: Array<VirtualHost>;
    /**
     *
     * @type {WebAuthnSettings}
     * @memberof PatchDomain
     */
    webAuthnSettings?: WebAuthnSettings;
    /**
     *
     * @type {WebProtectionSettings}
     * @memberof PatchDomain
     */
    webProtectionSettings?: WebProtectionSettings;
}
/**
 * @export
 */
export declare const PatchDomainRequiredPermissionsEnum: {
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
export type PatchDomainRequiredPermissionsEnum = typeof PatchDomainRequiredPermissionsEnum[keyof typeof PatchDomainRequiredPermissionsEnum];
/**
 * Check if a given object implements the PatchDomain interface.
 */
export declare function instanceOfPatchDomain(value: object): value is PatchDomain;
export declare function PatchDomainFromJSON(json: any): PatchDomain;
export declare function PatchDomainFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchDomain;
export declare function PatchDomainToJSON(json: any): PatchDomain;
export declare function PatchDomainToJSONTyped(value?: PatchDomain | null, ignoreDiscriminator?: boolean): any;
