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
 * @interface SecurityProfileSettings
 */
export interface SecurityProfileSettings {
  /**
   *
   * @type {boolean}
   * @memberof SecurityProfileSettings
   */
  enablePlainFapi?: boolean;
  /**
   *
   * @type {boolean}
   * @memberof SecurityProfileSettings
   */
  enableFapiBrazil?: boolean;
}

export function SecurityProfileSettingsFromJSON(json: any): SecurityProfileSettings {
  return SecurityProfileSettingsFromJSONTyped(json, false);
}

export function SecurityProfileSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): SecurityProfileSettings {
  if (json === undefined || json === null) {
    return json;
  }
  return {
    enablePlainFapi: !exists(json, 'enablePlainFapi') ? undefined : json['enablePlainFapi'],
    enableFapiBrazil: !exists(json, 'enableFapiBrazil') ? undefined : json['enableFapiBrazil'],
  };
}

export function SecurityProfileSettingsToJSON(value?: SecurityProfileSettings | null): any {
  if (value === undefined) {
    return undefined;
  }
  if (value === null) {
    return null;
  }
  return {
    enablePlainFapi: value.enablePlainFapi,
    enableFapiBrazil: value.enableFapiBrazil,
  };
}
