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

/** Which kind of external trust a trusted domain represents. */
export type TrustDomainKind = 'SPIFFE' | 'TOKEN_EXCHANGE';

export const SPIFFE: TrustDomainKind = 'SPIFFE';
export const TOKEN_EXCHANGE: TrustDomainKind = 'TOKEN_EXCHANGE';

export const TRUST_DOMAIN_KIND_OPTIONS: readonly { value: TrustDomainKind; label: string; hint: string }[] = [
  {
    value: TOKEN_EXCHANGE,
    label: 'Token Exchange',
    hint: 'Validates external subject and actor tokens during an RFC 8693 token exchange.',
  },
  {
    value: SPIFFE,
    label: 'SPIFFE',
    hint: 'Validates JWT-SVIDs presented by workloads as client assertions.',
  },
];

/** Where a trusted domain's signing keys come from. */
export type KeyMaterialSource = 'JWKS_URL' | 'JWK_SET' | 'PEM';

export const KEY_MATERIAL_SOURCE_OPTIONS: readonly { value: KeyMaterialSource; label: string }[] = [
  { value: 'JWKS_URL', label: 'JWKS URL' },
  { value: 'JWK_SET', label: 'JWK Set' },
  { value: 'PEM', label: 'PEM Certificate' },
];

export interface JwkSet {
  keys: unknown[];
}

export interface TrustDomainKeyMaterial {
  source: KeyMaterialSource;
  jwksUrl?: string;
  jwkSet?: JwkSet;
  certificate?: string;
}

/** One criterion for resolving an external JWT subject to a domain user (attribute + EL expression). */
export interface UserBindingCriterion {
  attribute: string;
  expression: string;
}

export interface TrustDomainTokenExchangeSettings {
  issuer: string;
  scopeMappings?: Record<string, string>;
  userBindingEnabled?: boolean;
  userBindingCriteria?: UserBindingCriterion[];
}

export interface TrustDomain {
  id?: string;
  kind: TrustDomainKind;
  name: string;
  description?: string;
  keyMaterial?: TrustDomainKeyMaterial;
  refreshIntervalSeconds?: number;
  allowedAlgorithms?: string[];
  tokenExchange?: TrustDomainTokenExchangeSettings;
}

export const DEFAULT_REFRESH_INTERVAL_SECONDS = 300;

const ACRONYMS = new Set(['JWKS', 'JWK', 'PEM', 'URL', 'URI', 'ID', 'SPIFFE', 'SVID', 'X509']);

/** Turns a raw enum value such as JWKS_URL into a readable "JWKS URL". */
export function humanizeEnumValue(value: string): string {
  return (value ?? '')
    .split(/[_\s-]+/)
    .filter((word) => word.length > 0)
    .map((word) => (ACRONYMS.has(word.toUpperCase()) ? word.toUpperCase() : word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()))
    .join(' ');
}

function optionLabel(options: readonly { value: string; label: string }[], value: string): string {
  const known = options.find((o) => o.value.toLowerCase() === (value ?? '').toLowerCase());
  return known?.label ?? humanizeEnumValue(value);
}

export function trustDomainKindLabel(kind: TrustDomainKind | string): string {
  return optionLabel(TRUST_DOMAIN_KIND_OPTIONS, kind);
}

export function keyMaterialSourceLabel(source: KeyMaterialSource | string): string {
  return optionLabel(KEY_MATERIAL_SOURCE_OPTIONS, source);
}

/**
 * Restores the canonical enum spelling. The management API serializes every enum lowercased
 * (ObjectMapperResolver) and accepts either case back, so responses carry "token_exchange" and
 * "pem" where the console compares against TOKEN_EXCHANGE and PEM.
 */
export function normalizeTrustDomain<T>(trustDomain: T): T {
  const td = trustDomain as TrustDomain;
  if (!td) {
    return trustDomain;
  }
  return {
    ...td,
    kind: td.kind?.toUpperCase() as TrustDomainKind,
    keyMaterial: td.keyMaterial ? { ...td.keyMaterial, source: td.keyMaterial.source?.toUpperCase() as KeyMaterialSource } : td.keyMaterial,
  } as T;
}

const OUTSIDE_LABEL = /[^a-z0-9.-]+/g;
const LABEL_EDGES = /^[.-]+|[.-]+$/g;

export function deriveNameFromIssuer(issuer: string): string {
  return (issuer ?? '').toLowerCase().replace(OUTSIDE_LABEL, '-').replace(LABEL_EDGES, '').slice(0, 255).replace(LABEL_EDGES, '');
}

const NAME_PATTERN = /^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$/;

export function isValidTrustDomainName(name: string): boolean {
  return NAME_PATTERN.test(name ?? '');
}

export function keyMaterialErrors(keyMaterial: TrustDomainKeyMaterial | undefined): string[] {
  if (!keyMaterial?.source) {
    return ['A key source is required.'];
  }
  switch (keyMaterial.source) {
    case 'JWKS_URL':
      return (keyMaterial.jwksUrl ?? '').trim() ? [] : ['JWKS URL is required when the key source is JWKS URL.'];
    case 'JWK_SET':
      return keyMaterial.jwkSet?.keys?.length ? [] : ['The JWK set must contain at least one key.'];
    case 'PEM':
      return (keyMaterial.certificate ?? '').trim() ? [] : ['PEM certificate is required when the key source is PEM Certificate.'];
    default:
      return ['A key source is required.'];
  }
}
