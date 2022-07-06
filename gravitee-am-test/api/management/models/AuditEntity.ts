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
 * @interface AuditEntity
 */
export interface AuditEntity {
    /**
     * 
     * @type {string}
     * @memberof AuditEntity
     */
    id?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditEntity
     */
    alternativeId?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditEntity
     */
    type?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditEntity
     */
    displayName?: string;
    /**
     * 
     * @type {string}
     * @memberof AuditEntity
     */
    referenceType?: AuditEntityReferenceTypeEnum;
    /**
     * 
     * @type {string}
     * @memberof AuditEntity
     */
    referenceId?: string;
    /**
     * 
     * @type {{ [key: string]: any; }}
     * @memberof AuditEntity
     */
    attributes?: { [key: string]: any; };
}


/**
 * @export
 */
export const AuditEntityReferenceTypeEnum = {
    Platform: 'PLATFORM',
    Domain: 'DOMAIN',
    Application: 'APPLICATION',
    Organization: 'ORGANIZATION',
    Environment: 'ENVIRONMENT'
} as const;
export type AuditEntityReferenceTypeEnum = typeof AuditEntityReferenceTypeEnum[keyof typeof AuditEntityReferenceTypeEnum];


export function AuditEntityFromJSON(json: any): AuditEntity {
    return AuditEntityFromJSONTyped(json, false);
}

export function AuditEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): AuditEntity {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'id': !exists(json, 'id') ? undefined : json['id'],
        'alternativeId': !exists(json, 'alternativeId') ? undefined : json['alternativeId'],
        'type': !exists(json, 'type') ? undefined : json['type'],
        'displayName': !exists(json, 'displayName') ? undefined : json['displayName'],
        'referenceType': !exists(json, 'referenceType') ? undefined : json['referenceType'],
        'referenceId': !exists(json, 'referenceId') ? undefined : json['referenceId'],
        'attributes': !exists(json, 'attributes') ? undefined : json['attributes'],
    };
}

export function AuditEntityToJSON(value?: AuditEntity | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'id': value.id,
        'alternativeId': value.alternativeId,
        'type': value.type,
        'displayName': value.displayName,
        'referenceType': value.referenceType,
        'referenceId': value.referenceId,
        'attributes': value.attributes,
    };
}

