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
 * @interface UpdatePasswordPolicy
 */
export interface UpdatePasswordPolicy {
  /**
   *
   * @type {string}
   * @memberof UpdatePasswordPolicy
   */
  name: string;
  /**
   *
   * @type {number}
   * @memberof UpdatePasswordPolicy
   */
  minLength?: number;
  /**
   *
   * @type {number}
   * @memberof UpdatePasswordPolicy
   */
  maxLength?: number;
  /**
   *
   * @type {boolean}
   * @memberof UpdatePasswordPolicy
   */
  includeNumbers?: boolean;
  /**
   *
   * @type {boolean}
   * @memberof UpdatePasswordPolicy
   */
  includeSpecialCharacters?: boolean;
  /**
   *
   * @type {boolean}
   * @memberof UpdatePasswordPolicy
   */
  lettersInMixedCase?: boolean;
  /**
   *
   * @type {number}
   * @memberof UpdatePasswordPolicy
   */
  maxConsecutiveLetters?: number;
  /**
   *
   * @type {boolean}
   * @memberof UpdatePasswordPolicy
   */
  excludePasswordsInDictionary?: boolean;
  /**
   *
   * @type {boolean}
   * @memberof UpdatePasswordPolicy
   */
  excludeUserProfileInfoInPassword?: boolean;
  /**
   *
   * @type {number}
   * @memberof UpdatePasswordPolicy
   */
  expiryDuration?: number;
  /**
   *
   * @type {boolean}
   * @memberof UpdatePasswordPolicy
   */
  passwordHistoryEnabled?: boolean;
  /**
   *
   * @type {number}
   * @memberof UpdatePasswordPolicy
   */
  oldPasswords?: number;

  /**
   *
   * @type {boolean}
   * @memberof defaultPolicy
   */
  defaultPolicy?: boolean;
}

export function UpdatePasswordPolicyFromJSON(json: any): UpdatePasswordPolicy {
  return UpdatePasswordPolicyFromJSONTyped(json, false);
}

export function UpdatePasswordPolicyFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdatePasswordPolicy {
  if (json === undefined || json === null) {
    return json;
  }
  return {
    name: json['name'],
    minLength: !exists(json, 'minLength') ? undefined : json['minLength'],
    maxLength: !exists(json, 'maxLength') ? undefined : json['maxLength'],
    includeNumbers: !exists(json, 'includeNumbers') ? undefined : json['includeNumbers'],
    includeSpecialCharacters: !exists(json, 'includeSpecialCharacters') ? undefined : json['includeSpecialCharacters'],
    lettersInMixedCase: !exists(json, 'lettersInMixedCase') ? undefined : json['lettersInMixedCase'],
    maxConsecutiveLetters: !exists(json, 'maxConsecutiveLetters') ? undefined : json['maxConsecutiveLetters'],
    excludePasswordsInDictionary: !exists(json, 'excludePasswordsInDictionary') ? undefined : json['excludePasswordsInDictionary'],
    excludeUserProfileInfoInPassword: !exists(json, 'excludeUserProfileInfoInPassword')
      ? undefined
      : json['excludeUserProfileInfoInPassword'],
    expiryDuration: !exists(json, 'expiryDuration') ? undefined : json['expiryDuration'],
    passwordHistoryEnabled: !exists(json, 'passwordHistoryEnabled') ? undefined : json['passwordHistoryEnabled'],
    oldPasswords: !exists(json, 'oldPasswords') ? undefined : json['oldPasswords'],
    defaultPolicy: !exists(json, 'defaultPolicy') ? undefined : json['defaultPolicy'],
  };
}

export function UpdatePasswordPolicyToJSON(value?: UpdatePasswordPolicy | null): any {
  if (value === undefined) {
    return undefined;
  }
  if (value === null) {
    return null;
  }
  return {
    name: value.name,
    minLength: value.minLength,
    maxLength: value.maxLength,
    includeNumbers: value.includeNumbers,
    includeSpecialCharacters: value.includeSpecialCharacters,
    lettersInMixedCase: value.lettersInMixedCase,
    maxConsecutiveLetters: value.maxConsecutiveLetters,
    excludePasswordsInDictionary: value.excludePasswordsInDictionary,
    excludeUserProfileInfoInPassword: value.excludeUserProfileInfoInPassword,
    expiryDuration: value.expiryDuration,
    passwordHistoryEnabled: value.passwordHistoryEnabled,
    oldPasswords: value.oldPasswords,
    defaultPolicy: value.defaultPolicy,
  };
}
