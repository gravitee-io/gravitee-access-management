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
 * @interface ExtensionGrant
 */
export interface ExtensionGrant {
    /**
     * 
     * @type {string}
     * @memberof ExtensionGrant
     */
    id?: string;
    /**
     * 
     * @type {string}
     * @memberof ExtensionGrant
     */
    name?: string;
    /**
     * 
     * @type {string}
     * @memberof ExtensionGrant
     */
    type?: string;
    /**
     * 
     * @type {string}
     * @memberof ExtensionGrant
     */
    configuration?: string;
    /**
     * 
     * @type {string}
     * @memberof ExtensionGrant
     */
    domain?: string;
    /**
     * 
     * @type {string}
     * @memberof ExtensionGrant
     */
    grantType?: string;
    /**
     * 
     * @type {string}
     * @memberof ExtensionGrant
     */
    identityProvider?: string;
    /**
     * 
     * @type {boolean}
     * @memberof ExtensionGrant
     */
    createUser?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof ExtensionGrant
     */
    userExists?: boolean;
    /**
     * 
     * @type {number}
     * @memberof ExtensionGrant
     */
    createdAt?: number;
    /**
     * 
     * @type {number}
     * @memberof ExtensionGrant
     */
    updatedAt?: number;
}

export function ExtensionGrantFromJSON(json: any): ExtensionGrant {
    return ExtensionGrantFromJSONTyped(json, false);
}

export function ExtensionGrantFromJSONTyped(json: any, ignoreDiscriminator: boolean): ExtensionGrant {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'id': !exists(json, 'id') ? undefined : json['id'],
        'name': !exists(json, 'name') ? undefined : json['name'],
        'type': !exists(json, 'type') ? undefined : json['type'],
        'configuration': !exists(json, 'configuration') ? undefined : json['configuration'],
        'domain': !exists(json, 'domain') ? undefined : json['domain'],
        'grantType': !exists(json, 'grantType') ? undefined : json['grantType'],
        'identityProvider': !exists(json, 'identityProvider') ? undefined : json['identityProvider'],
        'createUser': !exists(json, 'createUser') ? undefined : json['createUser'],
        'userExists': !exists(json, 'userExists') ? undefined : json['userExists'],
        'createdAt': !exists(json, 'createdAt') ? undefined : json['createdAt'],
        'updatedAt': !exists(json, 'updatedAt') ? undefined : json['updatedAt'],
    };
}

export function ExtensionGrantToJSON(value?: ExtensionGrant | null): any {
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
        'domain': value.domain,
        'grantType': value.grantType,
        'identityProvider': value.identityProvider,
        'createUser': value.createUser,
        'userExists': value.userExists,
        'createdAt': value.createdAt,
        'updatedAt': value.updatedAt,
    };
}

