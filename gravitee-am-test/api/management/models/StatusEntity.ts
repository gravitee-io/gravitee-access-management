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
 * @interface StatusEntity
 */
export interface StatusEntity {
  /**
   *
   * @type {boolean}
   * @memberof StatusEntity
   */
  enabled?: boolean;
}

export function StatusEntityFromJSON(json: any): StatusEntity {
  return StatusEntityFromJSONTyped(json, false);
}

export function StatusEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): StatusEntity {
  if (json === undefined || json === null) {
    return json;
  }
  return {
    enabled: !exists(json, 'enabled') ? undefined : json['enabled'],
  };
}

export function StatusEntityToJSON(value?: StatusEntity | null): any {
  if (value === undefined) {
    return undefined;
  }
  if (value === null) {
    return null;
  }
  return {
    enabled: value.enabled,
  };
}
