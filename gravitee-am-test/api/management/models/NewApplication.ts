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
 * @interface NewApplication
 */
export interface NewApplication {
    /**
     * 
     * @type {string}
     * @memberof NewApplication
     */
    name: string;
    /**
     * 
     * @type {string}
     * @memberof NewApplication
     */
    type: NewApplicationTypeEnum;
    /**
     * 
     * @type {string}
     * @memberof NewApplication
     */
    description?: string;
    /**
     * 
     * @type {string}
     * @memberof NewApplication
     */
    clientId?: string;
    /**
     * 
     * @type {string}
     * @memberof NewApplication
     */
    clientSecret?: string;
    /**
     * 
     * @type {Array<string>}
     * @memberof NewApplication
     */
    redirectUris?: Array<string>;
    /**
     * 
     * @type {{ [key: string]: any; }}
     * @memberof NewApplication
     */
    metadata?: { [key: string]: any; };
}


/**
 * @export
 */
export const NewApplicationTypeEnum = {
    Web: 'WEB',
    Native: 'NATIVE',
    Browser: 'BROWSER',
    Service: 'SERVICE',
    ResourceServer: 'RESOURCE_SERVER'
} as const;
export type NewApplicationTypeEnum = typeof NewApplicationTypeEnum[keyof typeof NewApplicationTypeEnum];


export function NewApplicationFromJSON(json: any): NewApplication {
    return NewApplicationFromJSONTyped(json, false);
}

export function NewApplicationFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewApplication {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'name': json['name'],
        'type': json['type'],
        'description': !exists(json, 'description') ? undefined : json['description'],
        'clientId': !exists(json, 'clientId') ? undefined : json['clientId'],
        'clientSecret': !exists(json, 'clientSecret') ? undefined : json['clientSecret'],
        'redirectUris': !exists(json, 'redirectUris') ? undefined : json['redirectUris'],
        'metadata': !exists(json, 'metadata') ? undefined : json['metadata'],
    };
}

export function NewApplicationToJSON(value?: NewApplication | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'name': value.name,
        'type': value.type,
        'description': value.description,
        'clientId': value.clientId,
        'clientSecret': value.clientSecret,
        'redirectUris': value.redirectUris,
        'metadata': value.metadata,
    };
}

