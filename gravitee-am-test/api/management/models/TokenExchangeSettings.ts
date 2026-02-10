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
 *
 * @export
 * @interface TokenExchangeSettings
 */
export interface TokenExchangeSettings {
  /**
   *
   * @type {boolean}
   * @memberof TokenExchangeSettings
   */
  enabled?: boolean;
  /**
   *
   * @type {boolean}
   * @memberof TokenExchangeSettings
   */
  allowImpersonation?: boolean;
  /**
   *
   * @type {boolean}
   * @memberof TokenExchangeSettings
   */
  allowDelegation?: boolean;
  /**
   *
   * @type {Array<string>}
   * @memberof TokenExchangeSettings
   */
  allowedSubjectTokenTypes?: Array<string>;
  /**
   *
   * @type {Array<string>}
   * @memberof TokenExchangeSettings
   */
  allowedRequestedTokenTypes?: Array<string>;
  /**
   *
   * @type {Array<string>}
   * @memberof TokenExchangeSettings
   */
  allowedActorTokenTypes?: Array<string>;
  /**
   *
   * @type {number}
   * @memberof TokenExchangeSettings
   */
  maxDelegationDepth?: number;
}

/**
 * Check if a given object implements the TokenExchangeSettings interface.
 */
export function instanceOfTokenExchangeSettings(value: object): value is TokenExchangeSettings {
  return true;
}

export function TokenExchangeSettingsFromJSON(json: any): TokenExchangeSettings {
  return TokenExchangeSettingsFromJSONTyped(json, false);
}

export function TokenExchangeSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): TokenExchangeSettings {
  if (json == null) {
    return json;
  }
  return {
    enabled: json['enabled'] == null ? undefined : json['enabled'],
    allowImpersonation: json['allowImpersonation'] == null ? undefined : json['allowImpersonation'],
    allowDelegation: json['allowDelegation'] == null ? undefined : json['allowDelegation'],
    allowedSubjectTokenTypes: json['allowedSubjectTokenTypes'] == null ? undefined : json['allowedSubjectTokenTypes'],
    allowedRequestedTokenTypes: json['allowedRequestedTokenTypes'] == null ? undefined : json['allowedRequestedTokenTypes'],
    allowedActorTokenTypes: json['allowedActorTokenTypes'] == null ? undefined : json['allowedActorTokenTypes'],
    maxDelegationDepth: json['maxDelegationDepth'] == null ? undefined : json['maxDelegationDepth'],
  };
}

export function TokenExchangeSettingsToJSON(json: any): TokenExchangeSettings {
  return TokenExchangeSettingsToJSONTyped(json, false);
}

export function TokenExchangeSettingsToJSONTyped(value?: TokenExchangeSettings | null, ignoreDiscriminator: boolean = false): any {
  if (value == null) {
    return value;
  }

  return {
    enabled: value['enabled'],
    allowImpersonation: value['allowImpersonation'],
    allowDelegation: value['allowDelegation'],
    allowedSubjectTokenTypes: value['allowedSubjectTokenTypes'],
    allowedRequestedTokenTypes: value['allowedRequestedTokenTypes'],
    allowedActorTokenTypes: value['allowedActorTokenTypes'],
    maxDelegationDepth: value['maxDelegationDepth'],
  };
}
