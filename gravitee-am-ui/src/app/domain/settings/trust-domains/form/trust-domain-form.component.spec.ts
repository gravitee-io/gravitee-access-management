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
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { of } from 'rxjs';

import { ScopeService } from '../../../../services/scope.service';
import { TrustDomain } from '../trust-domain.types';

import { TrustDomainFormComponent } from './trust-domain-form.component';

describe('TrustDomainFormComponent', () => {
  let component: TrustDomainFormComponent;
  let fixture: ComponentFixture<TrustDomainFormComponent>;
  let saved: TrustDomain[];

  beforeEach(waitForAsync(() => {
    const scopeServiceStub = {
      findAllByDomain: jest.fn().mockReturnValue(of([{ key: 'openid', name: 'OpenID' }])),
    } as Partial<ScopeService> as ScopeService;

    TestBed.configureTestingModule({
      declarations: [TrustDomainFormComponent],
      imports: [MatAutocompleteModule],
      providers: [{ provide: ScopeService, useValue: scopeServiceStub }],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();
  }));

  function build(trustDomain: Partial<TrustDomain>, createMode = true) {
    fixture = TestBed.createComponent(TrustDomainFormComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('domainId', 'domain-1');
    fixture.componentRef.setInput('createMode', createMode);
    fixture.componentRef.setInput('editMode', true);
    fixture.componentRef.setInput('trustDomain', trustDomain as TrustDomain);
    saved = [];
    component.saved.subscribe((td) => saved.push(td));
    fixture.detectChanges();
  }

  describe('SPIFFE usage', () => {
    beforeEach(() => {
      build({ name: '', spiffeTrustDomain: 'placeholder', refreshIntervalSeconds: 300 });
    });

    it('shouldSubmitTheSpiffeMatcherWithoutAnIssuer', () => {
      component.model.name = 'Prod Example';
      component.model.spiffeTrustDomain = 'Prod.Example';
      component.onKeyMaterialChange({ source: 'JWKS_URL', jwksUrl: 'https://issuer.example/keys' });
      component.addAlgorithm('RS256');

      component.submit();

      expect(saved).toHaveLength(1);
      expect(saved[0]).toEqual({
        name: 'Prod Example',
        description: undefined,
        spiffeTrustDomain: 'prod.example',
        issuer: undefined,
        keyMaterial: { source: 'JWKS_URL', jwksUrl: 'https://issuer.example/keys' },
        refreshIntervalSeconds: 300,
        allowedAlgorithms: ['RS256'],
        scopeMappings: undefined,
        userBindingEnabled: false,
        userBindingCriteria: undefined,
      });
    });

    it('shouldSendAnEmptyAllowedAlgorithmsListSoTheOverrideCanBeCleared', () => {
      component.model.name = 'prod-example';
      component.model.spiffeTrustDomain = 'prod.example';
      component.onKeyMaterialChange({ source: 'JWKS_URL', jwksUrl: 'https://issuer.example/keys' });
      component.addAlgorithm('RS256');
      component.removeAlgorithm('RS256');

      component.submit();

      expect(saved[0].allowedAlgorithms).toEqual([]);
    });

    it('shouldRejectASpiffeTrustDomainThatIsNotADnsStyleLabel', () => {
      component.model.name = 'prod-example';
      component.model.spiffeTrustDomain = '-nope-';
      component.onKeyMaterialChange({ source: 'JWKS_URL', jwksUrl: 'https://issuer.example/keys' });

      expect(component.getValidationErrors()).toContain(
        'SPIFFE trust domain must be a DNS-style label: lowercase letters, digits, "." or "-".',
      );
      component.submit();
      expect(saved).toHaveLength(0);
    });

    it('shouldAllowAFreeFormNameNowThatItIsOnlyALabel', () => {
      component.model.name = 'Acme Corp (prod)';
      component.model.spiffeTrustDomain = 'acme.org';
      component.onKeyMaterialChange({ source: 'JWKS_URL', jwksUrl: 'https://issuer.example/keys' });

      expect(component.getValidationErrors()).toEqual([]);
    });

    it('shouldSurfaceKeyMaterialErrors', () => {
      component.model.name = 'prod-example';
      component.model.spiffeTrustDomain = 'prod.example';
      component.onKeyMaterialChange({ source: 'JWKS_URL', jwksUrl: '' });

      expect(component.getValidationErrors()).toContain('JWKS URL is required when the key source is JWKS URL.');
    });
  });

  describe('trusted issuer usage', () => {
    beforeEach(() => {
      build({ name: '', refreshIntervalSeconds: 300 });
    });

    it('shouldDefaultToTheTrustedIssuerUsageOnCreate', () => {
      expect(component.issuerEnabled).toBe(true);
      expect(component.spiffeEnabled).toBe(false);
    });

    it('shouldSubmitTheIssuerMatcherWithoutASpiffeTrustDomain', () => {
      component.model.issuer = 'https://issuer.example.com';
      component.onIssuerChange();
      component.onKeyMaterialChange({ source: 'PEM', certificate: '-----BEGIN CERTIFICATE-----abc' });
      component.newScopeStaging = { key: 'external:read', value: 'openid' };
      component.addScopeMapping();

      component.submit();

      expect(saved).toHaveLength(1);
      expect(saved[0]).toEqual({
        name: 'https-issuer.example.com',
        description: undefined,
        spiffeTrustDomain: undefined,
        issuer: 'https://issuer.example.com',
        keyMaterial: { source: 'PEM', certificate: '-----BEGIN CERTIFICATE-----abc' },
        refreshIntervalSeconds: 300,
        allowedAlgorithms: [],
        scopeMappings: { 'external:read': 'openid' },
        userBindingEnabled: false,
        userBindingCriteria: undefined,
      });
    });

    it('shouldDeriveTheNameFromTheIssuerUntilTheOperatorEditsIt', () => {
      component.model.issuer = 'https://issuer.example.com';
      component.onIssuerChange();
      expect(component.model.name).toBe('https-issuer.example.com');

      component.onNameChange();
      component.model.name = 'chosen.name';
      component.model.issuer = 'https://other.example.com';
      component.onIssuerChange();

      expect(component.model.name).toBe('chosen.name');
    });

    it('shouldRequireAnIssuer', () => {
      component.model.name = 'external-idp';
      component.onKeyMaterialChange({ source: 'PEM', certificate: 'cert' });
      expect(component.getValidationErrors()).toContain('Issuer URL is required.');
    });

    it('shouldRequireAtLeastOneUsage', () => {
      component.model.name = 'external-idp';
      component.issuerEnabled = false;
      component.spiffeEnabled = false;
      component.onKeyMaterialChange({ source: 'PEM', certificate: 'cert' });

      expect(component.getValidationErrors()).toContain('Pick at least one usage: OIDC - Trusted Issuer, SPIFFE, or both.');
    });

    it('shouldRequireAtLeastOneCriterionWhenUserBindingIsEnabled', () => {
      component.model.issuer = 'https://issuer.example.com';
      component.onIssuerChange();
      component.onKeyMaterialChange({ source: 'PEM', certificate: 'cert' });
      component.model.userBindingEnabled = true;

      expect(component.getValidationErrors()).toContain(
        'At least one user binding criterion (attribute and expression) is required when user binding is enabled.',
      );
    });

    it('shouldSubmitUserBindingCriteriaWhenEnabled', () => {
      component.model.issuer = 'https://issuer.example.com';
      component.onIssuerChange();
      component.onKeyMaterialChange({ source: 'PEM', certificate: 'cert' });
      component.model.userBindingEnabled = true;
      component.newUserBindingStaging = { attribute: 'email', expression: "{#token['email']}" };
      component.addUserBindingCriterion();

      component.submit();

      expect(saved[0].userBindingCriteria).toEqual([{ attribute: 'email', expression: "{#token['email']}" }]);
    });
  });

  describe('both usages', () => {
    it('shouldSubmitBothMatchersOverSharedKeyMaterial', () => {
      build({ name: '', refreshIntervalSeconds: 300 });
      component.spiffeEnabled = true;
      component.onUsageToggle();
      component.model.name = 'acme-corp';
      component.model.spiffeTrustDomain = 'acme.org';
      component.model.issuer = 'https://sso.acme.com';
      component.onNameChange();
      component.onKeyMaterialChange({ source: 'JWKS_URL', jwksUrl: 'https://sso.acme.com/keys' });

      component.submit();

      expect(saved[0].spiffeTrustDomain).toBe('acme.org');
      expect(saved[0].issuer).toBe('https://sso.acme.com');
      expect(saved[0].keyMaterial).toEqual({ source: 'JWKS_URL', jwksUrl: 'https://sso.acme.com/keys' });
    });

    it('shouldClearTheSpiffeMatcherWhenTheUsageIsUnchecked', () => {
      build(
        {
          id: 'td-1',
          name: 'acme-corp',
          spiffeTrustDomain: 'acme.org',
          issuer: 'https://sso.acme.com',
          keyMaterial: { source: 'PEM', certificate: 'cert' },
          refreshIntervalSeconds: 300,
        },
        false,
      );
      expect(component.spiffeEnabled).toBe(true);

      component.spiffeEnabled = false;
      component.onUsageToggle();
      component.submit();

      expect(saved[0].spiffeTrustDomain).toBeUndefined();
      expect(saved[0].issuer).toBe('https://sso.acme.com');
    });
  });

  describe('edit mode', () => {
    it('shouldLoadExistingScopeMappingsAndCriteriaIntoTheForm', () => {
      build(
        {
          id: 'td-1',
          name: 'issuer.example',
          issuer: 'https://issuer.example',
          keyMaterial: { source: 'JWKS_URL', jwksUrl: 'https://issuer.example/keys' },
          refreshIntervalSeconds: 600,
          scopeMappings: { 'external:read': 'openid' },
          userBindingEnabled: true,
          userBindingCriteria: [{ attribute: 'email', expression: 'email' }],
        },
        false,
      );

      expect(component.scopeMappingRows).toEqual([{ key: 'external:read', value: 'openid' }]);
      expect(component.userBindingRows).toEqual([{ attribute: 'email', expression: 'email' }]);
      expect(component.model.name).toBe('issuer.example');
    });

    it('shouldNotDeriveTheNameFromTheIssuerOnAnExistingTrustedDomain', () => {
      build(
        {
          id: 'td-1',
          name: 'issuer.example',
          issuer: 'https://issuer.example',
          keyMaterial: { source: 'PEM', certificate: 'cert' },
        },
        false,
      );

      component.model.issuer = 'https://renamed.example';
      component.onIssuerChange();

      expect(component.model.name).toBe('issuer.example');
    });
  });
});
