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
import { Component, OnInit } from '@angular/core';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { AuthService } from '../../../../services/auth.service';
import { DomainService } from '../../../../services/domain.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { DomainStoreService } from '../../../../stores/domain.store';

const DEFAULT_SETTINGS = {
  enabled: false,
  allowUnsecuredHttpUri: false,
  allowPrivateIpAddress: false,
  fetchTimeoutMs: 5000,
  maxResponseSizeKb: 32,
  cacheTtlSeconds: 300,
  cacheMaxEntries: 50,
  maxJwtLifetimeSeconds: 300,
  clockSkewSeconds: 30,
  defaultAllowedAlgorithms: ['RS256', 'RS384', 'RS512', 'ES256', 'ES384', 'ES512', 'EdDSA'],
};

const SUPPORTED_ALGORITHMS = ['RS256', 'RS384', 'RS512', 'ES256', 'ES384', 'ES512', 'EdDSA', 'PS256', 'PS384', 'PS512'];

@Component({
  selector: 'app-oidc-spiffe-settings',
  templateUrl: './spiffe-settings.component.html',
  styleUrls: ['./spiffe-settings.component.scss'],
  standalone: false,
})
export class SpiffeSettingsComponent implements OnInit {
  domainId: string;
  domain: any = {};
  formChanged = false;
  editMode: boolean;
  supportedAlgorithms = SUPPORTED_ALGORITHMS;

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.subscribe((domain) => (this.domain = deepClone(domain)));
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_openid_update']);
    if (!this.domain.oidc.spiffeSettings) {
      this.domain.oidc.spiffeSettings = { ...DEFAULT_SETTINGS };
    }
  }

  save() {
    this.domainService.patchOpenidDCRSettings(this.domainId, this.domain).subscribe({
      next: (data) => {
        this.domainStore.set(data);
        this.domain = data;
        this.formChanged = false;
        this.snackbarService.open('SPIFFE configuration updated');
      },
      error: (err: unknown) => {
        const message = (err as any)?.error?.message || 'Failed to update SPIFFE configuration';
        this.snackbarService.open(message);
      },
    });
  }

  enableSpiffe(event) {
    if (!this.domain.oidc.spiffeSettings) {
      this.domain.oidc.spiffeSettings = { ...DEFAULT_SETTINGS };
    }
    this.domain.oidc.spiffeSettings.enabled = event.checked;
    this.formChanged = true;
  }

  isSpiffeEnabled(): boolean {
    return this.domain.oidc?.spiffeSettings?.enabled === true;
  }

  toggleAllowUnsecuredHttpUri(event) {
    this.domain.oidc.spiffeSettings.allowUnsecuredHttpUri = event.checked;
    this.formChanged = true;
  }

  toggleAllowPrivateIpAddress(event) {
    this.domain.oidc.spiffeSettings.allowPrivateIpAddress = event.checked;
    this.formChanged = true;
  }

  modelChanged(): void {
    this.formChanged = true;
  }
}
