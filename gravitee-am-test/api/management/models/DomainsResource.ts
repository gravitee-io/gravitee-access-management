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
import { DomainResource, DomainResourceFromJSON, DomainResourceFromJSONTyped, DomainResourceToJSON } from './DomainResource';

/**
 *
 * @export
 * @interface DomainsResource
 */
export interface DomainsResource {
  /**
   *
   * @type {DomainResource}
   * @memberof DomainsResource
   */
  domainResource?: DomainResource;
}

export function DomainsResourceFromJSON(json: any): DomainsResource {
  return DomainsResourceFromJSONTyped(json, false);
}

export function DomainsResourceFromJSONTyped(json: any, ignoreDiscriminator: boolean): DomainsResource {
  if (json === undefined || json === null) {
    return json;
  }
  return {
    domainResource: !exists(json, 'domainResource') ? undefined : DomainResourceFromJSON(json['domainResource']),
  };
}

export function DomainsResourceToJSON(value?: DomainsResource | null): any {
  if (value === undefined) {
    return undefined;
  }
  if (value === null) {
    return null;
  }
  return {
    domainResource: DomainResourceToJSON(value.domainResource),
  };
}
