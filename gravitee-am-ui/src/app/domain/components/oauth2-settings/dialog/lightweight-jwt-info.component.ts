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

interface KeptClaims {
  token: string;
  always: string[];
  whenSet: string[];
  internal: string[];
}

@Component({
  selector: 'lightweight-jwt-info-dialog',
  templateUrl: './lightweight-jwt-info.component.html',
  styleUrls: ['./lightweight-jwt-info.component.scss'],
  standalone: false,
})
export class LightweightJwtInfoDialogComponent {
  readonly keptClaims: KeptClaims[] = [
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

  constructor(public dialogRef: MatDialogRef<LightweightJwtInfoDialogComponent>) {}
}
