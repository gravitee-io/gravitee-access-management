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

interface TrustedIssuer {
  issuer: string;
  keyResolutionMethod: 'JWKS_URL' | 'PEM';
  jwksUri?: string;
  certificate?: string;
  scopeMappings?: { [key: string]: string };
  userBindingEnabled?: boolean;
  userBindingMappings?: { [key: string]: string };
}

interface UserBindingMappingEntry {
  userAttribute: string;
  claimExpression: string;
}

interface ScopeMappingEntry {
  externalScope: string;
  domainScope: string;
}

interface TokenExchangeSettings {
  enabled: boolean;
  allowedSubjectTokenTypes: string[];
  allowedRequestedTokenTypes: string[];
  allowImpersonation: boolean;
  allowedActorTokenTypes: string[];
  allowDelegation: boolean;
  maxDelegationDepth?: number;
  trustedIssuers?: TrustedIssuer[];
}

@Component({
  selector: 'app-token-exchange',
  templateUrl: './token-exchange.component.html',
  styleUrls: ['./token-exchange.component.scss'],
  standalone: false,
})
export class TokenExchangeComponent implements OnInit {
  readonly maxDelegationDepthLimit = 100;
  readonly minDelegationDepth = 1;
  readonly defaultDelegationDepth = 25;

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

  readonly ACTOR_TOKEN_TYPES = [
    { value: 'urn:ietf:params:oauth:token-type:access_token', label: 'Access Token' },
    { value: 'urn:ietf:params:oauth:token-type:id_token', label: 'ID Token' },
    { value: 'urn:ietf:params:oauth:token-type:jwt', label: 'JWT' },
  ];

  readonly REQUESTED_TOKEN_TYPES = [
    { value: 'urn:ietf:params:oauth:token-type:access_token', label: 'Access Token' },
    { value: 'urn:ietf:params:oauth:token-type:id_token', label: 'ID Token' },
  ];

  readonly KEY_RESOLUTION_METHODS = [
    { value: 'JWKS_URL', label: 'JWKS URL' },
    { value: 'PEM', label: 'PEM Certificate' },
  ];

  // Legacy alias for backward compatibility in template
  readonly TOKEN_TYPES = this.SUBJECT_TOKEN_TYPES;

  readonly USER_BINDING_ATTRIBUTES = [
    { value: 'email', label: 'Email' },
    { value: 'username', label: 'Username' },
  ];

  // Scope mapping entries per issuer index (for UI two-way binding)
  scopeMappingEntries: ScopeMappingEntry[][] = [];

  // User binding mapping entries per issuer index (for UI two-way binding)
  userBindingMappingEntries: UserBindingMappingEntry[][] = [];

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

    normalizedSettings.maxDelegationDepth =
      tokenExchangeSettings.maxDelegationDepth != null ? tokenExchangeSettings.maxDelegationDepth : this.defaultDelegationDepth;

    normalizedSettings.trustedIssuers = tokenExchangeSettings.trustedIssuers ?? [];

    this.domain.tokenExchangeSettings = normalizedSettings;
    this.initScopeMappingEntries();
  }

  private getDefaultSettings(): TokenExchangeSettings {
    return {
      enabled: false,
      allowedSubjectTokenTypes: this.SUBJECT_TOKEN_TYPES.map((t) => t.value),
      allowedRequestedTokenTypes: this.REQUESTED_TOKEN_TYPES.map((t) => t.value),
      allowImpersonation: true,
      allowedActorTokenTypes: this.ACTOR_TOKEN_TYPES.map((t) => t.value),
      allowDelegation: false,
      maxDelegationDepth: this.defaultDelegationDepth,
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
    if (settings.trustedIssuers) {
      for (let i = 0; i < settings.trustedIssuers.length; i++) {
        const ti = settings.trustedIssuers[i];
        if (!ti.issuer?.trim()) {
          errors.push(`Trusted Issuer #${i + 1}: Issuer URL is required.`);
        }
        if (ti.keyResolutionMethod === 'JWKS_URL' && !ti.jwksUri?.trim()) {
          errors.push(`Trusted Issuer #${i + 1}: JWKS URL is required.`);
        }
        if (ti.keyResolutionMethod === 'PEM' && !ti.certificate?.trim()) {
          errors.push(`Trusted Issuer #${i + 1}: PEM Certificate is required.`);
        }
        if (ti.userBindingEnabled) {
          const mappings = ti.userBindingMappings;
          if (!mappings || Object.keys(mappings).length === 0) {
            errors.push(`Trusted Issuer #${i + 1}: At least one user binding mapping is required when user binding is enabled.`);
          }
        }
      }
      const issuers = settings.trustedIssuers.map((ti) => ti.issuer).filter((iss) => iss?.trim());
      if (new Set(issuers).size !== issuers.length) {
        errors.push('Duplicate issuer URLs are not allowed.');
      }
    }
    if (
      settings.allowDelegation &&
      (settings.maxDelegationDepth == null ||
        settings.maxDelegationDepth < this.minDelegationDepth ||
        settings.maxDelegationDepth > this.maxDelegationDepthLimit)
    ) {
      errors.push(`Maximum Delegation Depth must be between ${this.minDelegationDepth} and ${this.maxDelegationDepthLimit}.`);
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

  addTrustedIssuer(): void {
    if (!this.domain.tokenExchangeSettings.trustedIssuers) {
      this.domain.tokenExchangeSettings.trustedIssuers = [];
    }
    this.domain.tokenExchangeSettings.trustedIssuers.push({
      issuer: '',
      keyResolutionMethod: 'JWKS_URL',
      jwksUri: '',
      certificate: '',
      scopeMappings: {},
      userBindingEnabled: false,
      userBindingMappings: {},
    });
    this.scopeMappingEntries.push([]);
    this.userBindingMappingEntries.push([]);
    this.formChanged = true;
  }

  removeTrustedIssuer(index: number): void {
    this.domain.tokenExchangeSettings.trustedIssuers.splice(index, 1);
    this.scopeMappingEntries.splice(index, 1);
    this.userBindingMappingEntries.splice(index, 1);
    this.formChanged = true;
  }

  addScopeMapping(issuerIndex: number): void {
    this.scopeMappingEntries[issuerIndex].push({ externalScope: '', domainScope: '' });
    this.syncScopeMappingsToModel(issuerIndex);
    this.formChanged = true;
  }

  removeScopeMapping(issuerIndex: number, mappingIndex: number): void {
    this.scopeMappingEntries[issuerIndex].splice(mappingIndex, 1);
    this.syncScopeMappingsToModel(issuerIndex);
    this.formChanged = true;
  }

  onScopeMappingChange(issuerIndex: number): void {
    this.syncScopeMappingsToModel(issuerIndex);
    this.formChanged = true;
  }

  private syncScopeMappingsToModel(issuerIndex: number): void {
    const entries = this.scopeMappingEntries[issuerIndex];
    const mappings: { [key: string]: string } = {};
    for (const entry of entries) {
      if (entry.externalScope && entry.domainScope) {
        mappings[entry.externalScope] = entry.domainScope;
      }
    }
    this.domain.tokenExchangeSettings.trustedIssuers[issuerIndex].scopeMappings = mappings;
  }

  addUserBindingMapping(issuerIndex: number): void {
    this.userBindingMappingEntries[issuerIndex].push({ userAttribute: '', claimExpression: '' });
    this.syncUserBindingMappingsToModel(issuerIndex);
    this.formChanged = true;
  }

  removeUserBindingMapping(issuerIndex: number, mappingIndex: number): void {
    this.userBindingMappingEntries[issuerIndex].splice(mappingIndex, 1);
    this.syncUserBindingMappingsToModel(issuerIndex);
    this.formChanged = true;
  }

  onUserBindingMappingChange(issuerIndex: number): void {
    this.syncUserBindingMappingsToModel(issuerIndex);
    this.formChanged = true;
  }

  private syncUserBindingMappingsToModel(issuerIndex: number): void {
    const entries = this.userBindingMappingEntries[issuerIndex];
    const mappings: { [key: string]: string } = {};
    for (const entry of entries) {
      if (entry.userAttribute && entry.claimExpression) {
        mappings[entry.userAttribute] = entry.claimExpression;
      }
    }
    this.domain.tokenExchangeSettings.trustedIssuers[issuerIndex].userBindingMappings = mappings;
  }

  private initScopeMappingEntries(): void {
    this.scopeMappingEntries = (this.domain.tokenExchangeSettings.trustedIssuers || []).map((issuer: TrustedIssuer) => {
      if (!issuer.scopeMappings) {
        return [];
      }
      return Object.entries(issuer.scopeMappings).map(([externalScope, domainScope]) => ({
        externalScope,
        domainScope,
      }));
    });
    this.initUserBindingMappingEntries();
  }

  private initUserBindingMappingEntries(): void {
    this.userBindingMappingEntries = (this.domain.tokenExchangeSettings.trustedIssuers || []).map((issuer: TrustedIssuer) => {
      if (!issuer.userBindingMappings) {
        return [];
      }
      return Object.entries(issuer.userBindingMappings).map(([userAttribute, claimExpression]) => ({
        userAttribute,
        claimExpression,
      }));
    });
  }
}
