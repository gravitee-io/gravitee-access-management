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
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';
import { Subject, takeUntil } from 'rxjs';
import { filter, switchMap, tap } from 'rxjs/operators';

import { AuthService } from '../../../../../services/auth.service';
import { DialogService } from '../../../../../services/dialog.service';
import { DomainService } from '../../../../../services/domain.service';
import { ScopeService } from '../../../../../services/scope.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { DomainStoreService } from '../../../../../stores/domain.store';
import { KEY_RESOLUTION_JWKS_URL, KEY_RESOLUTION_PEM, TrustedIssuer, UserBindingCriterion } from '../token-exchange.types';

interface ScopeStaging {
  key: string;
  value: string;
}

@Component({
  selector: 'app-trusted-issuer-detail',
  templateUrl: './trusted-issuer-detail.component.html',
  styleUrls: ['./trusted-issuer-detail.component.scss'],
  standalone: false,
})
export class TrustedIssuerDetailComponent implements OnInit, OnDestroy {
  readonly KEY_RESOLUTION_JWKS_URL = KEY_RESOLUTION_JWKS_URL;
  readonly KEY_RESOLUTION_PEM = KEY_RESOLUTION_PEM;

  readonly KEY_RESOLUTION_OPTIONS = [
    { value: KEY_RESOLUTION_JWKS_URL, label: 'JWKS URL' },
    { value: KEY_RESOLUTION_PEM, label: 'PEM Certificate' },
  ];

  createMode: boolean;
  issuerIndex: number;
  trustedIssuer: TrustedIssuer;
  domain: any = {};
  domainId: string;
  domainScopes: any[] = [];
  editMode: boolean;
  formChanged = false;

  newScopeStaging: ScopeStaging = { key: '', value: '' };
  newUserBindingStaging: UserBindingCriterion = { attribute: '', expression: '' };
  private destroy$ = new Subject<void>();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private domainService: DomainService,
    private scopeService: ScopeService,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.pipe(takeUntil(this.destroy$)).subscribe((domain) => (this.domain = deepClone(domain)));
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_openid_update']);
    this.scopeService.findAllByDomain(this.domainId).subscribe({
      next: (scopes) => (this.domainScopes = scopes || []),
      error: () => (this.domainScopes = []),
    });

    const param = this.route.snapshot.paramMap.get('issuerIndex');
    if (param === null || param === undefined) {
      // Route is 'new'
      this.createMode = true;
      this.trustedIssuer = {
        issuer: '',
        keyResolutionMethod: KEY_RESOLUTION_JWKS_URL,
        jwksUri: '',
        _scopeMappingRows: [],
        userBindingEnabled: false,
        _userBindingRows: [],
      };
    } else {
      this.createMode = false;
      this.issuerIndex = parseInt(param, 10);
      const issuers = this.domain.tokenExchangeSettings?.trustedIssuers ?? [];
      if (isNaN(this.issuerIndex) || this.issuerIndex < 0 || this.issuerIndex >= issuers.length) {
        this.router.navigate(['..'], { relativeTo: this.route });
        return;
      }
      this.trustedIssuer = this.normalizeTrustedIssuer(issuers[this.issuerIndex]);
    }
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private normalizeTrustedIssuer(ti: TrustedIssuer): TrustedIssuer {
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
    };
  }

  get pageTitle(): string {
    if (this.createMode) {
      return 'New Trusted Issuer';
    }
    return (this.trustedIssuer?.issuer ?? '').trim() || 'Trusted Issuer';
  }

  getValidationErrors(): string[] {
    const ti = this.trustedIssuer;
    if (!ti) return [];
    const errors: string[] = [];
    const iss = (ti.issuer ?? '').trim();
    if (!iss) {
      errors.push('Issuer URL is required.');
    } else {
      // Check for duplicates against other issuers
      const issuers = this.domain.tokenExchangeSettings?.trustedIssuers ?? [];
      for (let i = 0; i < issuers.length; i++) {
        if (!this.createMode && i === this.issuerIndex) continue;
        if ((issuers[i].issuer ?? '').trim() === iss) {
          errors.push(`Duplicate issuer "${iss}" already exists.`);
          break;
        }
      }
    }
    if (ti.keyResolutionMethod === KEY_RESOLUTION_JWKS_URL) {
      if (!(ti.jwksUri ?? '').trim()) {
        errors.push('JWKS URL is required when key method is JWKS URL.');
      }
    } else if (ti.keyResolutionMethod === KEY_RESOLUTION_PEM) {
      if (!(ti.certificate ?? '').trim()) {
        errors.push('PEM certificate is required when key method is PEM.');
      }
    }
    if (ti.userBindingEnabled) {
      const rows = ti._userBindingRows ?? [];
      const validCriteria = rows.filter((r) => (r.attribute ?? '').trim() && (r.expression ?? '').trim());
      if (validCriteria.length === 0) {
        errors.push('At least one user binding criterion (attribute and expression) is required when user binding is enabled.');
      }
    }
    return errors;
  }

  isFormValid(): boolean {
    return this.getValidationErrors().length === 0;
  }

  save() {
    const serialized = this.serializeTrustedIssuer();
    const issuers = deepClone(this.domain.tokenExchangeSettings?.trustedIssuers ?? []);

    if (this.createMode) {
      issuers.push(serialized);
    } else {
      issuers[this.issuerIndex] = serialized;
    }

    const payload = {
      ...this.domain,
      tokenExchangeSettings: {
        ...this.domain.tokenExchangeSettings,
        trustedIssuers: issuers,
      },
    };

    this.domainService.patchTokenExchangeSettings(this.domainId, payload).subscribe({
      next: (data) => {
        this.domainStore.set(data);
        this.domain = deepClone(data);
        this.formChanged = false;
        this.snackbarService.open(this.createMode ? 'Trusted issuer created' : 'Trusted issuer updated');
        const savedIssuers = data.tokenExchangeSettings?.trustedIssuers ?? [];
        if (this.createMode) {
          const serializedIssuer = (this.trustedIssuer.issuer ?? '').trim();
          const newIndex = savedIssuers.findIndex((i) => (i.issuer ?? '').trim() === serializedIssuer);
          this.router.navigate(['..', newIndex >= 0 ? newIndex : savedIssuers.length - 1], { relativeTo: this.route });
        } else if (this.issuerIndex < savedIssuers.length) {
          this.trustedIssuer = this.normalizeTrustedIssuer(savedIssuers[this.issuerIndex]);
        }
      },
      error: () => {
        this.formChanged = true;
      },
    });
  }

  private serializeTrustedIssuer(): any {
    const ti = this.trustedIssuer;
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
  }

  deleteIssuer() {
    const issuerLabel = (this.trustedIssuer.issuer ?? '').trim() || `Issuer #${this.issuerIndex + 1}`;
    this.dialogService
      .confirm('Delete Trusted Issuer', `Are you sure you want to delete "${issuerLabel}"?`)
      .pipe(
        filter((res) => res),
        switchMap(() => {
          const issuers = deepClone(this.domain.tokenExchangeSettings?.trustedIssuers ?? []);
          issuers.splice(this.issuerIndex, 1);
          return this.domainService.patchTokenExchangeSettings(this.domainId, {
            ...this.domain,
            tokenExchangeSettings: {
              ...this.domain.tokenExchangeSettings,
              trustedIssuers: issuers,
            },
          });
        }),
        tap((data) => {
          this.domainStore.set(data);
          this.snackbarService.open('Trusted issuer deleted');
          this.router.navigate(['..'], { relativeTo: this.route });
        }),
      )
      .subscribe({
        error: () => this.snackbarService.open('Failed to delete trusted issuer'),
      });
  }

  // Scope mapping methods
  addScopeMapping(): void {
    const ext = (this.newScopeStaging.key ?? '').trim();
    const dom = (this.newScopeStaging.value ?? '').trim();
    if (!ext || !dom) return;
    if (!this.trustedIssuer._scopeMappingRows) this.trustedIssuer._scopeMappingRows = [];
    const isDuplicate = this.trustedIssuer._scopeMappingRows.some((r) => r.key === ext && r.value === dom);
    if (isDuplicate) return;
    this.trustedIssuer._scopeMappingRows = [...this.trustedIssuer._scopeMappingRows, { key: ext, value: dom }];
    this.newScopeStaging = { key: '', value: '' };
    this.formChanged = true;
  }

  removeScopeMapping(rowIndex: number): void {
    if (this.trustedIssuer._scopeMappingRows) {
      this.trustedIssuer._scopeMappingRows = this.trustedIssuer._scopeMappingRows.filter((_, idx) => idx !== rowIndex);
      this.formChanged = true;
    }
  }

  canAddScopeMapping(): boolean {
    return !!(this.newScopeStaging.key?.trim() && this.newScopeStaging.value?.trim());
  }

  getAvailableDomainScopes(): any[] {
    if (!this.domainScopes?.length) return [];
    const usedKeys = new Set((this.trustedIssuer._scopeMappingRows ?? []).map((r) => r.value));
    return this.domainScopes.filter((s) => !usedKeys.has(s.key));
  }

  getDomainScopeLabel(scopeKey: string): string {
    const scope = this.domainScopes?.find((s) => s.key === scopeKey);
    return scope ? scope.name || scope.key : scopeKey;
  }

  // User binding methods
  addUserBindingCriterion(): void {
    const attr = (this.newUserBindingStaging.attribute ?? '').trim();
    const expr = (this.newUserBindingStaging.expression ?? '').trim();
    if (!attr || !expr) return;
    if (!this.trustedIssuer._userBindingRows) this.trustedIssuer._userBindingRows = [];
    const isDuplicate = this.trustedIssuer._userBindingRows.some((r) => r.attribute === attr && r.expression === expr);
    if (isDuplicate) return;
    this.trustedIssuer._userBindingRows = [...this.trustedIssuer._userBindingRows, { attribute: attr, expression: expr }];
    this.newUserBindingStaging = { attribute: '', expression: '' };
    this.formChanged = true;
  }

  removeUserBindingCriterion(rowIndex: number): void {
    if (this.trustedIssuer._userBindingRows) {
      this.trustedIssuer._userBindingRows = this.trustedIssuer._userBindingRows.filter((_, idx) => idx !== rowIndex);
      this.formChanged = true;
    }
  }

  canAddUserBindingCriterion(): boolean {
    return !!(this.newUserBindingStaging.attribute?.trim() && this.newUserBindingStaging.expression?.trim());
  }

  onFieldChange(): void {
    this.formChanged = true;
  }
}
