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
import { ScopeService } from '../../../../services/scope.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { DomainStoreService } from '../../../../stores/domain.store';

import { KEY_RESOLUTION_JWKS_URL, KEY_RESOLUTION_PEM, TrustedIssuer } from './token-exchange.types';

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
  domainScopes: any[] = [];

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
    private scopeService: ScopeService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.subscribe((domain) => (this.domain = deepClone(domain)));
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_openid_update']);
    this.initializeSettings();
    this.scopeService.findAllByDomain(this.domainId).subscribe((scopes) => (this.domainScopes = scopes || []));
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
      trustedIssuers: this.normalizeTrustedIssuers(tokenExchangeSettings.trustedIssuers),
    };

    normalizedSettings.maxDelegationDepth =
      tokenExchangeSettings.maxDelegationDepth != null ? tokenExchangeSettings.maxDelegationDepth : this.defaultDelegationDepth;

    this.domain.tokenExchangeSettings = normalizedSettings;
  }

  private normalizeTrustedIssuers(trustedIssuers: TrustedIssuer[] | undefined): TrustedIssuer[] {
    if (!trustedIssuers?.length) {
      return [];
    }
    return trustedIssuers.map((ti) => {
      const criteria = ti.userBindingCriteria ?? [];
      return {
        issuer: ti.issuer ?? '',
        keyResolutionMethod: ti.keyResolutionMethod ?? KEY_RESOLUTION_JWKS_URL,
        jwksUri: ti.jwksUri,
        certificate: ti.certificate,
        scopeMappings: ti.scopeMappings ?? {},
        _scopeMappingRows: Object.entries(ti.scopeMappings ?? {}).map(([key, value]) => ({ key, value })),
        userBindingEnabled: ti.userBindingEnabled ?? false,
        userBindingCriteria: criteria,
        _userBindingRows: criteria.length ? criteria.map((c) => ({ attribute: c.attribute ?? '', expression: c.expression ?? '' })) : [],
        _collapsed: true,
      };
    });
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
      trustedIssuers: [],
    };
  }

  onTrustedIssuersChange(): void {
    this.modelChanged();
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
    if (
      settings.allowDelegation &&
      (settings.maxDelegationDepth == null ||
        settings.maxDelegationDepth < this.minDelegationDepth ||
        settings.maxDelegationDepth > this.maxDelegationDepthLimit)
    ) {
      errors.push(`Maximum Delegation Depth must be between ${this.minDelegationDepth} and ${this.maxDelegationDepthLimit}.`);
    }
    const trustedIssuers = settings.trustedIssuers ?? [];
    const seenIssuers = new Set<string>();
    for (let i = 0; i < trustedIssuers.length; i++) {
      const ti = trustedIssuers[i];
      const iss = (ti.issuer ?? '').trim();
      if (!iss) {
        errors.push(`Trusted issuer #${i + 1}: Issuer URL is required.`);
      } else if (seenIssuers.has(iss)) {
        errors.push(`Trusted issuer #${i + 1}: Duplicate issuer "${iss}".`);
      } else {
        seenIssuers.add(iss);
      }
      if (ti.keyResolutionMethod === KEY_RESOLUTION_JWKS_URL) {
        if (!(ti.jwksUri ?? '').trim()) {
          errors.push(`Trusted issuer #${i + 1}: JWKS URL is required when key method is JWKS URL.`);
        }
      } else if (ti.keyResolutionMethod === KEY_RESOLUTION_PEM) {
        if (!(ti.certificate ?? '').trim()) {
          errors.push(`Trusted issuer #${i + 1}: PEM certificate is required when key method is PEM.`);
        }
      }
      if (ti.userBindingEnabled) {
        const rows = ti._userBindingRows ?? [];
        const validCriteria = rows.filter((r) => (r.attribute ?? '').trim() && (r.expression ?? '').trim());
        if (validCriteria.length === 0) {
          errors.push(
            `Trusted issuer #${i + 1}: At least one user binding criterion (attribute and expression) is required when user binding is enabled.`,
          );
        }
      }
    }
    return errors;
  }

  isFormValid(): boolean {
    return this.getValidationErrors().length === 0;
  }

  save() {
    const payload = this.buildTokenExchangePayload();
    this.domainService.patchTokenExchangeSettings(this.domainId, payload).subscribe({
      next: (data) => {
        this.domainStore.set(data);
        this.domain = deepClone(data);
        this.initializeSettings();
        this.formChanged = false;
        this.snackbarService.open('Token Exchange settings updated');
      },
      error: () => {
        this.formChanged = true;
      },
    });
  }

  private buildTokenExchangePayload(): any {
    const settings = this.domain.tokenExchangeSettings;
    const trustedIssuers = (settings.trustedIssuers ?? []).map((ti: TrustedIssuer) => {
      const scopeMappings: Record<string, string> = {};
      (ti._scopeMappingRows ?? []).forEach((row) => {
        const k = (row.key ?? '').trim();
        if (k) {
          scopeMappings[k] = (row.value ?? '').trim();
        }
      });
      const userBindingCriteria = (ti._userBindingRows ?? [])
        .map((row) => ({
          attribute: (row.attribute ?? '').trim(),
          expression: (row.expression ?? '').trim(),
        }))
        .filter((c) => c.attribute && c.expression);
      return {
        issuer: (ti.issuer ?? '').trim(),
        keyResolutionMethod: ti.keyResolutionMethod ?? KEY_RESOLUTION_JWKS_URL,
        jwksUri: ti.keyResolutionMethod === KEY_RESOLUTION_JWKS_URL ? (ti.jwksUri ?? '').trim() : undefined,
        certificate: ti.keyResolutionMethod === KEY_RESOLUTION_PEM ? (ti.certificate ?? '').trim() : undefined,
        scopeMappings: Object.keys(scopeMappings).length ? scopeMappings : undefined,
        userBindingEnabled: ti.userBindingEnabled ?? false,
        userBindingCriteria: ti.userBindingEnabled && userBindingCriteria.length > 0 ? userBindingCriteria : undefined,
      };
    });
    return {
      ...this.domain,
      tokenExchangeSettings: {
        ...settings,
        trustedIssuers,
      },
    };
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
