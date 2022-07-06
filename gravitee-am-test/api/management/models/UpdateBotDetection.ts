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
 * @interface UpdateBotDetection
 */
export interface UpdateBotDetection {
    /**
     * 
     * @type {string}
     * @memberof UpdateBotDetection
     */
    name: string;
    /**
     * 
     * @type {string}
     * @memberof UpdateBotDetection
     */
    configuration: string;
}

export function UpdateBotDetectionFromJSON(json: any): UpdateBotDetection {
    return UpdateBotDetectionFromJSONTyped(json, false);
}

export function UpdateBotDetectionFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateBotDetection {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'name': json['name'],
        'configuration': json['configuration'],
    };
}

export function UpdateBotDetectionToJSON(value?: UpdateBotDetection | null): any {
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

