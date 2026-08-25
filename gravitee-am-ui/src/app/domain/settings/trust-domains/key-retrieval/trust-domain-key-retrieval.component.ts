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

const DEFAULT_KEY_RETRIEVAL_SETTINGS = {
  allowUnsecuredHttpUri: false,
  allowPrivateIpAddress: false,
  fetchTimeoutMs: 5000,
  maxResponseSizeKb: 32,
  cacheTtlSeconds: 300,
  cacheMaxEntries: 50,
};

@Component({
  selector: 'app-trust-domain-key-retrieval',
  templateUrl: './trust-domain-key-retrieval.component.html',
  standalone: false,
})
export class TrustDomainKeyRetrievalComponent implements OnInit {
  domainId: string;
  domain: any = {};
  formChanged = false;
  editMode: boolean;

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.subscribe((domain) => (this.domain = deepClone(domain)));
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_settings_update']);
    if (!this.domain.keyRetrievalSettings) {
      this.domain.keyRetrievalSettings = { ...DEFAULT_KEY_RETRIEVAL_SETTINGS };
    }
  }

  save() {
    this.domainService.patch(this.domainId, { keyRetrievalSettings: this.domain.keyRetrievalSettings }).subscribe({
      next: (data) => {
        this.domainStore.set(data);
        this.domain = data;
        this.formChanged = false;
        this.snackbarService.open('Key retrieval settings updated');
      },
      error: (err: unknown) => {
        const message = (err as any)?.error?.message || 'Failed to update key retrieval settings';
        this.snackbarService.open(message);
      },
    });
  }

  toggleAllowUnsecuredHttpUri(event) {
    this.domain.keyRetrievalSettings.allowUnsecuredHttpUri = event.checked;
    this.formChanged = true;
  }

  toggleAllowPrivateIpAddress(event) {
    this.domain.keyRetrievalSettings.allowPrivateIpAddress = event.checked;
    this.formChanged = true;
  }

  modelChanged(): void {
    this.formChanged = true;
  }
}
