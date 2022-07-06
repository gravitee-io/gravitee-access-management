/* tslint:disable */
/* eslint-disable */
/**
 * Gravitee.io - Access Management API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

import { exists, mapValues } from '../runtime';
import {
    PatchApplicationIdentityProvider,
    PatchApplicationIdentityProviderFromJSON,
    PatchApplicationIdentityProviderFromJSONTyped,
    PatchApplicationIdentityProviderToJSON,
} from './PatchApplicationIdentityProvider';
import {
    PatchApplicationSettings,
    PatchApplicationSettingsFromJSON,
    PatchApplicationSettingsFromJSONTyped,
    PatchApplicationSettingsToJSON,
} from './PatchApplicationSettings';

/**
 * 
 * @export
 * @interface PatchApplication
 */
export interface PatchApplication {
    /**
     * 
     * @type {string}
     * @memberof PatchApplication
     */
    name?: string;
    /**
     * 
     * @type {string}
     * @memberof PatchApplication
     */
    description?: string;
    /**
     * 
     * @type {boolean}
     * @memberof PatchApplication
     */
    enabled?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof PatchApplication
     */
    template?: boolean;
    /**
     * 
     * @type {Set<PatchApplicationIdentityProvider>}
     * @memberof PatchApplication
     */
    identityProviders?: Set<PatchApplicationIdentityProvider>;
    /**
     * 
     * @type {Set<string>}
     * @memberof PatchApplication
     */
    factors?: Set<string>;
    /**
     * 
     * @type {string}
     * @memberof PatchApplication
     */
    certificate?: string;
    /**
     * 
     * @type {{ [key: string]: any; }}
     * @memberof PatchApplication
     */
    metadata?: { [key: string]: any; };
    /**
     * 
     * @type {PatchApplicationSettings}
     * @memberof PatchApplication
     */
    settings?: PatchApplicationSettings;
    /**
     * 
     * @type {Set<string>}
     * @memberof PatchApplication
     */
    requiredPermissions?: Set<PatchApplicationRequiredPermissionsEnum>;
}


/**
 * @export
 */
export const PatchApplicationRequiredPermissionsEnum = {
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
    Installation: 'INSTALLATION'
} as const;
export type PatchApplicationRequiredPermissionsEnum = typeof PatchApplicationRequiredPermissionsEnum[keyof typeof PatchApplicationRequiredPermissionsEnum];


export function PatchApplicationFromJSON(json: any): PatchApplication {
    return PatchApplicationFromJSONTyped(json, false);
}

export function PatchApplicationFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchApplication {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'name': !exists(json, 'name') ? undefined : json['name'],
        'description': !exists(json, 'description') ? undefined : json['description'],
        'enabled': !exists(json, 'enabled') ? undefined : json['enabled'],
        'template': !exists(json, 'template') ? undefined : json['template'],
        'identityProviders': !exists(json, 'identityProviders') ? undefined : (new Set((json['identityProviders'] as Array<any>).map(PatchApplicationIdentityProviderFromJSON))),
        'factors': !exists(json, 'factors') ? undefined : json['factors'],
        'certificate': !exists(json, 'certificate') ? undefined : json['certificate'],
        'metadata': !exists(json, 'metadata') ? undefined : json['metadata'],
        'settings': !exists(json, 'settings') ? undefined : PatchApplicationSettingsFromJSON(json['settings']),
        'requiredPermissions': !exists(json, 'requiredPermissions') ? undefined : json['requiredPermissions'],
    };
}

export function PatchApplicationToJSON(value?: PatchApplication | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'name': value.name,
        'description': value.description,
        'enabled': value.enabled,
        'template': value.template,
        'identityProviders': value.identityProviders === undefined ? undefined : (Array.from(value.identityProviders as Set<any>).map(PatchApplicationIdentityProviderToJSON)),
        'factors': value.factors,
        'certificate': value.certificate,
        'metadata': value.metadata,
        'settings': PatchApplicationSettingsToJSON(value.settings),
        'requiredPermissions': value.requiredPermissions,
    };
}

