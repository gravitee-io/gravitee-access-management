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
 * @interface FactorsResource
 */
export interface FactorsResource {
    /**
     * 
     * @type {any}
     * @memberof FactorsResource
     */
    factorResource?: any;
}

export function FactorsResourceFromJSON(json: any): FactorsResource {
    return FactorsResourceFromJSONTyped(json, false);
}

export function FactorsResourceFromJSONTyped(json: any, ignoreDiscriminator: boolean): FactorsResource {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'factorResource': !exists(json, 'factorResource') ? undefined : json['factorResource'],
    };
}

export function FactorsResourceToJSON(value?: FactorsResource | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'factorResource': value.factorResource,
    };
}

