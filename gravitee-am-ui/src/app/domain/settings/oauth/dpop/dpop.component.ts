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

import { SnackbarService } from '../../../../services/snackbar.service';
import { DomainService } from '../../../../services/domain.service';
import { AuthService } from '../../../../services/auth.service';
import { DomainStoreService } from '../../../../stores/domain.store';

const DPOP_SUPPORTED_ALGORITHMS = ['ES256', 'ES384', 'ES512', 'RS256', 'RS384', 'RS512'];

@Component({
  selector: 'app-dpop',
  templateUrl: './dpop.component.html',
  styleUrls: ['./dpop.component.scss'],
  standalone: false,
})
export class DpopSettingsComponent implements OnInit {
  readonly supportedAlgorithms = DPOP_SUPPORTED_ALGORITHMS;
  domain: any = {};
  domainId: string;
  requireDpopForAll = false;
  dpopSigningAlgorithms: string[] = [];
  formChanged = false;
  editMode: boolean;

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.subscribe((domain) => {
      this.domain = deepClone(domain);
      this.domainId = this.domain.id;
      this.requireDpopForAll = this.domain.oidc?.dpopSettings?.requireDpopForAll ?? false;
      this.dpopSigningAlgorithms = this.domain.oidc?.dpopSettings?.dpopSigningAlgorithms ?? [];
    });
    this.editMode = this.authService.hasPermissions(['domain_openid_update']);
  }

  save() {
    // Empty selection means "no restriction": send null so the backend clears the
    // allowlist (an empty array is rejected server-side).
    const dpopSettings = {
      requireDpopForAll: this.requireDpopForAll,
      dpopSigningAlgorithms: this.dpopSigningAlgorithms.length ? this.dpopSigningAlgorithms : null,
    };
    this.domainService.patchDpopSettings(this.domainId, dpopSettings).subscribe({
      next: (data) => {
        this.domainStore.set(data);
        this.domain = data;
        this.requireDpopForAll = data.oidc?.dpopSettings?.requireDpopForAll ?? false;
        this.dpopSigningAlgorithms = data.oidc?.dpopSettings?.dpopSigningAlgorithms ?? [];
        this.formChanged = false;
        this.snackbarService.open('DPoP configuration updated');
      },
      error: (error: unknown) => {
        const httpError = error as { error?: { message?: string } };
        if (httpError?.error?.message) {
          this.snackbarService.open(httpError.error.message);
        } else {
          this.snackbarService.open('Failed to update DPoP configuration');
        }
      },
    });
  }

  enableRequireDpopForAll(event) {
    this.requireDpopForAll = event.checked;
    this.formChanged = true;
  }

  onFormChange() {
    this.formChanged = true;
  }
}
