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
import {
    ResourceEntity,
    ResourceEntityFromJSON,
    ResourceEntityFromJSONTyped,
    ResourceEntityToJSON,
} from './ResourceEntity';

/**
 * 
 * @export
 * @interface ResourceListItem
 */
export interface ResourceListItem {
    /**
     * 
     * @type {Array<ResourceEntity>}
     * @memberof ResourceListItem
     */
    resources?: Array<ResourceEntity>;
    /**
     * 
     * @type {{ [key: string]: { [key: string]: any; }; }}
     * @memberof ResourceListItem
     */
    metadata?: { [key: string]: { [key: string]: any; }; };
}

export function ResourceListItemFromJSON(json: any): ResourceListItem {
    return ResourceListItemFromJSONTyped(json, false);
}

export function ResourceListItemFromJSONTyped(json: any, ignoreDiscriminator: boolean): ResourceListItem {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'resources': !exists(json, 'resources') ? undefined : ((json['resources'] as Array<any>).map(ResourceEntityFromJSON)),
        'metadata': !exists(json, 'metadata') ? undefined : json['metadata'],
    };
}

export function ResourceListItemToJSON(value?: ResourceListItem | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'resources': value.resources === undefined ? undefined : ((value.resources as Array<any>).map(ResourceEntityToJSON)),
        'metadata': value.metadata,
    };
}

