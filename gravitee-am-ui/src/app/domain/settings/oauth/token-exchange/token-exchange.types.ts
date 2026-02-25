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

/** Key resolution method for a trusted issuer (lowercase matches enum serialization). */
export const KEY_RESOLUTION_JWKS_URL = 'jwks_url';
export const KEY_RESOLUTION_PEM = 'pem';

/** One criterion for resolving an external JWT subject to a domain user (attribute + EL expression). */
export interface UserBindingCriterion {
  attribute: string;
  expression: string;
}

export interface TrustedIssuer {
  issuer: string;
  keyResolutionMethod: string;
  jwksUri?: string;
  certificate?: string;
  scopeMappings?: Record<string, string>;
  /** UI-only: key-value rows for scope mappings form. */
  _scopeMappingRows?: { key: string; value: string }[];
  /** When true, resolve external JWT subject to a domain user using criteria below. */
  userBindingEnabled?: boolean;
  /** Criteria (attribute + EL expression) for user lookup; ANDed. Sent to API. */
  userBindingCriteria?: UserBindingCriterion[];
  /** UI-only: rows for user binding criteria form. */
  _userBindingRows?: UserBindingCriterion[];
  /** UI-only: whether the card is collapsed. */
  _collapsed?: boolean;
}
