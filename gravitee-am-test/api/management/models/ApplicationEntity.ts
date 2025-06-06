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
 * @interface ApplicationEntity
 */
export interface ApplicationEntity {
  /**
   *
   * @type {string}
   * @memberof ApplicationEntity
   */
  id?: string;
  /**
   *
   * @type {string}
   * @memberof ApplicationEntity
   */
  clientId?: string;
  /**
   *
   * @type {string}
   * @memberof ApplicationEntity
   */
  name?: string;
}

export function ApplicationEntityFromJSON(json: any): ApplicationEntity {
  return ApplicationEntityFromJSONTyped(json, false);
}

export function ApplicationEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationEntity {
  if (json === undefined || json === null) {
    return json;
  }
  return {
    id: !exists(json, 'id') ? undefined : json['id'],
    clientId: !exists(json, 'clientId') ? undefined : json['clientId'],
    name: !exists(json, 'name') ? undefined : json['name'],
  };
}

export function ApplicationEntityToJSON(value?: ApplicationEntity | null): any {
  if (value === undefined) {
    return undefined;
  }
  if (value === null) {
    return null;
  }
  return {
    id: value.id,
    clientId: value.clientId,
    name: value.name,
  };
}
