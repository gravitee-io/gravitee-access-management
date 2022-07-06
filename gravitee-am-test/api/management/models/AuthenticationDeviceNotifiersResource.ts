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
 * @interface AuthenticationDeviceNotifiersResource
 */
export interface AuthenticationDeviceNotifiersResource {
    /**
     * 
     * @type {any}
     * @memberof AuthenticationDeviceNotifiersResource
     */
    authenticationDeviceNotifierResource?: any;
}

export function AuthenticationDeviceNotifiersResourceFromJSON(json: any): AuthenticationDeviceNotifiersResource {
    return AuthenticationDeviceNotifiersResourceFromJSONTyped(json, false);
}

export function AuthenticationDeviceNotifiersResourceFromJSONTyped(json: any, ignoreDiscriminator: boolean): AuthenticationDeviceNotifiersResource {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'authenticationDeviceNotifierResource': !exists(json, 'authenticationDeviceNotifierResource') ? undefined : json['authenticationDeviceNotifierResource'],
    };
}

export function AuthenticationDeviceNotifiersResourceToJSON(value?: AuthenticationDeviceNotifiersResource | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'authenticationDeviceNotifierResource': value.authenticationDeviceNotifierResource,
    };
}

