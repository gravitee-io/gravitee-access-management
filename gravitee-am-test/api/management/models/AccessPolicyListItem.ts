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
 * @interface AccessPolicyListItem
 */
export interface AccessPolicyListItem {
    /**
     * 
     * @type {string}
     * @memberof AccessPolicyListItem
     */
    id?: string;
    /**
     * 
     * @type {string}
     * @memberof AccessPolicyListItem
     */
    name?: string;
    /**
     * 
     * @type {string}
     * @memberof AccessPolicyListItem
     */
    description?: string;
    /**
     * 
     * @type {Date}
     * @memberof AccessPolicyListItem
     */
    createdAt?: Date;
    /**
     * 
     * @type {Date}
     * @memberof AccessPolicyListItem
     */
    updatedAt?: Date;
}

export function AccessPolicyListItemFromJSON(json: any): AccessPolicyListItem {
    return AccessPolicyListItemFromJSONTyped(json, false);
}

export function AccessPolicyListItemFromJSONTyped(json: any, ignoreDiscriminator: boolean): AccessPolicyListItem {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'id': !exists(json, 'id') ? undefined : json['id'],
        'name': !exists(json, 'name') ? undefined : json['name'],
        'description': !exists(json, 'description') ? undefined : json['description'],
        'createdAt': !exists(json, 'createdAt') ? undefined : (new Date(json['createdAt'])),
        'updatedAt': !exists(json, 'updatedAt') ? undefined : (new Date(json['updatedAt'])),
    };
}

export function AccessPolicyListItemToJSON(value?: AccessPolicyListItem | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'id': value.id,
        'name': value.name,
        'description': value.description,
        'createdAt': value.createdAt === undefined ? undefined : (value.createdAt.toISOString()),
        'updatedAt': value.updatedAt === undefined ? undefined : (value.updatedAt.toISOString()),
    };
}

