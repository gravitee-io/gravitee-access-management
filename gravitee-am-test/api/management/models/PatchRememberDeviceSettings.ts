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
 * @interface PatchRememberDeviceSettings
 */
export interface PatchRememberDeviceSettings {
    /**
     * 
     * @type {boolean}
     * @memberof PatchRememberDeviceSettings
     */
    active?: boolean;
    /**
     * 
     * @type {number}
     * @memberof PatchRememberDeviceSettings
     */
    expirationTimeSeconds?: number;
    /**
     * 
     * @type {string}
     * @memberof PatchRememberDeviceSettings
     */
    deviceIdentifierId?: string;
}

export function PatchRememberDeviceSettingsFromJSON(json: any): PatchRememberDeviceSettings {
    return PatchRememberDeviceSettingsFromJSONTyped(json, false);
}

export function PatchRememberDeviceSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchRememberDeviceSettings {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'active': !exists(json, 'active') ? undefined : json['active'],
        'expirationTimeSeconds': !exists(json, 'expirationTimeSeconds') ? undefined : json['expirationTimeSeconds'],
        'deviceIdentifierId': !exists(json, 'deviceIdentifierId') ? undefined : json['deviceIdentifierId'],
    };
}

export function PatchRememberDeviceSettingsToJSON(value?: PatchRememberDeviceSettings | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'active': value.active,
        'expirationTimeSeconds': value.expirationTimeSeconds,
        'deviceIdentifierId': value.deviceIdentifierId,
    };
}

