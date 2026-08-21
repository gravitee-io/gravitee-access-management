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
import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';

import { ScopeService } from '../../../../services/scope.service';
import {
  DEFAULT_REFRESH_INTERVAL_SECONDS,
  deriveNameFromIssuer,
  isValidTrustDomainName,
  keyMaterialErrors,
  TOKEN_EXCHANGE,
  TrustDomain,
  TrustDomainKeyMaterial,
  trustDomainKindLabel,
  UserBindingCriterion,
} from '../trust-domain.types';

interface ScopeStaging {
  key: string;
  value: string;
}

@Component({
  selector: 'app-trust-domain-form',
  templateUrl: './trust-domain-form.component.html',
  styleUrls: ['./trust-domain-form.component.scss'],
  standalone: false,
})
export class TrustDomainFormComponent implements OnInit, OnChanges, OnDestroy {
  @Input() trustDomain: TrustDomain;
  @Input() domainId: string;
  @Input() createMode = false;
  @Input() editMode = false;
  @Output() saved = new EventEmitter<TrustDomain>();

  readonly TOKEN_EXCHANGE = TOKEN_EXCHANGE;

  model: TrustDomain;
  scopeMappingRows: ScopeStaging[] = [];
  userBindingRows: UserBindingCriterion[] = [];
  algorithmInput = '';
  formChanged = false;
  nameEditedByOperator = false;

  newScopeStaging: ScopeStaging = { key: '', value: '' };
  newUserBindingStaging: UserBindingCriterion = { attribute: '', expression: '' };
  domainScopes: any[] = [];
  filteredDomainScopes: any[] = [];
  domainScopeCtrl = new UntypedFormControl();

  private destroy$ = new Subject<void>();

  constructor(private scopeService: ScopeService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['trustDomain']) {
      return;
    }
    this.model = this.normalize(this.trustDomain);
    this.scopeMappingRows = Object.entries(this.model.tokenExchange?.scopeMappings ?? {}).map(([key, value]) => ({ key, value }));
    this.userBindingRows = (this.model.tokenExchange?.userBindingCriteria ?? []).map((c) => ({
      attribute: c.attribute ?? '',
      expression: c.expression ?? '',
    }));
    this.nameEditedByOperator = !this.createMode;
    this.formChanged = false;
    if (this.isTokenExchange && !this.domainScopes.length) {
      this.loadDomainScopes();
    }
  }

  ngOnInit(): void {
    if (!this.editMode) {
      this.domainScopeCtrl.disable();
    }
    this.domainScopeCtrl.valueChanges.pipe(takeUntil(this.destroy$)).subscribe((value) => {
      if (typeof value === 'string') {
        this.newScopeStaging.value = '';
        const term = value.toLowerCase();
        this.filteredDomainScopes = this.availableDomainScopes.filter(
          (s) => s.key.toLowerCase().includes(term) || (s.name ?? '').toLowerCase().includes(term),
        );
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private normalize(td: TrustDomain): TrustDomain {
    return {
      id: td?.id,
      kind: td?.kind,
      name: td?.name ?? '',
      description: td?.description ?? '',
      keyMaterial: td?.keyMaterial,
      refreshIntervalSeconds: td?.refreshIntervalSeconds ?? DEFAULT_REFRESH_INTERVAL_SECONDS,
      allowedAlgorithms: td?.allowedAlgorithms ?? [],
      tokenExchange:
        td?.kind === TOKEN_EXCHANGE
          ? {
              issuer: td?.tokenExchange?.issuer ?? '',
              scopeMappings: td?.tokenExchange?.scopeMappings ?? {},
              userBindingEnabled: td?.tokenExchange?.userBindingEnabled ?? false,
              userBindingCriteria: td?.tokenExchange?.userBindingCriteria ?? [],
            }
          : undefined,
    };
  }

  get isTokenExchange(): boolean {
    return this.model?.kind === TOKEN_EXCHANGE;
  }

  get kindLabel(): string {
    return trustDomainKindLabel(this.model?.kind);
  }

  get keySource(): string {
    return this.model?.keyMaterial?.source;
  }

  onKeyMaterialChange(keyMaterial: TrustDomainKeyMaterial): void {
    this.model.keyMaterial = keyMaterial;
    this.onFieldChange();
  }

  onNameChange(): void {
    this.nameEditedByOperator = true;
    this.onFieldChange();
  }

  onIssuerChange(): void {
    if (!this.nameEditedByOperator) {
      this.model.name = deriveNameFromIssuer(this.model.tokenExchange.issuer);
    }
    this.onFieldChange();
  }

  onFieldChange(): void {
    this.formChanged = true;
  }

  addAlgorithm(value: string): void {
    const trimmed = (value ?? '').trim();
    if (trimmed && !this.model.allowedAlgorithms.includes(trimmed)) {
      this.model.allowedAlgorithms = [...this.model.allowedAlgorithms, trimmed];
      this.onFieldChange();
    }
    this.algorithmInput = '';
  }

  removeAlgorithm(alg: string): void {
    this.model.allowedAlgorithms = this.model.allowedAlgorithms.filter((a) => a !== alg);
    this.onFieldChange();
  }

  addScopeMapping(): void {
    const ext = (this.newScopeStaging.key ?? '').trim();
    const dom = (this.newScopeStaging.value ?? '').trim();
    if (!ext || !dom || this.scopeMappingRows.some((r) => r.key === ext && r.value === dom)) {
      return;
    }
    this.scopeMappingRows = [...this.scopeMappingRows, { key: ext, value: dom }];
    this.newScopeStaging = { key: '', value: '' };
    this.domainScopeCtrl.setValue('');
    this.refreshFilteredDomainScopes();
    this.onFieldChange();
  }

  removeScopeMapping(rowIndex: number): void {
    this.scopeMappingRows = this.scopeMappingRows.filter((_, idx) => idx !== rowIndex);
    this.refreshFilteredDomainScopes();
    this.onFieldChange();
  }

  canAddScopeMapping(): boolean {
    return !!(this.newScopeStaging.key?.trim() && this.newScopeStaging.value?.trim());
  }

  addUserBindingCriterion(): void {
    const attribute = (this.newUserBindingStaging.attribute ?? '').trim();
    const expression = (this.newUserBindingStaging.expression ?? '').trim();
    if (!attribute || !expression || this.userBindingRows.some((r) => r.attribute === attribute && r.expression === expression)) {
      return;
    }
    this.userBindingRows = [...this.userBindingRows, { attribute, expression }];
    this.newUserBindingStaging = { attribute: '', expression: '' };
    this.onFieldChange();
  }

  removeUserBindingCriterion(rowIndex: number): void {
    this.userBindingRows = this.userBindingRows.filter((_, idx) => idx !== rowIndex);
    this.onFieldChange();
  }

  canAddUserBindingCriterion(): boolean {
    return !!(this.newUserBindingStaging.attribute?.trim() && this.newUserBindingStaging.expression?.trim());
  }

  getValidationErrors(): string[] {
    const errors: string[] = [];
    const name = (this.model.name ?? '').trim().toLowerCase();
    if (!name) {
      errors.push('Name is required.');
    } else if (!isValidTrustDomainName(name)) {
      errors.push('Name must be a DNS-style label: lowercase letters, digits, "." or "-".');
    }
    if (this.isTokenExchange && !(this.model.tokenExchange.issuer ?? '').trim()) {
      errors.push('Issuer URL is required.');
    }
    errors.push(...keyMaterialErrors(this.model.keyMaterial));
    if (this.keySource === 'JWKS_URL' && !(this.model.refreshIntervalSeconds > 0)) {
      errors.push('Refresh interval must be a positive number of seconds.');
    }
    if (this.isTokenExchange && this.model.tokenExchange.userBindingEnabled && this.userBindingRows.length === 0) {
      errors.push('At least one user binding criterion (attribute and expression) is required when user binding is enabled.');
    }
    return errors;
  }

  isFormValid(): boolean {
    return this.getValidationErrors().length === 0;
  }

  submit(): void {
    if (!this.isFormValid()) {
      return;
    }
    this.saved.emit(this.payload());
  }

  private payload(): TrustDomain {
    const description = (this.model.description ?? '').trim();
    return {
      kind: this.model.kind,
      name: (this.model.name ?? '').trim().toLowerCase(),
      description: description || undefined,
      keyMaterial: this.model.keyMaterial,
      refreshIntervalSeconds: this.model.refreshIntervalSeconds,
      allowedAlgorithms: this.isTokenExchange ? undefined : this.model.allowedAlgorithms,
      tokenExchange: this.isTokenExchange ? this.tokenExchangePayload() : undefined,
    };
  }

  private tokenExchangePayload(): TrustDomain['tokenExchange'] {
    const scopeMappings: Record<string, string> = {};
    this.scopeMappingRows.forEach((row) => (scopeMappings[row.key] = row.value));
    const userBindingEnabled = this.model.tokenExchange.userBindingEnabled ?? false;
    return {
      issuer: (this.model.tokenExchange.issuer ?? '').trim(),
      scopeMappings: Object.keys(scopeMappings).length ? scopeMappings : undefined,
      userBindingEnabled,
      userBindingCriteria: userBindingEnabled && this.userBindingRows.length ? this.userBindingRows : undefined,
    };
  }

  private loadDomainScopes(): void {
    this.scopeService.findAllByDomain(this.domainId).subscribe({
      next: (scopes) => {
        this.domainScopes = scopes || [];
        this.refreshFilteredDomainScopes();
      },
      error: () => (this.domainScopes = []),
    });
  }

  get availableDomainScopes(): any[] {
    const used = new Set(this.scopeMappingRows.map((r) => r.value));
    return (this.domainScopes ?? []).filter((s) => !used.has(s.key));
  }

  refreshFilteredDomainScopes(): void {
    this.filteredDomainScopes = this.availableDomainScopes;
  }

  displayDomainScope = (key: string): string => this.domainScopeLabel(key);

  domainScopeLabel(scopeKey: string): string {
    if (!scopeKey) {
      return '';
    }
    const scope = this.domainScopes?.find((s) => s.key === scopeKey);
    return scope ? scope.name || scope.key : scopeKey;
  }

  onDomainScopeSelected(event): void {
    this.newScopeStaging.value = event.option.value;
    this.refreshFilteredDomainScopes();
    this.onFieldChange();
  }
}
