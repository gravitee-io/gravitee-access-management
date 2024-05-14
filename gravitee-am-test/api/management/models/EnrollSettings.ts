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

import { exists } from '../runtime';
/**
 *
 * @export
 * @interface EnrollSettings
 */
export interface EnrollSettings {
  /**
   *
   * @type {boolean}
   * @memberof EnrollSettings
   */
  active?: boolean;
  /**
   *
   * @type {boolean}
   * @memberof EnrollSettings
   */
  forceEnrollment?: boolean;
  /**
   *
   * @type {string}
   * @memberof EnrollSettings
   */
  enrollmentRule?: string;
  /**
   *
   * @type {number}
   * @memberof EnrollSettings
   */
  skipTimeSeconds?: number;
  /**
   *
   * @type {string}
   * @memberof EnrollSettings
   */
  type?: EnrollSettingsTypeEnum;
}

/**
 * @export
 */
export const EnrollSettingsTypeEnum = {
  Optional: 'OPTIONAL',
  Required: 'REQUIRED',
  Conditional: 'CONDITIONAL',
} as const;
export type EnrollSettingsTypeEnum = typeof EnrollSettingsTypeEnum[keyof typeof EnrollSettingsTypeEnum];

export function EnrollSettingsFromJSON(json: any): EnrollSettings {
  return EnrollSettingsFromJSONTyped(json, false);
}

export function EnrollSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): EnrollSettings {
  if (json == null) {
    return json;
  }
  return {
    active: !exists(json, 'active') ? undefined : json['active'],
    forceEnrollment: !exists(json, 'forceEnrollment') ? undefined : json['forceEnrollment'],
    enrollmentRule: !exists(json, 'enrollmentRule') ? undefined : json['enrollmentRule'],
    skipTimeSeconds: !exists(json, 'skipTimeSeconds') ? undefined : json['skipTimeSeconds'],
    type: !exists(json, 'type') ? undefined : json['type'],
  };
}

export function EnrollSettingsToJSON(value?: EnrollSettings | null): any {
  if (value == null) {
    return value;
  }
  return {
    active: value.active,
    forceEnrollment: value.forceEnrollment,
    enrollmentRule: value.enrollmentRule,
    skipTimeSeconds: value.skipTimeSeconds,
    type: value.type,
  };
}
