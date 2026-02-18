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
  allowedActorTokenTypes: string[];
  allowDelegation: boolean;
  maxDelegationDepth?: number;
}

@Component({
  selector: 'app-token-exchange',
  templateUrl: './token-exchange.component.html',
  styleUrls: ['./token-exchange.component.scss'],
  standalone: false,
})
export class TokenExchangeComponent implements OnInit {
  domainId: string;
  domain: any = {};
  formChanged = false;
  editMode: boolean;
  maxDelegationDepthLimit: number = 10;

  readonly SUBJECT_TOKEN_TYPES = [
    { value: 'urn:ietf:params:oauth:token-type:access_token', label: 'Access Token' },
    { value: 'urn:ietf:params:oauth:token-type:refresh_token', label: 'Refresh Token' },
    { value: 'urn:ietf:params:oauth:token-type:id_token', label: 'ID Token' },
    { value: 'urn:ietf:params:oauth:token-type:jwt', label: 'JWT' },
  ];

  readonly ACTOR_TOKEN_TYPES = [
    { value: 'urn:ietf:params:oauth:token-type:access_token', label: 'Access Token' },
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
    this.domainService.getTokenExchangeSettings().subscribe({
      next: (settings) => {
        this.maxDelegationDepthLimit = settings.maxDelegationDepth;
      },
      error: () => {
        // Keep default limit if platform config is unavailable
      },
    });
  }

  private initializeSettings() {
    if (!this.domain.tokenExchangeSettings) {
      this.domain.tokenExchangeSettings = this.getDefaultSettings();
      return;
    }

    const tokenExchangeSettings = this.domain.tokenExchangeSettings;
    const normalizedSettings: TokenExchangeSettings = {
      enabled: tokenExchangeSettings.enabled ?? false,
      allowedSubjectTokenTypes: tokenExchangeSettings.allowedSubjectTokenTypes ?? this.SUBJECT_TOKEN_TYPES.map((t) => t.value),
      allowedRequestedTokenTypes: tokenExchangeSettings.allowedRequestedTokenTypes ?? this.REQUESTED_TOKEN_TYPES.map((t) => t.value),
      allowImpersonation: tokenExchangeSettings.allowImpersonation ?? true,
      allowedActorTokenTypes: tokenExchangeSettings.allowedActorTokenTypes ?? this.ACTOR_TOKEN_TYPES.map((t) => t.value),
      allowDelegation: tokenExchangeSettings.allowDelegation ?? false,
    };

    if (tokenExchangeSettings.maxDelegationDepth !== undefined && tokenExchangeSettings.maxDelegationDepth !== null) {
      normalizedSettings.maxDelegationDepth = tokenExchangeSettings.maxDelegationDepth;
    }

    this.domain.tokenExchangeSettings = normalizedSettings;
  }

  private getDefaultSettings(): TokenExchangeSettings {
    return {
      enabled: false,
      allowedSubjectTokenTypes: this.SUBJECT_TOKEN_TYPES.map((t) => t.value),
      allowedRequestedTokenTypes: this.REQUESTED_TOKEN_TYPES.map((t) => t.value),
      allowImpersonation: true,
      allowedActorTokenTypes: this.ACTOR_TOKEN_TYPES.map((t) => t.value),
      allowDelegation: false,
    };
  }

  getValidationErrors(): string[] {
    const settings = this.domain.tokenExchangeSettings;
    if (!settings?.enabled) {
      return [];
    }
    const errors: string[] = [];
    if (!settings.allowImpersonation && !settings.allowDelegation) {
      errors.push('At least one of Impersonation or Delegation must be enabled.');
    }
    if (!settings.allowedSubjectTokenTypes?.length) {
      errors.push('At least one Subject Token Type must be selected.');
    }
    if (!settings.allowedRequestedTokenTypes?.length) {
      errors.push('At least one Requested Token Type must be selected.');
    }
    if (settings.allowDelegation && !settings.allowedActorTokenTypes?.length) {
      errors.push('At least one Actor Token Type must be selected when Delegation is enabled.');
    }
    if (settings.allowDelegation && settings.maxDelegationDepth > 0 && settings.maxDelegationDepth > this.maxDelegationDepthLimit) {
      errors.push(`Maximum Delegation Depth must not exceed ${this.maxDelegationDepthLimit}.`);
    }
    return errors;
  }

  isFormValid(): boolean {
    return this.getValidationErrors().length === 0;
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
