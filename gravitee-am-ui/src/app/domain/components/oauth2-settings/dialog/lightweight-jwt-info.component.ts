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
import { Component } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';

export type LightweightJwtTokenType = 'Access token' | 'ID token' | 'Refresh token';

export interface KeptClaims {
  token: LightweightJwtTokenType;
  always: string[];
  whenSet: string[];
  internal: string[];
}

export const LIGHTWEIGHT_JWT_KEPT_CLAIMS: KeptClaims[] = [
  {
    token: 'Access token',
    always: ['iss', 'sub', 'aud', 'exp', 'iat', 'jti', 'scope', 'client_id'],
    whenSet: ['cnf', 'act', 'client_profile', 'permissions', 'authorization_details'],
    internal: ['gis', 'domain', 'claims_request_parameter'],
  },
  {
    token: 'ID token',
    always: ['iss', 'sub', 'aud', 'exp', 'iat', 'auth_time'],
    whenSet: ['nonce', 'acr', 'client_profile', 'at_hash', 'c_hash', 's_hash'],
    internal: ['gis'],
  },
  {
    token: 'Refresh token',
    always: ['iss', 'sub', 'aud', 'exp', 'iat', 'jti', 'scope'],
    whenSet: ['cnf', 'act', 'client_profile', 'permissions', 'authorization_details', 'orig_resources'],
    internal: ['gis', 'domain'],
  },
];

const LIGHTWEIGHT_JWT_CLAIM_DESCRIPTIONS: Record<string, string> = {
  iss: 'Issuer',
  sub: 'Subject',
  aud: 'Audience',
  exp: 'Expiration time',
  iat: 'Issued at',
  jti: 'JWT ID',
  scope: 'Scope',
  client_id: 'Client ID',
  auth_time: 'Authentication time',
  cnf: 'Confirmation',
  act: 'Actor',
  client_profile: 'Client profile',
  permissions: 'UMA permissions',
  authorization_details: 'Authorization details',
  orig_resources: 'Original resources',
  nonce: 'Nonce',
  acr: 'Authentication context class reference',
  at_hash: 'Access token hash',
  c_hash: 'Authorization code hash',
  s_hash: 'State hash',
  gis: 'Gravitee internal subject',
  domain: 'Security domain',
  claims_request_parameter: 'Claims request parameter',
};

export interface ReservedClaim {
  name: string;
  internal: boolean;
  tooltip: string;
}

export function reservedClaimsForTokens(tokens: LightweightJwtTokenType[]): ReservedClaim[] {
  const rows = LIGHTWEIGHT_JWT_KEPT_CLAIMS.filter((row) => tokens.includes(row.token));
  const reserved = new Map<string, ReservedClaim>();
  for (const kind of ['always', 'whenSet', 'internal'] as const) {
    for (const row of rows) {
      for (const name of row[kind]) {
        if (!reserved.has(name)) {
          const internal = kind === 'internal';
          const tooltip = `Reserved claim: ${LIGHTWEIGHT_JWT_CLAIM_DESCRIPTIONS[name] ?? name}${internal ? ' (internal)' : ''}`;
          reserved.set(name, { name, internal, tooltip });
        }
      }
    }
  }
  return [...reserved.values()];
}

@Component({
  selector: 'lightweight-jwt-info-dialog',
  templateUrl: './lightweight-jwt-info.component.html',
  styleUrls: ['./lightweight-jwt-info.component.scss'],
  standalone: false,
})
export class LightweightJwtInfoDialogComponent {
  readonly keptClaims = LIGHTWEIGHT_JWT_KEPT_CLAIMS;
  readonly claimDescriptions = LIGHTWEIGHT_JWT_CLAIM_DESCRIPTIONS;

  constructor(public dialogRef: MatDialogRef<LightweightJwtInfoDialogComponent>) {}
}
