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
 * @interface EnrollmentSettings
 */
export interface EnrollmentSettings {
    /**
     * 
     * @type {boolean}
     * @memberof EnrollmentSettings
     */
    forceEnrollment?: boolean;
    /**
     * 
     * @type {number}
     * @memberof EnrollmentSettings
     */
    skipTimeSeconds?: number;
}

export function EnrollmentSettingsFromJSON(json: any): EnrollmentSettings {
    return EnrollmentSettingsFromJSONTyped(json, false);
}

export function EnrollmentSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): EnrollmentSettings {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'forceEnrollment': !exists(json, 'forceEnrollment') ? undefined : json['forceEnrollment'],
        'skipTimeSeconds': !exists(json, 'skipTimeSeconds') ? undefined : json['skipTimeSeconds'],
    };
}

export function EnrollmentSettingsToJSON(value?: EnrollmentSettings | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'forceEnrollment': value.forceEnrollment,
        'skipTimeSeconds': value.skipTimeSeconds,
    };
}

