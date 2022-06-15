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
 * @interface UpdateDeviceIdentifier
 */
export interface UpdateDeviceIdentifier {
    /**
     * 
     * @type {string}
     * @memberof UpdateDeviceIdentifier
     */
    name: string;
    /**
     * 
     * @type {string}
     * @memberof UpdateDeviceIdentifier
     */
    configuration: string;
}

export function UpdateDeviceIdentifierFromJSON(json: any): UpdateDeviceIdentifier {
    return UpdateDeviceIdentifierFromJSONTyped(json, false);
}

export function UpdateDeviceIdentifierFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateDeviceIdentifier {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'name': json['name'],
        'configuration': json['configuration'],
    };
}

export function UpdateDeviceIdentifierToJSON(value?: UpdateDeviceIdentifier | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'name': value.name,
        'configuration': value.configuration,
    };
}

