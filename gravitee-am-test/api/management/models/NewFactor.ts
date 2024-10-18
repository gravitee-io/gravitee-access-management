/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* Gravitee.io - Access Management API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

/* tslint:disable */
/* eslint-disable */
import { exists, mapValues } from '../runtime';
/**
 * 
 * @export
 * @interface NewFactor
 */
export interface NewFactor {
    /**
     * 
     * @type {string}
     * @memberof NewFactor
     */
    id?: string;
    /**
     * 
     * @type {string}
     * @memberof NewFactor
     */
    type: string;
    /**
     * 
     * @type {string}
     * @memberof NewFactor
     */
    factorType: string;
    /**
     * 
     * @type {string}
     * @memberof NewFactor
     */
    name: string;
    /**
     * 
     * @type {string}
     * @memberof NewFactor
     */
    configuration: string;
}

export function NewFactorFromJSON(json: any): NewFactor {
    return NewFactorFromJSONTyped(json, false);
}

export function NewFactorFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewFactor {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'id': !exists(json, 'id') ? undefined : json['id'],
        'type': json['type'],
        'factorType': json['factorType'],
        'name': json['name'],
        'configuration': json['configuration'],
    };
}

export function NewFactorToJSON(value?: NewFactor | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'id': value.id,
        'type': value.type,
        'factorType': value.factorType,
        'name': value.name,
        'configuration': value.configuration,
    };
}

