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
 * @interface NotifierPlugin
 */
export interface NotifierPlugin {
    /**
     * 
     * @type {string}
     * @memberof NotifierPlugin
     */
    id?: string;
    /**
     * 
     * @type {string}
     * @memberof NotifierPlugin
     */
    name?: string;
    /**
     * 
     * @type {string}
     * @memberof NotifierPlugin
     */
    description?: string;
    /**
     * 
     * @type {string}
     * @memberof NotifierPlugin
     */
    version?: string;
    /**
     * 
     * @type {string}
     * @memberof NotifierPlugin
     */
    displayName?: string;
    /**
     * 
     * @type {string}
     * @memberof NotifierPlugin
     */
    icon?: string;
}

export function NotifierPluginFromJSON(json: any): NotifierPlugin {
    return NotifierPluginFromJSONTyped(json, false);
}

export function NotifierPluginFromJSONTyped(json: any, ignoreDiscriminator: boolean): NotifierPlugin {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'id': !exists(json, 'id') ? undefined : json['id'],
        'name': !exists(json, 'name') ? undefined : json['name'],
        'description': !exists(json, 'description') ? undefined : json['description'],
        'version': !exists(json, 'version') ? undefined : json['version'],
        'displayName': !exists(json, 'displayName') ? undefined : json['displayName'],
        'icon': !exists(json, 'icon') ? undefined : json['icon'],
    };
}

export function NotifierPluginToJSON(value?: NotifierPlugin | null): any {
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
        'version': value.version,
        'displayName': value.displayName,
        'icon': value.icon,
    };
}

