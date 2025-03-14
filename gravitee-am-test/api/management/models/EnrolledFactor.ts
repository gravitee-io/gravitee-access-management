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
  EnrolledFactorChannel,
  EnrolledFactorChannelFromJSON,
  EnrolledFactorChannelFromJSONTyped,
  EnrolledFactorChannelToJSON,
} from './EnrolledFactorChannel';
import {
  EnrolledFactorSecurity,
  EnrolledFactorSecurityFromJSON,
  EnrolledFactorSecurityFromJSONTyped,
  EnrolledFactorSecurityToJSON,
} from './EnrolledFactorSecurity';

/**
 *
 * @export
 * @interface EnrolledFactor
 */
export interface EnrolledFactor {
  /**
   *
   * @type {string}
   * @memberof EnrolledFactor
   */
  factorId?: string;
  /**
   *
   * @type {string}
   * @memberof EnrolledFactor
   */
  appId?: string;
  /**
   *
   * @type {string}
   * @memberof EnrolledFactor
   */
  status?: EnrolledFactorStatusEnum;
  /**
   *
   * @type {EnrolledFactorSecurity}
   * @memberof EnrolledFactor
   */
  security?: EnrolledFactorSecurity;
  /**
   *
   * @type {EnrolledFactorChannel}
   * @memberof EnrolledFactor
   */
  channel?: EnrolledFactorChannel;
  /**
   *
   * @type {boolean}
   * @memberof EnrolledFactor
   */
  primary?: boolean;
  /**
   *
   * @type {Date}
   * @memberof EnrolledFactor
   */
  createdAt?: Date;
  /**
   *
   * @type {Date}
   * @memberof EnrolledFactor
   */
  updatedAt?: Date;
}

/**
 * @export
 */
export const EnrolledFactorStatusEnum = {
  PendingActivation: 'PENDING_ACTIVATION',
  Activated: 'ACTIVATED',
  Revoked: 'REVOKED',
  Null: 'NULL',
} as const;
export type EnrolledFactorStatusEnum = typeof EnrolledFactorStatusEnum[keyof typeof EnrolledFactorStatusEnum];

export function EnrolledFactorFromJSON(json: any): EnrolledFactor {
  return EnrolledFactorFromJSONTyped(json, false);
}

export function EnrolledFactorFromJSONTyped(json: any, ignoreDiscriminator: boolean): EnrolledFactor {
  if (json === undefined || json === null) {
    return json;
  }
  return {
    factorId: !exists(json, 'factorId') ? undefined : json['factorId'],
    appId: !exists(json, 'appId') ? undefined : json['appId'],
    status: !exists(json, 'status') ? undefined : json['status'],
    security: !exists(json, 'security') ? undefined : EnrolledFactorSecurityFromJSON(json['security']),
    channel: !exists(json, 'channel') ? undefined : EnrolledFactorChannelFromJSON(json['channel']),
    primary: !exists(json, 'primary') ? undefined : json['primary'],
    createdAt: !exists(json, 'createdAt') ? undefined : new Date(json['createdAt']),
    updatedAt: !exists(json, 'updatedAt') ? undefined : new Date(json['updatedAt']),
  };
}

export function EnrolledFactorToJSON(value?: EnrolledFactor | null): any {
  if (value === undefined) {
    return undefined;
  }
  if (value === null) {
    return null;
  }
  return {
    factorId: value.factorId,
    appId: value.appId,
    status: value.status,
    security: EnrolledFactorSecurityToJSON(value.security),
    channel: EnrolledFactorChannelToJSON(value.channel),
    primary: value.primary,
    createdAt: value.createdAt === undefined ? undefined : value.createdAt.toISOString(),
    updatedAt: value.updatedAt === undefined ? undefined : value.updatedAt.toISOString(),
  };
}
