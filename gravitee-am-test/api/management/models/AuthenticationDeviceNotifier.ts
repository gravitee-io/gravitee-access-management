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
 * @interface AuthenticationDeviceNotifier
 */
export interface AuthenticationDeviceNotifier {
    /**
     * 
     * @type {string}
     * @memberof AuthenticationDeviceNotifier
     */
    id?: string;
    /**
     * 
     * @type {string}
     * @memberof AuthenticationDeviceNotifier
     */
    name?: string;
    /**
     * 
     * @type {string}
     * @memberof AuthenticationDeviceNotifier
     */
    type?: string;
    /**
     * 
     * @type {string}
     * @memberof AuthenticationDeviceNotifier
     */
    configuration?: string;
    /**
     * 
     * @type {string}
     * @memberof AuthenticationDeviceNotifier
     */
    referenceId?: string;
    /**
     * 
     * @type {string}
     * @memberof AuthenticationDeviceNotifier
     */
    referenceType?: AuthenticationDeviceNotifierReferenceTypeEnum;
    /**
     * 
     * @type {Date}
     * @memberof AuthenticationDeviceNotifier
     */
    createdAt?: Date;
    /**
     * 
     * @type {Date}
     * @memberof AuthenticationDeviceNotifier
     */
    updatedAt?: Date;
}


/**
 * @export
 */
export const AuthenticationDeviceNotifierReferenceTypeEnum = {
    Platform: 'PLATFORM',
    Domain: 'DOMAIN',
    Application: 'APPLICATION',
    Organization: 'ORGANIZATION',
    Environment: 'ENVIRONMENT'
} as const;
export type AuthenticationDeviceNotifierReferenceTypeEnum = typeof AuthenticationDeviceNotifierReferenceTypeEnum[keyof typeof AuthenticationDeviceNotifierReferenceTypeEnum];


export function AuthenticationDeviceNotifierFromJSON(json: any): AuthenticationDeviceNotifier {
    return AuthenticationDeviceNotifierFromJSONTyped(json, false);
}

export function AuthenticationDeviceNotifierFromJSONTyped(json: any, ignoreDiscriminator: boolean): AuthenticationDeviceNotifier {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'id': !exists(json, 'id') ? undefined : json['id'],
        'name': !exists(json, 'name') ? undefined : json['name'],
        'type': !exists(json, 'type') ? undefined : json['type'],
        'configuration': !exists(json, 'configuration') ? undefined : json['configuration'],
        'referenceId': !exists(json, 'referenceId') ? undefined : json['referenceId'],
        'referenceType': !exists(json, 'referenceType') ? undefined : json['referenceType'],
        'createdAt': !exists(json, 'createdAt') ? undefined : (new Date(json['createdAt'])),
        'updatedAt': !exists(json, 'updatedAt') ? undefined : (new Date(json['updatedAt'])),
    };
}

export function AuthenticationDeviceNotifierToJSON(value?: AuthenticationDeviceNotifier | null): any {
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
        'configuration': value.configuration,
        'referenceId': value.referenceId,
        'referenceType': value.referenceType,
        'createdAt': value.createdAt === undefined ? undefined : (value.createdAt.toISOString()),
        'updatedAt': value.updatedAt === undefined ? undefined : (value.updatedAt.toISOString()),
    };
}

