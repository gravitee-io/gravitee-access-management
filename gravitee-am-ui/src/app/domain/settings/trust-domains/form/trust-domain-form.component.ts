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
  AUD_SUB_MAPPING_MAX_LENGTH,
  AUDIENCE_MAX_LENGTH,
  CrossAppAccessResourceServer,
  CrossAppAccessSettings,
  DEFAULT_REFRESH_INTERVAL_SECONDS,
  deriveNameFromIssuer,
  isAbsoluteUri,
  isValidSpiffeTrustDomain,
  keyMaterialErrors,
  TRUST_DOMAIN_USAGE_OPTIONS,
  TrustDomain,
  TrustDomainKeyMaterial,
  trustDomainUsagesLabel,
  UserBindingCriterion,
} from '../trust-domain.types';

interface ScopeStaging {
  key: string;
  value: string;
}

/** One domain scope and the name the authority AM mints towards knows it by. */
interface OutboundScopeStaging {
  domainScope: string;
  externalScope: string;
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

  readonly TRUST_DOMAIN_USAGE_OPTIONS = TRUST_DOMAIN_USAGE_OPTIONS;

  model: TrustDomain;
  spiffeEnabled = false;
  issuerEnabled = false;
  tokenExchangeEnabled = false;
  crossAppAccessEnabled = false;
  scopeMappingRows: ScopeStaging[] = [];
  userBindingRows: UserBindingCriterion[] = [];
  resourceServerRows: CrossAppAccessResourceServer[] = [];
  outboundScopeMappingRows: OutboundScopeStaging[] = [];
  crossAppAccessAudience = '';
  audSubMapping = '';
  algorithmInput = '';
  formChanged = false;
  nameEditedByOperator = false;

  newScopeStaging: ScopeStaging = { key: '', value: '' };
  newUserBindingStaging: UserBindingCriterion = { attribute: '', expression: '' };
  newResourceServerStaging: CrossAppAccessResourceServer = { name: '', resource: '' };
  newOutboundScopeStaging: OutboundScopeStaging = { domainScope: '', externalScope: '' };
  domainScopes: any[] = [];
  filteredDomainScopes: any[] = [];
  filteredOutboundDomainScopes: any[] = [];
  domainScopeCtrl = new UntypedFormControl();
  outboundDomainScopeCtrl = new UntypedFormControl();

  private destroy$ = new Subject<void>();

  constructor(private scopeService: ScopeService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['trustDomain']) {
      return;
    }
    this.model = this.normalize(this.trustDomain);
    this.spiffeEnabled = !!this.trustDomain?.spiffeTrustDomain;
    this.tokenExchangeEnabled = !!this.trustDomain?.issuer || (this.createMode && !this.spiffeEnabled);
    this.crossAppAccessEnabled = !!this.trustDomain?.crossAppAccess?.enabled;
    this.issuerEnabled = this.tokenExchangeEnabled || this.crossAppAccessEnabled;
    this.scopeMappingRows = Object.entries(this.model.scopeMappings ?? {}).map(([key, value]) => ({ key, value }));
    this.userBindingRows = (this.model.userBindingCriteria ?? []).map((c) => ({
      attribute: c.attribute ?? '',
      expression: c.expression ?? '',
    }));
    const crossAppAccess = this.trustDomain?.crossAppAccess;
    this.resourceServerRows = (crossAppAccess?.resourceServers ?? []).map((rs) => ({
      id: rs.id,
      name: rs.name ?? '',
      resource: rs.resource ?? '',
    }));
    this.crossAppAccessAudience = crossAppAccess?.audience ?? '';
    this.audSubMapping = crossAppAccess?.audSubMapping ?? '';
    this.outboundScopeMappingRows = Object.entries(crossAppAccess?.scopeMappings ?? {}).map(([domainScope, externalScope]) => ({
      domainScope,
      externalScope,
    }));
    this.nameEditedByOperator = !this.createMode;
    this.formChanged = false;
    if (this.issuerEnabled && !this.domainScopes.length) {
      this.loadDomainScopes();
    }
  }

  ngOnInit(): void {
    if (!this.editMode) {
      this.domainScopeCtrl.disable();
      this.outboundDomainScopeCtrl.disable();
    }
    this.domainScopeCtrl.valueChanges.pipe(takeUntil(this.destroy$)).subscribe((value) => {
      if (typeof value === 'string') {
        this.newScopeStaging.value = '';
        this.filteredDomainScopes = matchingScopes(this.availableDomainScopes, value);
      }
    });
    this.outboundDomainScopeCtrl.valueChanges.pipe(takeUntil(this.destroy$)).subscribe((value) => {
      if (typeof value === 'string') {
        this.newOutboundScopeStaging.domainScope = '';
        this.filteredOutboundDomainScopes = matchingScopes(this.availableOutboundDomainScopes, value);
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
      name: td?.name ?? '',
      description: td?.description ?? '',
      spiffeTrustDomain: td?.spiffeTrustDomain ?? '',
      issuer: td?.issuer ?? '',
      keyMaterial: td?.keyMaterial,
      refreshIntervalSeconds: td?.refreshIntervalSeconds ?? DEFAULT_REFRESH_INTERVAL_SECONDS,
      allowedAlgorithms: td?.allowedAlgorithms ?? [],
      scopeMappings: td?.scopeMappings ?? {},
      userBindingEnabled: td?.userBindingEnabled ?? false,
      userBindingCriteria: td?.userBindingCriteria ?? [],
    };
  }

  get usagesLabel(): string {
    return trustDomainUsagesLabel(this.trustDomain);
  }

  get crossAppAccessOnly(): boolean {
    return this.crossAppAccessEnabled && !this.tokenExchangeEnabled && !this.spiffeEnabled;
  }

  onUsageToggle(): void {
    if (!this.issuerEnabled) {
      this.tokenExchangeEnabled = false;
      this.crossAppAccessEnabled = false;
    }
    if (this.issuerEnabled && !this.domainScopes.length) {
      this.loadDomainScopes();
    }
    this.onFieldChange();
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
      this.model.name = deriveNameFromIssuer(this.model.issuer);
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

  addResourceServer(): void {
    if (!this.canAddResourceServer()) {
      return;
    }
    this.resourceServerRows = [
      ...this.resourceServerRows,
      { name: this.newResourceServerStaging.name.trim(), resource: this.newResourceServerStaging.resource.trim() },
    ];
    this.newResourceServerStaging = { name: '', resource: '' };
    this.onFieldChange();
  }

  removeResourceServer(rowIndex: number): void {
    this.resourceServerRows = this.resourceServerRows.filter((_, idx) => idx !== rowIndex);
    this.onFieldChange();
  }

  canAddResourceServer(): boolean {
    return !!(this.newResourceServerStaging.name?.trim() && this.newResourceServerStaging.resource?.trim());
  }

  addOutboundScopeMapping(): void {
    if (!this.canAddOutboundScopeMapping()) {
      return;
    }
    this.outboundScopeMappingRows = [
      ...this.outboundScopeMappingRows,
      {
        domainScope: this.newOutboundScopeStaging.domainScope.trim(),
        externalScope: this.newOutboundScopeStaging.externalScope.trim(),
      },
    ];
    this.newOutboundScopeStaging = { domainScope: '', externalScope: '' };
    this.outboundDomainScopeCtrl.setValue('');
    this.refreshFilteredOutboundDomainScopes();
    this.onFieldChange();
  }

  removeOutboundScopeMapping(rowIndex: number): void {
    this.outboundScopeMappingRows = this.outboundScopeMappingRows.filter((_, idx) => idx !== rowIndex);
    this.refreshFilteredOutboundDomainScopes();
    this.onFieldChange();
  }

  canAddOutboundScopeMapping(): boolean {
    const domainScope = this.newOutboundScopeStaging.domainScope?.trim();
    const externalScope = this.newOutboundScopeStaging.externalScope?.trim();
    return !!(domainScope && externalScope) && !this.outboundScopeMappingRows.some((row) => row.domainScope === domainScope);
  }

  getValidationErrors(): string[] {
    const errors: string[] = [];
    if (!(this.model.name ?? '').trim()) {
      errors.push('Name is required.');
    }
    if (!this.spiffeEnabled && !this.issuerEnabled) {
      errors.push('Pick at least one usage: OIDC - Trusted Issuer, SPIFFE, or both.');
    }
    if (this.spiffeEnabled) {
      const spiffeTrustDomain = (this.model.spiffeTrustDomain ?? '').trim().toLowerCase();
      if (!spiffeTrustDomain) {
        errors.push('SPIFFE trust domain is required.');
      } else if (!isValidSpiffeTrustDomain(spiffeTrustDomain)) {
        errors.push('SPIFFE trust domain must be a DNS-style label: lowercase letters, digits, "." or "-".');
      }
    }
    if (this.issuerEnabled && !this.tokenExchangeEnabled && !this.crossAppAccessEnabled) {
      errors.push('Pick at least one of Token exchange or Cross App Access.');
    }
    if (this.tokenExchangeEnabled && !(this.model.issuer ?? '').trim()) {
      errors.push('Issuer URL is required.');
    }
    if (!this.crossAppAccessOnly) {
      errors.push(...keyMaterialErrors(this.model.keyMaterial));
      if (this.keySource === 'JWKS_URL' && !(this.model.refreshIntervalSeconds > 0)) {
        errors.push('Refresh interval must be a positive number of seconds.');
      }
    }
    if (this.tokenExchangeEnabled && this.model.userBindingEnabled && this.userBindingRows.length === 0) {
      errors.push('At least one user binding criterion (attribute and expression) is required when user binding is enabled.');
    }
    if (this.crossAppAccessEnabled) {
      errors.push(...this.crossAppAccessErrors());
    }
    return errors;
  }

  private crossAppAccessErrors(): string[] {
    const errors: string[] = [];
    const audience = this.crossAppAccessAudience.trim();
    if (!audience) {
      errors.push('Authorization server is required when Cross App Access is enabled.');
    } else if (!isAbsoluteUri(audience)) {
      errors.push(`Authorization server must be an absolute URI: ${audience}`);
    } else if (audience.length > AUDIENCE_MAX_LENGTH) {
      errors.push(`The authorization server must be at most ${AUDIENCE_MAX_LENGTH} characters.`);
    }
    if (this.resourceServerRows.length === 0) {
      errors.push('At least one resource server is required when Cross App Access is enabled.');
    }
    this.resourceServerRows
      .filter((rs) => !isAbsoluteUri(rs.resource))
      .forEach((rs) => errors.push(`Resource server resource must be an absolute URI: ${rs.resource}`));
    const resources = this.resourceServerRows.map((rs) => rs.resource);
    resources
      .filter((resource, idx) => resources.indexOf(resource) !== idx)
      .forEach((resource) => errors.push(`Resource server resource ${resource} is used more than once.`));
    if (this.audSubMapping.trim().length > AUD_SUB_MAPPING_MAX_LENGTH) {
      errors.push(`The aud_sub expression must be at most ${AUD_SUB_MAPPING_MAX_LENGTH} characters.`);
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
    const scopeMappings: Record<string, string> = {};
    this.scopeMappingRows.forEach((row) => (scopeMappings[row.key] = row.value));
    const userBindingEnabled = this.tokenExchangeEnabled && (this.model.userBindingEnabled ?? false);
    return {
      name: (this.model.name ?? '').trim(),
      description: description || undefined,
      spiffeTrustDomain: this.spiffeEnabled ? (this.model.spiffeTrustDomain ?? '').trim().toLowerCase() : undefined,
      issuer: this.issuerEnabled ? (this.tokenExchangeEnabled ? (this.model.issuer ?? '').trim() : '') : undefined,
      keyMaterial: this.model.keyMaterial,
      refreshIntervalSeconds: this.model.refreshIntervalSeconds,
      allowedAlgorithms: this.model.allowedAlgorithms,
      scopeMappings: this.tokenExchangeEnabled && Object.keys(scopeMappings).length ? scopeMappings : undefined,
      userBindingEnabled,
      userBindingCriteria: userBindingEnabled && this.userBindingRows.length ? this.userBindingRows : undefined,
      crossAppAccess: this.crossAppAccessPayload(),
    };
  }

  private crossAppAccessPayload(): CrossAppAccessSettings | undefined {
    if (!this.crossAppAccessEnabled) {
      return undefined;
    }
    const audSubMapping = this.audSubMapping.trim();
    const scopeMappings: Record<string, string> = {};
    this.outboundScopeMappingRows.forEach((row) => (scopeMappings[row.domainScope] = row.externalScope));
    return {
      enabled: true,
      audience: this.crossAppAccessAudience.trim() || undefined,
      resourceServers: this.resourceServerRows.length ? this.resourceServerRows : undefined,
      audSubMapping: audSubMapping || undefined,
      scopeMappings: Object.keys(scopeMappings).length ? scopeMappings : undefined,
    };
  }

  private loadDomainScopes(): void {
    this.scopeService.findAllByDomain(this.domainId).subscribe({
      next: (scopes) => {
        this.domainScopes = scopes || [];
        this.refreshFilteredDomainScopes();
        this.refreshFilteredOutboundDomainScopes();
      },
      error: () => (this.domainScopes = []),
    });
  }

  get availableDomainScopes(): any[] {
    const used = new Set(this.scopeMappingRows.map((r) => r.value));
    return (this.domainScopes ?? []).filter((s) => !used.has(s.key));
  }

  get availableOutboundDomainScopes(): any[] {
    const used = new Set(this.outboundScopeMappingRows.map((r) => r.domainScope));
    return (this.domainScopes ?? []).filter((s) => !used.has(s.key));
  }

  refreshFilteredDomainScopes(): void {
    this.filteredDomainScopes = this.availableDomainScopes;
  }

  refreshFilteredOutboundDomainScopes(): void {
    this.filteredOutboundDomainScopes = this.availableOutboundDomainScopes;
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

  onOutboundDomainScopeSelected(event): void {
    this.newOutboundScopeStaging.domainScope = event.option.value;
    this.refreshFilteredOutboundDomainScopes();
    this.onFieldChange();
  }
}

function matchingScopes(scopes: any[], term: string): any[] {
  const lowered = term.toLowerCase();
  return scopes.filter((s) => s.key.toLowerCase().includes(lowered) || (s.name ?? '').toLowerCase().includes(lowered));
}
