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
 * @interface UpdateIdentityProvider
 */
export interface UpdateIdentityProvider {
  /**
   *
   * @type {string}
   * @memberof UpdateIdentityProvider
   */
  name: string;
  /**
   *
   * @type {string}
   * @memberof UpdateIdentityProvider
   */
  type: string;
  /**
   *
   * @type {string}
   * @memberof UpdateIdentityProvider
   */
  configuration: string;
  /**
   *
   * @type {{ [key: string]: string; }}
   * @memberof UpdateIdentityProvider
   */
  mappers?: { [key: string]: string };
  /**
   *
   * @type {{ [key: string]: Array<string>; }}
   * @memberof UpdateIdentityProvider
   */
  roleMapper?: { [key: string]: Array<string> };
  /**
   *
   * @type {{ [key: string]: Array<string>; }}
   * @memberof UpdateIdentityProvider
   */
  groupMapper?: { [key: string]: Array<string> };
  /**
   *
   * @type {Array<string>}
   * @memberof UpdateIdentityProvider
   */
  domainWhitelist?: Array<string>;
  /**
   *
   * @type {string}
   * @memberof UpdateIdentityProvider
   */
  passwordPolicy?: string;
}

export function UpdateIdentityProviderFromJSON(json: any): UpdateIdentityProvider {
  return UpdateIdentityProviderFromJSONTyped(json, false);
}

export function UpdateIdentityProviderFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateIdentityProvider {
  if (json === undefined || json === null) {
    return json;
  }
  return {
    name: json['name'],
    type: json['type'],
    configuration: json['configuration'],
    mappers: !exists(json, 'mappers') ? undefined : json['mappers'],
    roleMapper: !exists(json, 'roleMapper') ? undefined : json['roleMapper'],
    groupMapper: !exists(json, 'groupMapper') ? undefined : json['groupMapper'],
    domainWhitelist: !exists(json, 'domainWhitelist') ? undefined : json['domainWhitelist'],
    passwordPolicy: !exists(json, 'passwordPolicy') ? undefined : json['passwordPolicy'],
  };
}

export function UpdateIdentityProviderToJSON(value?: UpdateIdentityProvider | null): any {
  if (value === undefined) {
    return undefined;
  }
  if (value === null) {
    return null;
  }
  return {
    name: value.name,
    type: value.type,
    configuration: value.configuration,
    mappers: value.mappers,
    roleMapper: value.roleMapper,
    groupMapper: value.groupMapper,
    domainWhitelist: value.domainWhitelist,
    passwordPolicy: value.passwordPolicy,
  };
}
