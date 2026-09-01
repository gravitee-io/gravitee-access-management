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
import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';

import { JwkSet, KEY_MATERIAL_SOURCE_OPTIONS, KeyMaterialSource, TrustDomainKeyMaterial } from '../trust-domain.types';

@Component({
  selector: 'app-trust-domain-key-material',
  templateUrl: './trust-domain-key-material.component.html',
  styleUrls: ['./trust-domain-key-material.component.scss'],
  standalone: false,
})
export class TrustDomainKeyMaterialComponent implements OnChanges {
  @Input() keyMaterial: TrustDomainKeyMaterial;
  @Input() disabled = false;
  @Output() keyMaterialChange = new EventEmitter<TrustDomainKeyMaterial>();

  readonly KEY_MATERIAL_SOURCE_OPTIONS = KEY_MATERIAL_SOURCE_OPTIONS;

  source: KeyMaterialSource = 'JWKS_URL';
  jwksUrl = '';
  jwkSetText = '';
  certificate = '';
  jwkSetError = '';

  private emitted: TrustDomainKeyMaterial;

  ngOnChanges(): void {
    if (this.keyMaterial === this.emitted) {
      return;
    }
    this.source = this.keyMaterial?.source ?? 'JWKS_URL';
    this.jwksUrl = this.keyMaterial?.jwksUrl ?? '';
    this.certificate = this.keyMaterial?.certificate ?? '';
    this.jwkSetText = this.keyMaterial?.jwkSet ? JSON.stringify(this.keyMaterial.jwkSet, null, 2) : '';
    this.jwkSetError = '';
  }

  onSourceChange(): void {
    this.jwkSetError = '';
    this.onFieldChange();
  }

  onFieldChange(): void {
    this.emitted = this.currentKeyMaterial();
    this.keyMaterialChange.emit(this.emitted);
  }

  private currentKeyMaterial(): TrustDomainKeyMaterial {
    switch (this.source) {
      case 'JWKS_URL':
        return { source: 'JWKS_URL', jwksUrl: this.jwksUrl.trim() };
      case 'JWK_SET':
        return { source: 'JWK_SET', jwkSet: this.parseJwkSet() };
      case 'PEM':
        return { source: 'PEM', certificate: this.certificate.trim() };
    }
  }

  private parseJwkSet(): JwkSet | undefined {
    const text = this.jwkSetText.trim();
    if (!text) {
      this.jwkSetError = '';
      return undefined;
    }
    try {
      const parsed = JSON.parse(text);
      this.jwkSetError = Array.isArray(parsed?.keys) ? '' : 'The JWK set must be an object with a "keys" array.';
      return this.jwkSetError ? undefined : parsed;
    } catch {
      this.jwkSetError = 'The JWK set must be valid JSON.';
      return undefined;
    }
  }
}
