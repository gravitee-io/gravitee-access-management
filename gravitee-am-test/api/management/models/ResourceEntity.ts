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
 * @interface ResourceEntity
 */
export interface ResourceEntity {
  /**
   *
   * @type {string}
   * @memberof ResourceEntity
   */
  id?: string;
  /**
   *
   * @type {Array<string>}
   * @memberof ResourceEntity
   */
  resourceScopes?: Array<string>;
  /**
   *
   * @type {string}
   * @memberof ResourceEntity
   */
  description?: string;
  /**
   *
   * @type {string}
   * @memberof ResourceEntity
   */
  iconUri?: string;
  /**
   *
   * @type {string}
   * @memberof ResourceEntity
   */
  name?: string;
  /**
   *
   * @type {string}
   * @memberof ResourceEntity
   */
  type?: string;
  /**
   *
   * @type {string}
   * @memberof ResourceEntity
   */
  domain?: string;
  /**
   *
   * @type {string}
   * @memberof ResourceEntity
   */
  userId?: string;
  /**
   *
   * @type {string}
   * @memberof ResourceEntity
   */
  userDisplayName?: string;
  /**
   *
   * @type {string}
   * @memberof ResourceEntity
   */
  clientId?: string;
  /**
   *
   * @type {number}
   * @memberof ResourceEntity
   */
  policies?: number;
  /**
   *
   * @type {Date}
   * @memberof ResourceEntity
   */
  createdAt?: Date;
  /**
   *
   * @type {Date}
   * @memberof ResourceEntity
   */
  updatedAt?: Date;
}

export function ResourceEntityFromJSON(json: any): ResourceEntity {
  return ResourceEntityFromJSONTyped(json, false);
}

export function ResourceEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): ResourceEntity {
  if (json === undefined || json === null) {
    return json;
  }
  return {
    id: !exists(json, 'id') ? undefined : json['id'],
    resourceScopes: !exists(json, 'resourceScopes') ? undefined : json['resourceScopes'],
    description: !exists(json, 'description') ? undefined : json['description'],
    iconUri: !exists(json, 'iconUri') ? undefined : json['iconUri'],
    name: !exists(json, 'name') ? undefined : json['name'],
    type: !exists(json, 'type') ? undefined : json['type'],
    domain: !exists(json, 'domain') ? undefined : json['domain'],
    userId: !exists(json, 'userId') ? undefined : json['userId'],
    userDisplayName: !exists(json, 'userDisplayName') ? undefined : json['userDisplayName'],
    clientId: !exists(json, 'clientId') ? undefined : json['clientId'],
    policies: !exists(json, 'policies') ? undefined : json['policies'],
    createdAt: !exists(json, 'createdAt') ? undefined : new Date(json['createdAt']),
    updatedAt: !exists(json, 'updatedAt') ? undefined : new Date(json['updatedAt']),
  };
}

export function ResourceEntityToJSON(value?: ResourceEntity | null): any {
  if (value === undefined) {
    return undefined;
  }
  if (value === null) {
    return null;
  }
  return {
    id: value.id,
    resourceScopes: value.resourceScopes,
    description: value.description,
    iconUri: value.iconUri,
    name: value.name,
    type: value.type,
    domain: value.domain,
    userId: value.userId,
    userDisplayName: value.userDisplayName,
    clientId: value.clientId,
    policies: value.policies,
    createdAt: value.createdAt === undefined ? undefined : value.createdAt.toISOString(),
    updatedAt: value.updatedAt === undefined ? undefined : value.updatedAt.toISOString(),
  };
}
