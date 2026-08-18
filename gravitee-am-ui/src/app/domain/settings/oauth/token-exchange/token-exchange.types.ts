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

/** Scope handling mode for token exchange. */
export type TokenExchangeScopeHandling = 'downscoping' | 'permissive';

/** Which validated token a claim mapping reads from. */
export const CLAIM_SOURCE_SUBJECT_TOKEN = 'SUBJECT_TOKEN';
export const CLAIM_SOURCE_ACTOR_TOKEN = 'ACTOR_TOKEN';
export type TokenExchangeClaimSource = typeof CLAIM_SOURCE_SUBJECT_TOKEN | typeof CLAIM_SOURCE_ACTOR_TOKEN;

/** Copies one claim from a validated token onto the issued token. */
export interface TokenExchangeClaimMapping {
  source: TokenExchangeClaimSource;
  /** Claim name on the source token. */
  sourceClaim: string;
  /** Claim name on the issued token. */
  tokenClaim: string;
}

export const TOKEN_EXCHANGE_CLAIM_SOURCE_OPTIONS: readonly { label: string; value: TokenExchangeClaimSource }[] = [
  { label: 'Subject token', value: CLAIM_SOURCE_SUBJECT_TOKEN },
  { label: 'Actor token', value: CLAIM_SOURCE_ACTOR_TOKEN },
];

/** Per-application or domain-default token exchange OAuth settings. */
export interface TokenExchangeOAuthSettings {
  /** When true, effective settings are resolved from the domain default. */
  inherited: boolean;
  scopeHandling: TokenExchangeScopeHandling;
  /** Claims copied from the subject or actor token onto the issued token. */
  claimsMapper?: TokenExchangeClaimMapping[];
}

export const DEFAULT_TOKEN_EXCHANGE_SCOPE_HANDLING: TokenExchangeScopeHandling = 'downscoping';

export const TOKEN_EXCHANGE_SCOPE_HANDLING_OPTIONS: readonly { label: string; value: TokenExchangeScopeHandling }[] = [
  { label: 'Downscoping (default)', value: 'downscoping' },
  { label: 'Permissive', value: 'permissive' },
];

/** Key resolution method for a trusted issuer (lowercase matches enum serialization). */
export const KEY_RESOLUTION_JWKS_URL = 'jwks_url';
export const KEY_RESOLUTION_PEM = 'pem';
export type KeyResolutionMethod = typeof KEY_RESOLUTION_JWKS_URL | typeof KEY_RESOLUTION_PEM;

/** One criterion for resolving an external JWT subject to a domain user (attribute + EL expression). */
export interface UserBindingCriterion {
  attribute: string;
  expression: string;
}

export interface TrustedIssuer {
  issuer: string;
  keyResolutionMethod: KeyResolutionMethod;
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
}
