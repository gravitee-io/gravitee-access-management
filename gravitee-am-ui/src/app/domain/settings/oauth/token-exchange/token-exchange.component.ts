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

interface TokenExchangeSettings {
  enabled: boolean;
  allowedSubjectTokenTypes: string[];
  allowedRequestedTokenTypes: string[];
  allowImpersonation: boolean;
}

@Component({
  selector: 'app-token-exchange',
  templateUrl: './token-exchange.component.html',
  standalone: false,
})
export class TokenExchangeComponent implements OnInit {
  domainId: string;
  domain: any = {};
  formChanged = false;
  editMode: boolean;

  readonly SUBJECT_TOKEN_TYPES = [
    { value: 'urn:ietf:params:oauth:token-type:access_token', label: 'Access Token' },
    { value: 'urn:ietf:params:oauth:token-type:refresh_token', label: 'Refresh Token' },
    { value: 'urn:ietf:params:oauth:token-type:id_token', label: 'ID Token' },
    { value: 'urn:ietf:params:oauth:token-type:jwt', label: 'JWT' },
  ];

  readonly REQUESTED_TOKEN_TYPES = [
    { value: 'urn:ietf:params:oauth:token-type:access_token', label: 'Access Token' },
    { value: 'urn:ietf:params:oauth:token-type:id_token', label: 'ID Token' },
  ];

  // Legacy alias for backward compatibility in template
  readonly TOKEN_TYPES = this.SUBJECT_TOKEN_TYPES;

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
    this.initializeSettings();
  }

  private initializeSettings() {
    if (!this.domain.tokenExchangeSettings) {
      this.domain.tokenExchangeSettings = this.getDefaultSettings();
    } else {
      this.domain.tokenExchangeSettings = {
        enabled: this.domain.tokenExchangeSettings.enabled ?? false,
        allowedSubjectTokenTypes:
          this.domain.tokenExchangeSettings.allowedSubjectTokenTypes ?? this.SUBJECT_TOKEN_TYPES.map((t) => t.value),
        allowedRequestedTokenTypes:
          this.domain.tokenExchangeSettings.allowedRequestedTokenTypes ?? this.REQUESTED_TOKEN_TYPES.map((t) => t.value),
        allowImpersonation: this.domain.tokenExchangeSettings.allowImpersonation ?? true,
      };
    }
  }

  private getDefaultSettings(): TokenExchangeSettings {
    return {
      enabled: false,
      allowedSubjectTokenTypes: this.SUBJECT_TOKEN_TYPES.map((t) => t.value),
      allowedRequestedTokenTypes: this.REQUESTED_TOKEN_TYPES.map((t) => t.value),
      allowImpersonation: true,
    };
  }

  save() {
    this.domainService.patchTokenExchangeSettings(this.domainId, this.domain).subscribe((data) => {
      this.domainStore.set(data);
      this.domain = deepClone(data);
      this.initializeSettings();
      this.formChanged = false;
      this.snackbarService.open('Token Exchange settings updated');
    });
  }

  enableTokenExchange(event) {
    this.domain.tokenExchangeSettings.enabled = event.checked;
    this.formChanged = true;
  }

  isTokenExchangeEnabled(): boolean {
    return this.domain.tokenExchangeSettings?.enabled;
  }

  modelChanged(): void {
    this.formChanged = true;
  }
}
