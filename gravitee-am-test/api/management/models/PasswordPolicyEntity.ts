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
 * @interface PasswordPolicyEntity
 */
export interface PasswordPolicyEntity {
  /**
   *
   * @type {string}
   * @memberof PasswordPolicyEntity
   */
  id?: string;
  /**
   *
   * @type {string}
   * @memberof PasswordPolicyEntity
   */
  name?: string;
  /**
   *
   * @type {boolean}
   * @memberof PasswordPolicyEntity
   */
  isDefault?: boolean;
  /**
   *
   * @type {Array<string>}
   * @memberof PasswordPolicyEntity
   */
  idpsNames?: Array<string>;
}

export function PasswordPolicyEntityFromJSON(json: any): PasswordPolicyEntity {
  return PasswordPolicyEntityFromJSONTyped(json, false);
}

export function PasswordPolicyEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): PasswordPolicyEntity {
  if (json === undefined || json === null) {
    return json;
  }
  return {
    id: !exists(json, 'id') ? undefined : json['id'],
    name: !exists(json, 'name') ? undefined : json['name'],
    isDefault: !exists(json, 'isDefault') ? undefined : json['isDefault'],
    idpsNames: !exists(json, 'idpsNames') ? undefined : json['idpsNames'],
  };
}

export function PasswordPolicyEntityToJSON(value?: PasswordPolicyEntity | null): any {
  if (value === undefined) {
    return undefined;
  }
  if (value === null) {
    return null;
  }
  return {
    id: value.id,
    name: value.name,
    isDefault: value.isDefault,
    idpsNames: value.idpsNames,
  };
}
