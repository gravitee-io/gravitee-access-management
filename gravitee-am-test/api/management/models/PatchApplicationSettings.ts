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
/* Gravitee.io - Access Management API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

/* tslint:disable */
/* eslint-disable */
import { exists, mapValues } from '../runtime';
import {
    AccountSettings,
    AccountSettingsFromJSON,
    AccountSettingsFromJSONTyped,
    AccountSettingsToJSON,
} from './AccountSettings';
import {
    CookieSettings,
    CookieSettingsFromJSON,
    CookieSettingsFromJSONTyped,
    CookieSettingsToJSON,
} from './CookieSettings';
import {
    LoginSettings,
    LoginSettingsFromJSON,
    LoginSettingsFromJSONTyped,
    LoginSettingsToJSON,
} from './LoginSettings';
import {
    PatchApplicationAdvancedSettings,
    PatchApplicationAdvancedSettingsFromJSON,
    PatchApplicationAdvancedSettingsFromJSONTyped,
    PatchApplicationAdvancedSettingsToJSON,
} from './PatchApplicationAdvancedSettings';
import {
    PatchApplicationOAuthSettings,
    PatchApplicationOAuthSettingsFromJSON,
    PatchApplicationOAuthSettingsFromJSONTyped,
    PatchApplicationOAuthSettingsToJSON,
} from './PatchApplicationOAuthSettings';
import {
    PatchApplicationSAMLSettings,
    PatchApplicationSAMLSettingsFromJSON,
    PatchApplicationSAMLSettingsFromJSONTyped,
    PatchApplicationSAMLSettingsToJSON,
} from './PatchApplicationSAMLSettings';
import {
    PatchMFASettings,
    PatchMFASettingsFromJSON,
    PatchMFASettingsFromJSONTyped,
    PatchMFASettingsToJSON,
} from './PatchMFASettings';
import {
    PatchPasswordSettings,
    PatchPasswordSettingsFromJSON,
    PatchPasswordSettingsFromJSONTyped,
    PatchPasswordSettingsToJSON,
} from './PatchPasswordSettings';
import {
    RiskAssessmentSettings,
    RiskAssessmentSettingsFromJSON,
    RiskAssessmentSettingsFromJSONTyped,
    RiskAssessmentSettingsToJSON,
} from './RiskAssessmentSettings';

/**
 * 
 * @export
 * @interface PatchApplicationSettings
 */
export interface PatchApplicationSettings {
    /**
     * 
     * @type {AccountSettings}
     * @memberof PatchApplicationSettings
     */
    account?: AccountSettings;
    /**
     * 
     * @type {LoginSettings}
     * @memberof PatchApplicationSettings
     */
    login?: LoginSettings;
    /**
     * 
     * @type {PatchApplicationOAuthSettings}
     * @memberof PatchApplicationSettings
     */
    oauth?: PatchApplicationOAuthSettings;
    /**
     * 
     * @type {PatchApplicationSAMLSettings}
     * @memberof PatchApplicationSettings
     */
    saml?: PatchApplicationSAMLSettings;
    /**
     * 
     * @type {PatchApplicationAdvancedSettings}
     * @memberof PatchApplicationSettings
     */
    advanced?: PatchApplicationAdvancedSettings;
    /**
     * 
     * @type {PatchPasswordSettings}
     * @memberof PatchApplicationSettings
     */
    passwordSettings?: PatchPasswordSettings;
    /**
     * 
     * @type {PatchMFASettings}
     * @memberof PatchApplicationSettings
     */
    mfa?: PatchMFASettings;
    /**
     * 
     * @type {CookieSettings}
     * @memberof PatchApplicationSettings
     */
    cookieSettings?: CookieSettings;
    /**
     * 
     * @type {RiskAssessmentSettings}
     * @memberof PatchApplicationSettings
     */
    riskAssessment?: RiskAssessmentSettings;
    /**
     * 
     * @type {Set<string>}
     * @memberof PatchApplicationSettings
     */
    requiredPermissions?: Set<PatchApplicationSettingsRequiredPermissionsEnum>;
}


/**
 * @export
 */
export const PatchApplicationSettingsRequiredPermissionsEnum = {
    Organization: 'ORGANIZATION',
    OrganizationSettings: 'ORGANIZATION_SETTINGS',
    OrganizationIdentityProvider: 'ORGANIZATION_IDENTITY_PROVIDER',
    OrganizationAudit: 'ORGANIZATION_AUDIT',
    OrganizationReporter: 'ORGANIZATION_REPORTER',
    OrganizationScope: 'ORGANIZATION_SCOPE',
    OrganizationUser: 'ORGANIZATION_USER',
    OrganizationGroup: 'ORGANIZATION_GROUP',
    OrganizationRole: 'ORGANIZATION_ROLE',
    OrganizationTag: 'ORGANIZATION_TAG',
    OrganizationEntrypoint: 'ORGANIZATION_ENTRYPOINT',
    OrganizationForm: 'ORGANIZATION_FORM',
    OrganizationMember: 'ORGANIZATION_MEMBER',
    Environment: 'ENVIRONMENT',
    Domain: 'DOMAIN',
    DomainSettings: 'DOMAIN_SETTINGS',
    DomainForm: 'DOMAIN_FORM',
    DomainEmailTemplate: 'DOMAIN_EMAIL_TEMPLATE',
    DomainExtensionPoint: 'DOMAIN_EXTENSION_POINT',
    DomainIdentityProvider: 'DOMAIN_IDENTITY_PROVIDER',
    DomainAudit: 'DOMAIN_AUDIT',
    DomainCertificate: 'DOMAIN_CERTIFICATE',
    DomainUser: 'DOMAIN_USER',
    DomainUserDevice: 'DOMAIN_USER_DEVICE',
    DomainGroup: 'DOMAIN_GROUP',
    DomainRole: 'DOMAIN_ROLE',
    DomainScim: 'DOMAIN_SCIM',
    DomainScope: 'DOMAIN_SCOPE',
    DomainExtensionGrant: 'DOMAIN_EXTENSION_GRANT',
    DomainOpenid: 'DOMAIN_OPENID',
    DomainSaml: 'DOMAIN_SAML',
    DomainUma: 'DOMAIN_UMA',
    DomainUmaScope: 'DOMAIN_UMA_SCOPE',
    DomainReporter: 'DOMAIN_REPORTER',
    DomainMember: 'DOMAIN_MEMBER',
    DomainAnalytics: 'DOMAIN_ANALYTICS',
    DomainFactor: 'DOMAIN_FACTOR',
    DomainResource: 'DOMAIN_RESOURCE',
    DomainFlow: 'DOMAIN_FLOW',
    DomainAlert: 'DOMAIN_ALERT',
    DomainAlertNotifier: 'DOMAIN_ALERT_NOTIFIER',
    DomainBotDetection: 'DOMAIN_BOT_DETECTION',
    DomainDeviceIdentifier: 'DOMAIN_DEVICE_IDENTIFIER',
    DomainAuthdeviceNotifier: 'DOMAIN_AUTHDEVICE_NOTIFIER',
    DomainI18NDictionary: 'DOMAIN_I18N_DICTIONARY',
    DomainTheme: 'DOMAIN_THEME',
    Application: 'APPLICATION',
    ApplicationSettings: 'APPLICATION_SETTINGS',
    ApplicationIdentityProvider: 'APPLICATION_IDENTITY_PROVIDER',
    ApplicationForm: 'APPLICATION_FORM',
    ApplicationEmailTemplate: 'APPLICATION_EMAIL_TEMPLATE',
    ApplicationOpenid: 'APPLICATION_OPENID',
    ApplicationSaml: 'APPLICATION_SAML',
    ApplicationCertificate: 'APPLICATION_CERTIFICATE',
    ApplicationMember: 'APPLICATION_MEMBER',
    ApplicationFactor: 'APPLICATION_FACTOR',
    ApplicationResource: 'APPLICATION_RESOURCE',
    ApplicationAnalytics: 'APPLICATION_ANALYTICS',
    ApplicationFlow: 'APPLICATION_FLOW',
    LicenseNotification: 'LICENSE_NOTIFICATION',
    Installation: 'INSTALLATION'
} as const;
export type PatchApplicationSettingsRequiredPermissionsEnum = typeof PatchApplicationSettingsRequiredPermissionsEnum[keyof typeof PatchApplicationSettingsRequiredPermissionsEnum];


export function PatchApplicationSettingsFromJSON(json: any): PatchApplicationSettings {
    return PatchApplicationSettingsFromJSONTyped(json, false);
}

export function PatchApplicationSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchApplicationSettings {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'account': !exists(json, 'account') ? undefined : AccountSettingsFromJSON(json['account']),
        'login': !exists(json, 'login') ? undefined : LoginSettingsFromJSON(json['login']),
        'oauth': !exists(json, 'oauth') ? undefined : PatchApplicationOAuthSettingsFromJSON(json['oauth']),
        'saml': !exists(json, 'saml') ? undefined : PatchApplicationSAMLSettingsFromJSON(json['saml']),
        'advanced': !exists(json, 'advanced') ? undefined : PatchApplicationAdvancedSettingsFromJSON(json['advanced']),
        'passwordSettings': !exists(json, 'passwordSettings') ? undefined : PatchPasswordSettingsFromJSON(json['passwordSettings']),
        'mfa': !exists(json, 'mfa') ? undefined : PatchMFASettingsFromJSON(json['mfa']),
        'cookieSettings': !exists(json, 'cookieSettings') ? undefined : CookieSettingsFromJSON(json['cookieSettings']),
        'riskAssessment': !exists(json, 'riskAssessment') ? undefined : RiskAssessmentSettingsFromJSON(json['riskAssessment']),
        'requiredPermissions': !exists(json, 'requiredPermissions') ? undefined : json['requiredPermissions'],
    };
}

export function PatchApplicationSettingsToJSON(value?: PatchApplicationSettings | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'account': AccountSettingsToJSON(value.account),
        'login': LoginSettingsToJSON(value.login),
        'oauth': PatchApplicationOAuthSettingsToJSON(value.oauth),
        'saml': PatchApplicationSAMLSettingsToJSON(value.saml),
        'advanced': PatchApplicationAdvancedSettingsToJSON(value.advanced),
        'passwordSettings': PatchPasswordSettingsToJSON(value.passwordSettings),
        'mfa': PatchMFASettingsToJSON(value.mfa),
        'cookieSettings': CookieSettingsToJSON(value.cookieSettings),
        'riskAssessment': RiskAssessmentSettingsToJSON(value.riskAssessment),
        'requiredPermissions': value.requiredPermissions,
    };
}

