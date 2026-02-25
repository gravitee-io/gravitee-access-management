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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';

import { KEY_RESOLUTION_JWKS_URL, KEY_RESOLUTION_PEM, TrustedIssuer } from '../token-exchange.types';

/** Staging entry for a new scope mapping (external scope -> domain scope key). */
interface ScopeStaging {
  key: string;
  value: string;
}

/** Staging entry for a new user binding criterion. */
interface UserBindingStaging {
  attribute: string;
  expression: string;
}

@Component({
  selector: 'app-trusted-issuers-section',
  templateUrl: './trusted-issuers-section.component.html',
  styleUrls: ['./trusted-issuers-section.component.scss'],
  standalone: false,
})
export class TrustedIssuersSectionComponent implements OnChanges {
  readonly KEY_RESOLUTION_JWKS_URL = KEY_RESOLUTION_JWKS_URL;
  readonly KEY_RESOLUTION_PEM = KEY_RESOLUTION_PEM;

  readonly KEY_RESOLUTION_OPTIONS = [
    { value: KEY_RESOLUTION_JWKS_URL, label: 'JWKS URL' },
    { value: KEY_RESOLUTION_PEM, label: 'PEM Certificate' },
  ];

  @Input() trustedIssuers: TrustedIssuer[] = [];
  @Input() domainScopes: any[] = [];
  @Input() editMode: boolean;
  @Input() formValid = true;

  @Output() trustedIssuersChange = new EventEmitter<void>();

  /** Staging row for new scope mapping, one per issuer index. */
  newScopeStaging: ScopeStaging[] = [];
  /** Staging row for new user binding criterion, one per issuer index. */
  newUserBindingStaging: UserBindingStaging[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['trustedIssuers']) {
      this.ensureStagingLength();
    }
  }

  addTrustedIssuer(): void {
    if (!this.trustedIssuers) {
      this.trustedIssuers = [];
    }
    this.trustedIssuers.push({
      issuer: '',
      keyResolutionMethod: KEY_RESOLUTION_JWKS_URL,
      jwksUri: '',
      _scopeMappingRows: [],
      userBindingEnabled: false,
      _userBindingRows: [],
    });
    this.newScopeStaging.push({ key: '', value: '' });
    this.newUserBindingStaging.push({ attribute: '', expression: '' });
    this.trustedIssuersChange.emit();
  }

  removeTrustedIssuer(index: number): void {
    this.trustedIssuers.splice(index, 1);
    this.newScopeStaging.splice(index, 1);
    this.newUserBindingStaging.splice(index, 1);
    this.trustedIssuersChange.emit();
  }

  ensureStagingLength(): void {
    const n = this.trustedIssuers?.length ?? 0;
    while (this.newScopeStaging.length < n) {
      this.newScopeStaging.push({ key: '', value: '' });
    }
    this.newScopeStaging.splice(n);
    while (this.newUserBindingStaging.length < n) {
      this.newUserBindingStaging.push({ attribute: '', expression: '' });
    }
    this.newUserBindingStaging.splice(n);
  }

  addScopeMapping(issuerIndex: number): void {
    this.ensureStagingLength();
    const staging = this.newScopeStaging[issuerIndex];
    const ext = (staging?.key ?? '').trim();
    const dom = (staging?.value ?? '').trim();
    if (!ext || !dom) return;
    const ti = this.trustedIssuers[issuerIndex];
    if (!ti._scopeMappingRows) ti._scopeMappingRows = [];
    const isDuplicate = ti._scopeMappingRows.some((r) => r.key === ext && r.value === dom);
    if (isDuplicate) return;
    ti._scopeMappingRows = [...ti._scopeMappingRows, { key: ext, value: dom }];
    this.newScopeStaging[issuerIndex] = { key: '', value: '' };
    this.trustedIssuersChange.emit();
  }

  removeScopeMapping(issuerIndex: number, rowIndex: number): void {
    const ti = this.trustedIssuers[issuerIndex];
    if (ti._scopeMappingRows) {
      ti._scopeMappingRows = ti._scopeMappingRows.filter((_, idx) => idx !== rowIndex);
      this.trustedIssuersChange.emit();
    }
  }

  addUserBindingCriterion(issuerIndex: number): void {
    this.ensureStagingLength();
    const staging = this.newUserBindingStaging[issuerIndex];
    const attr = (staging?.attribute ?? '').trim();
    const expr = (staging?.expression ?? '').trim();
    if (!attr || !expr) return;
    const ti = this.trustedIssuers[issuerIndex];
    if (!ti._userBindingRows) ti._userBindingRows = [];
    const isDuplicate = ti._userBindingRows.some((r) => r.attribute === attr && r.expression === expr);
    if (isDuplicate) return;
    ti._userBindingRows = [...ti._userBindingRows, { attribute: attr, expression: expr }];
    this.newUserBindingStaging[issuerIndex] = { attribute: '', expression: '' };
    this.trustedIssuersChange.emit();
  }

  removeUserBindingCriterion(issuerIndex: number, rowIndex: number): void {
    const ti = this.trustedIssuers[issuerIndex];
    if (ti._userBindingRows) {
      ti._userBindingRows = ti._userBindingRows.filter((_, idx) => idx !== rowIndex);
      this.trustedIssuersChange.emit();
    }
  }

  getAvailableDomainScopes(issuerIndex: number): any[] {
    if (!this.domainScopes?.length) return [];
    const ti = this.trustedIssuers[issuerIndex];
    const usedKeys = new Set((ti._scopeMappingRows ?? []).map((r) => r.value));
    return this.domainScopes.filter((s) => !usedKeys.has(s.key));
  }

  getDomainScopeLabel(scopeKey: string): string {
    const scope = this.domainScopes?.find((s) => s.key === scopeKey);
    return scope ? `${scope.name || scope.key}${scope.description ? ' | ' + scope.description : ''}` : scopeKey;
  }

  canAddScopeMapping(issuerIndex: number): boolean {
    const s = this.newScopeStaging[issuerIndex];
    return !!(s?.key?.trim() && s?.value?.trim());
  }

  canAddUserBindingCriterion(issuerIndex: number): boolean {
    const s = this.newUserBindingStaging[issuerIndex];
    return !!(s?.attribute?.trim() && s?.expression?.trim());
  }

  toggleCollapse(index: number): void {
    this.trustedIssuers[index]._collapsed = !this.trustedIssuers[index]._collapsed;
  }

  getIssuerSummary(trustedIssuer: TrustedIssuer): string {
    const iss = (trustedIssuer.issuer ?? '').trim();
    return iss || 'Not configured';
  }

  onFieldChange(): void {
    this.trustedIssuersChange.emit();
  }
}
