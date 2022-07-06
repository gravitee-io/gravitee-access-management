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
 * @interface UpdateIdentityProvider
 */
export interface UpdateIdentityProvider {
    /**
     * 
     * @type {string}
     * @memberof UpdateIdentityProvider
     */
    name: string;
    /**
     * 
     * @type {string}
     * @memberof UpdateIdentityProvider
     */
    configuration: string;
    /**
     * 
     * @type {{ [key: string]: string; }}
     * @memberof UpdateIdentityProvider
     */
    mappers?: { [key: string]: string; };
    /**
     * 
     * @type {{ [key: string]: Array<string>; }}
     * @memberof UpdateIdentityProvider
     */
    roleMapper?: { [key: string]: Array<string>; };
    /**
     * 
     * @type {Array<string>}
     * @memberof UpdateIdentityProvider
     */
    domainWhitelist?: Array<string>;
}

export function UpdateIdentityProviderFromJSON(json: any): UpdateIdentityProvider {
    return UpdateIdentityProviderFromJSONTyped(json, false);
}

export function UpdateIdentityProviderFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateIdentityProvider {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'name': json['name'],
        'configuration': json['configuration'],
        'mappers': !exists(json, 'mappers') ? undefined : json['mappers'],
        'roleMapper': !exists(json, 'roleMapper') ? undefined : json['roleMapper'],
        'domainWhitelist': !exists(json, 'domainWhitelist') ? undefined : json['domainWhitelist'],
    };
}

export function UpdateIdentityProviderToJSON(value?: UpdateIdentityProvider | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'name': value.name,
        'configuration': value.configuration,
        'mappers': value.mappers,
        'roleMapper': value.roleMapper,
        'domainWhitelist': value.domainWhitelist,
    };
}

