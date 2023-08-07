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
/**
 *
 * @export
 * @interface ErrorEntity
 */
export interface ErrorEntity {
  /**
   *
   * @type {string}
   * @memberof ErrorEntity
   */
  message?: string;
  /**
   *
   * @type {number}
   * @memberof ErrorEntity
   */
  http_status?: number;
}

export function ErrorEntityFromJSON(json: any): ErrorEntity {
  return ErrorEntityFromJSONTyped(json, false);
}

export function ErrorEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): ErrorEntity {
  if (json === undefined || json === null) {
    return json;
  }
  return {
    message: !exists(json, 'message') ? undefined : json['message'],
    http_status: !exists(json, 'http_status') ? undefined : json['http_status'],
  };
}

export function ErrorEntityToJSON(value?: ErrorEntity | null): any {
  if (value === undefined) {
    return undefined;
  }
  if (value === null) {
    return null;
  }
  return {
    message: value.message,
    http_status: value.http_status,
  };
}
