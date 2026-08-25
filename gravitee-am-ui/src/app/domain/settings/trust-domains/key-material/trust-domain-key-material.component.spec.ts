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
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';

import { TrustDomainKeyMaterial } from '../trust-domain.types';

import { TrustDomainKeyMaterialComponent } from './trust-domain-key-material.component';

describe('TrustDomainKeyMaterialComponent', () => {
  let component: TrustDomainKeyMaterialComponent;
  let fixture: ComponentFixture<TrustDomainKeyMaterialComponent>;
  let emitted: TrustDomainKeyMaterial[];

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [TrustDomainKeyMaterialComponent],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(TrustDomainKeyMaterialComponent);
    component = fixture.componentInstance;
    emitted = [];
    component.keyMaterialChange.subscribe((km) => emitted.push(km));
  });

  function hydrate(keyMaterial: TrustDomainKeyMaterial | undefined) {
    component.keyMaterial = keyMaterial;
    component.ngOnChanges();
    fixture.detectChanges();
  }

  function writeBack() {
    component.keyMaterial = emitted[emitted.length - 1];
    component.ngOnChanges();
    fixture.detectChanges();
  }

  it('shouldDefaultToJwksUrlWhenNoKeyMaterialIsSupplied', () => {
    hydrate(undefined);
    expect(component.source).toBe('JWKS_URL');
  });

  it('shouldEmitJwksUrlOnly', () => {
    hydrate({ source: 'JWKS_URL' });
    component.jwksUrl = 'https://issuer.example/keys';
    component.onFieldChange();
    expect(emitted.pop()).toEqual({ source: 'JWKS_URL', jwksUrl: 'https://issuer.example/keys' });
  });

  it('shouldEmitCertificateOnlyWhenSourceIsPem', () => {
    hydrate({ source: 'JWKS_URL', jwksUrl: 'https://issuer.example/keys' });
    component.source = 'PEM';
    component.certificate = '-----BEGIN CERTIFICATE-----abc-----END CERTIFICATE-----';
    component.onFieldChange();
    expect(emitted.pop()).toEqual({ source: 'PEM', certificate: '-----BEGIN CERTIFICATE-----abc-----END CERTIFICATE-----' });
  });

  it('shouldEmitParsedJwkSet', () => {
    hydrate({ source: 'JWK_SET' });
    component.jwkSetText = '{"keys":[{"kty":"RSA"}]}';
    component.onFieldChange();
    expect(component.jwkSetError).toBe('');
    expect(emitted.pop()).toEqual({ source: 'JWK_SET', jwkSet: { keys: [{ kty: 'RSA' }] } });
  });

  it('shouldReportUnparseableJwkSetAndEmitNoKeys', () => {
    hydrate({ source: 'JWK_SET' });
    component.jwkSetText = 'not json';
    component.onFieldChange();
    expect(component.jwkSetError).toContain('valid JSON');
    expect(emitted.pop()).toEqual({ source: 'JWK_SET', jwkSet: undefined });
  });

  it('shouldKeepPartialJwkSetTextWhenTheParentWritesTheEmittedValueBack', () => {
    hydrate({ source: 'JWK_SET' });

    component.jwkSetText = '{"keys":';
    component.onFieldChange();
    writeBack();

    expect(component.jwkSetText).toBe('{"keys":');
    expect(component.jwkSetError).toContain('valid JSON');
  });

  it('shouldKeepAJwksUrlBeingTypedWhenTheParentWritesTheEmittedValueBack', () => {
    hydrate({ source: 'JWKS_URL' });

    component.jwksUrl = 'https://issuer.example/keys ';
    component.onFieldChange();
    writeBack();

    expect(component.jwksUrl).toBe('https://issuer.example/keys ');
  });

  it('shouldRehydrateWhenTheParentSuppliesADifferentTrustedDomain', () => {
    hydrate({ source: 'JWKS_URL', jwksUrl: 'https://first.example/keys' });
    hydrate({ source: 'PEM', certificate: 'cert' });

    expect(component.source).toBe('PEM');
    expect(component.certificate).toBe('cert');
  });

  it('shouldRenderAnExistingJwkSetAsFormattedJson', () => {
    hydrate({ source: 'JWK_SET', jwkSet: { keys: [{ kty: 'RSA' }] } });
    expect(JSON.parse(component.jwkSetText)).toEqual({ keys: [{ kty: 'RSA' }] });
  });
});
