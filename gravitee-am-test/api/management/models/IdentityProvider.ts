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
/**
 * 
 * @export
 * @interface IdentityProvider
 */
export interface IdentityProvider {
    /**
     * 
     * @type {string}
     * @memberof IdentityProvider
     */
    id?: string;
    /**
     * 
     * @type {string}
     * @memberof IdentityProvider
     */
    name?: string;
    /**
     * 
     * @type {string}
     * @memberof IdentityProvider
     */
    type?: string;
    /**
     * 
     * @type {boolean}
     * @memberof IdentityProvider
     */
    system?: boolean;
    /**
     * 
     * @type {string}
     * @memberof IdentityProvider
     */
    configuration?: string;
    /**
     * 
     * @type {{ [key: string]: string; }}
     * @memberof IdentityProvider
     */
    mappers?: { [key: string]: string; };
    /**
     * 
     * @type {{ [key: string]: Array<string>; }}
     * @memberof IdentityProvider
     */
    roleMapper?: { [key: string]: Array<string>; };
    /**
     * 
     * @type {string}
     * @memberof IdentityProvider
     */
    referenceType?: IdentityProviderReferenceTypeEnum;
    /**
     * 
     * @type {string}
     * @memberof IdentityProvider
     */
    referenceId?: string;
    /**
     * 
     * @type {boolean}
     * @memberof IdentityProvider
     */
    external?: boolean;
    /**
     * 
     * @type {Array<string>}
     * @memberof IdentityProvider
     */
    domainWhitelist?: Array<string>;
    /**
     * 
     * @type {Date}
     * @memberof IdentityProvider
     */
    createdAt?: Date;
    /**
     * 
     * @type {Date}
     * @memberof IdentityProvider
     */
    updatedAt?: Date;
}


/**
 * @export
 */
export const IdentityProviderReferenceTypeEnum = {
    Platform: 'PLATFORM',
    Domain: 'DOMAIN',
    Application: 'APPLICATION',
    Organization: 'ORGANIZATION',
    Environment: 'ENVIRONMENT'
} as const;
export type IdentityProviderReferenceTypeEnum = typeof IdentityProviderReferenceTypeEnum[keyof typeof IdentityProviderReferenceTypeEnum];


export function IdentityProviderFromJSON(json: any): IdentityProvider {
    return IdentityProviderFromJSONTyped(json, false);
}

export function IdentityProviderFromJSONTyped(json: any, ignoreDiscriminator: boolean): IdentityProvider {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'id': !exists(json, 'id') ? undefined : json['id'],
        'name': !exists(json, 'name') ? undefined : json['name'],
        'type': !exists(json, 'type') ? undefined : json['type'],
        'system': !exists(json, 'system') ? undefined : json['system'],
        'configuration': !exists(json, 'configuration') ? undefined : json['configuration'],
        'mappers': !exists(json, 'mappers') ? undefined : json['mappers'],
        'roleMapper': !exists(json, 'roleMapper') ? undefined : json['roleMapper'],
        'referenceType': !exists(json, 'referenceType') ? undefined : json['referenceType'],
        'referenceId': !exists(json, 'referenceId') ? undefined : json['referenceId'],
        'external': !exists(json, 'external') ? undefined : json['external'],
        'domainWhitelist': !exists(json, 'domainWhitelist') ? undefined : json['domainWhitelist'],
        'createdAt': !exists(json, 'createdAt') ? undefined : (new Date(json['createdAt'])),
        'updatedAt': !exists(json, 'updatedAt') ? undefined : (new Date(json['updatedAt'])),
    };
}

export function IdentityProviderToJSON(value?: IdentityProvider | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'id': value.id,
        'name': value.name,
        'type': value.type,
        'system': value.system,
        'configuration': value.configuration,
        'mappers': value.mappers,
        'roleMapper': value.roleMapper,
        'referenceType': value.referenceType,
        'referenceId': value.referenceId,
        'external': value.external,
        'domainWhitelist': value.domainWhitelist,
        'createdAt': value.createdAt === undefined ? undefined : (value.createdAt.toISOString()),
        'updatedAt': value.updatedAt === undefined ? undefined : (value.updatedAt.toISOString()),
    };
}

