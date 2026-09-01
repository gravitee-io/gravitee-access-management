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

  function build(trustDomain: Partial<TrustDomain>, createMode = true, editMode = true) {
    fixture = TestBed.createComponent(TrustDomainFormComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('domainId', 'domain-1');
    fixture.componentRef.setInput('createMode', createMode);
    fixture.componentRef.setInput('editMode', editMode);
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
  describe('Cross App Access', () => {
    const trustedIssuer = {
      id: 'td-1',
      name: 'acme-corp',
      issuer: 'https://sso.acme.com',
      keyMaterial: { source: 'PEM' as const, certificate: 'cert' },
      refreshIntervalSeconds: 300,
    };

    function testId(id: string): HTMLElement {
      return fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
    }

    it('shouldBeOffByDefaultAndHideEveryFieldBelowIt', () => {
      build(trustedIssuer, false);

      expect(component.crossAppAccessEnabled).toBe(false);
      expect(testId('usageChoice-CROSS_APP_ACCESS')).toBeTruthy();
      expect(testId('resourceServerNameInput')).toBeFalsy();
      expect(testId('audSubMappingInput')).toBeFalsy();
      expect(testId('outboundExternalScopeInput')).toBeFalsy();
    });

    it('shouldRevealItsFieldsOnceEnabled', () => {
      build(trustedIssuer, false);
      component.crossAppAccessEnabled = true;
      component.onUsageToggle();
      fixture.detectChanges();

      expect(testId('resourceServerNameInput')).toBeTruthy();
      expect(testId('resourceServerResourceInput')).toBeTruthy();
      expect(testId('audSubMappingInput')).toBeTruthy();
      expect(testId('outboundExternalScopeInput')).toBeTruthy();
    });

    it('shouldOfferCrossAppAccessOnTheCreationForm', () => {
      build({ name: '', refreshIntervalSeconds: 300 });

      expect(component.issuerEnabled).toBe(true);
      expect(component.tokenExchangeEnabled).toBe(true);
      expect(component.crossAppAccessEnabled).toBe(false);
      expect(testId('usageChoice-CROSS_APP_ACCESS')).toBeTruthy();
      expect(testId('resourceServerNameInput')).toBeFalsy();
    });

    it('shouldCreateACrossAppAccessOnlyTrustedDomainInOnePass', () => {
      build({ name: '', refreshIntervalSeconds: 300 });
      component.model.name = 'acme-suite';
      component.onNameChange();
      component.tokenExchangeEnabled = false;
      component.crossAppAccessEnabled = true;
      component.onUsageToggle();
      component.crossAppAccessAudience = 'https://auth.acme.com';
      component.newResourceServerStaging = { name: 'Acme Calendar', resource: 'https://calendar.acme.com' };
      component.addResourceServer();

      expect(component.crossAppAccessOnly).toBe(true);
      expect(component.getValidationErrors()).toEqual([]);

      component.submit();

      expect(saved[0].issuer).toBe('');
      expect(saved[0].spiffeTrustDomain).toBeUndefined();
      expect(saved[0].keyMaterial).toBeUndefined();
      expect(saved[0].crossAppAccess).toEqual({
        enabled: true,
        audience: 'https://auth.acme.com',
        resourceServers: [{ name: 'Acme Calendar', resource: 'https://calendar.acme.com' }],
        audSubMapping: undefined,
        scopeMappings: undefined,
      });
    });

    it('shouldShowNeitherSubsectionOnASpiffeOnlyTrustedDomain', () => {
      build({ id: 'td-2', name: 'spire', spiffeTrustDomain: 'spire.example', keyMaterial: { source: 'PEM', certificate: 'cert' } }, false);

      expect(testId('usageChoice-CROSS_APP_ACCESS')).toBeFalsy();
      expect(testId('scopeMappingsTable')).toBeFalsy();
    });

    it('shouldKeepTheAddResourceServerButtonDisabledUntilTheRowIsValid', () => {
      build(trustedIssuer, false);
      component.crossAppAccessEnabled = true;
      fixture.detectChanges();

      expect(component.canAddResourceServer()).toBe(false);
      expect((testId('addResourceServerButton') as HTMLButtonElement).disabled).toBe(true);

      component.newResourceServerStaging = { name: 'Acme Calendar', resource: '' };
      expect(component.canAddResourceServer()).toBe(false);

      component.newResourceServerStaging = { name: 'Acme Calendar', resource: 'https://calendar.acme.com' };
      fixture.detectChanges();
      expect(component.canAddResourceServer()).toBe(true);
      expect((testId('addResourceServerButton') as HTMLButtonElement).disabled).toBe(false);
    });

    it('shouldAddAndRemoveResourceServers', () => {
      build(trustedIssuer, false);
      component.crossAppAccessEnabled = true;

      component.newResourceServerStaging = { name: 'Acme Calendar', resource: 'https://calendar.acme.com' };
      component.addResourceServer();
      component.newResourceServerStaging = { name: 'Acme Mail', resource: 'https://mail.acme.com' };
      component.addResourceServer();

      expect(component.resourceServerRows).toEqual([
        { name: 'Acme Calendar', resource: 'https://calendar.acme.com' },
        { name: 'Acme Mail', resource: 'https://mail.acme.com' },
      ]);
      expect(component.newResourceServerStaging).toEqual({ name: '', resource: '' });

      component.removeResourceServer(0);
      expect(component.resourceServerRows).toEqual([{ name: 'Acme Mail', resource: 'https://mail.acme.com' }]);
    });

    it('shouldRejectTwoResourceServersSharingAResource', () => {
      build(trustedIssuer, false);
      component.crossAppAccessEnabled = true;
      component.newResourceServerStaging = { name: 'Acme Calendar', resource: 'https://calendar.acme.com' };
      component.addResourceServer();
      component.newResourceServerStaging = { name: 'Acme Agenda', resource: 'https://calendar.acme.com' };
      component.addResourceServer();

      expect(component.getValidationErrors()).toContain('Resource server resource https://calendar.acme.com is used more than once.');
      component.submit();
      expect(saved).toHaveLength(0);
    });

    it('shouldDropInboundSettingsTheApiWouldRejectWhenTokenExchangeGoesOff', () => {
      build(
        {
          ...trustedIssuer,
          scopeMappings: { 'external:read': 'openid' },
          userBindingEnabled: true,
          userBindingCriteria: [{ attribute: 'email', expression: 'email' }],
          crossAppAccess: {
            enabled: true,
            audience: 'https://auth.acme.com',
            resourceServers: [{ name: 'Acme Calendar', resource: 'https://calendar.acme.com' }],
          },
        },
        false,
      );
      component.tokenExchangeEnabled = false;
      component.onUsageToggle();

      expect(component.getValidationErrors()).toEqual([]);
      component.submit();

      expect(saved[0].issuer).toBe('');
      expect(saved[0].scopeMappings).toBeUndefined();
      expect(saved[0].userBindingEnabled).toBe(false);
      expect(saved[0].userBindingCriteria).toBeUndefined();
    });

    it('shouldRejectAResourceThatIsNotAnAbsoluteUri', () => {
      build(trustedIssuer, false);
      component.crossAppAccessEnabled = true;
      component.newResourceServerStaging = { name: 'Acme Calendar', resource: 'calendar.acme.com' };
      component.addResourceServer();

      expect(component.getValidationErrors()).toContain('Resource server resource must be an absolute URI: calendar.acme.com');
      component.submit();
      expect(saved).toHaveLength(0);
    });

    it('shouldRejectADuplicateDomainScope', () => {
      build(trustedIssuer, false);
      component.crossAppAccessEnabled = true;
      component.newOutboundScopeStaging = { domainScope: 'openid', externalScope: 'acme:openid' };
      component.addOutboundScopeMapping();

      component.newOutboundScopeStaging = { domainScope: 'openid', externalScope: 'acme:profile' };
      expect(component.canAddOutboundScopeMapping()).toBe(false);
      component.addOutboundScopeMapping();

      expect(component.outboundScopeMappingRows).toEqual([{ domainScope: 'openid', externalScope: 'acme:openid' }]);
    });

    it('shouldSubmitTheWholeBlock', () => {
      build(trustedIssuer, false);
      component.crossAppAccessEnabled = true;
      component.onUsageToggle();
      component.crossAppAccessAudience = 'https://auth.acme.com';
      component.newResourceServerStaging = { name: 'Acme Calendar', resource: 'https://calendar.acme.com' };
      component.addResourceServer();
      component.audSubMapping = '{#user.email}';
      component.newOutboundScopeStaging = { domainScope: 'openid', externalScope: 'acme:openid' };
      component.addOutboundScopeMapping();

      component.submit();

      expect(saved[0].crossAppAccess).toEqual({
        enabled: true,
        audience: 'https://auth.acme.com',
        resourceServers: [{ name: 'Acme Calendar', resource: 'https://calendar.acme.com' }],
        audSubMapping: '{#user.email}',
        scopeMappings: { openid: 'acme:openid' },
      });
    });

    it('shouldLoadAnExistingBlockAndEchoResourceServerIdsBack', () => {
      build(
        {
          ...trustedIssuer,
          crossAppAccess: {
            enabled: true,
            audience: 'https://auth.acme.com',
            resourceServers: [{ id: 'rs-1', name: 'Acme Calendar', resource: 'https://calendar.acme.com' }],
            audSubMapping: '{#user.email}',
            scopeMappings: { openid: 'acme:openid' },
          },
        },
        false,
      );

      expect(component.crossAppAccessEnabled).toBe(true);
      expect(component.audSubMapping).toBe('{#user.email}');
      expect(component.outboundScopeMappingRows).toEqual([{ domainScope: 'openid', externalScope: 'acme:openid' }]);

      component.resourceServerRows[0].name = 'Acme Agenda';
      component.onFieldChange();
      component.submit();

      expect(saved[0].crossAppAccess.resourceServers).toEqual([{ id: 'rs-1', name: 'Acme Agenda', resource: 'https://calendar.acme.com' }]);
    });

    it('shouldStopRequiringAnIssuerAndKeyMaterialWhenCrossAppAccessIsTheOnlyUsage', () => {
      build(
        {
          ...trustedIssuer,
          crossAppAccess: {
            enabled: true,
            audience: 'https://auth.acme.com',
            resourceServers: [{ name: 'Acme Calendar', resource: 'https://calendar.acme.com' }],
          },
        },
        false,
      );
      component.tokenExchangeEnabled = false;
      component.onUsageToggle();
      component.model.keyMaterial = undefined;
      fixture.detectChanges();

      expect(component.crossAppAccessOnly).toBe(true);
      expect(component.getValidationErrors()).toEqual([]);
      expect(fixture.nativeElement.querySelector('app-trust-domain-key-material')).toBeFalsy();

      component.submit();
      expect(saved[0].issuer).toBe('');
    });

    it('shouldSendAnEmptyIssuerSoTheServerClearsItInsteadOfLeavingItAlone', () => {
      build(
        {
          ...trustedIssuer,
          crossAppAccess: {
            enabled: true,
            audience: 'https://auth.acme.com',
            resourceServers: [{ name: 'Acme Calendar', resource: 'https://calendar.acme.com' }],
          },
        },
        false,
      );
      component.tokenExchangeEnabled = false;
      component.onUsageToggle();

      component.submit();

      expect(saved[0]).toHaveProperty('issuer', '');
      expect(saved[0].spiffeTrustDomain).toBeUndefined();
    });

    it('shouldRequireAtLeastOneResourceServerWhenCrossAppAccessIsEnabled', () => {
      build({ ...trustedIssuer, crossAppAccess: { enabled: true, audience: 'https://auth.acme.com' } }, false);

      expect(component.getValidationErrors()).toContain('At least one resource server is required when Cross App Access is enabled.');

      component.newResourceServerStaging = { name: 'Acme Calendar', resource: 'https://calendar.acme.com' };
      component.addResourceServer();

      expect(component.getValidationErrors()).toEqual([]);
    });

    it('shouldRequireAnAuthorizationServerWhenCrossAppAccessIsEnabled', () => {
      build(
        {
          ...trustedIssuer,
          crossAppAccess: { enabled: true, resourceServers: [{ name: 'Acme Calendar', resource: 'https://calendar.acme.com' }] },
        },
        false,
      );
      component.crossAppAccessAudience = '';

      expect(component.getValidationErrors()).toContain('Authorization server is required when Cross App Access is enabled.');

      component.crossAppAccessAudience = 'auth.acme.com';
      expect(component.getValidationErrors()).toContain('Authorization server must be an absolute URI: auth.acme.com');

      component.crossAppAccessAudience = 'https://auth.acme.com';
      expect(component.getValidationErrors()).toEqual([]);
    });

    it('shouldKeepTheParentUsageCheckedOnATrustedDomainThatOnlyDoesCrossAppAccess', () => {
      build({ id: 'td-1', name: 'acme-suite', crossAppAccess: { enabled: true } }, false);

      expect(component.issuerEnabled).toBe(true);
      expect(component.tokenExchangeEnabled).toBe(false);
      expect(component.spiffeEnabled).toBe(false);
      expect(component.crossAppAccessOnly).toBe(true);
      expect(testId('usageChoice-CROSS_APP_ACCESS')).toBeTruthy();
      expect(testId('issuerUrlInput')).toBeFalsy();
    });

    it('shouldRequireOneOfTokenExchangeOrCrossAppAccessUnderTheOidcUsage', () => {
      build(trustedIssuer, false);
      component.tokenExchangeEnabled = false;
      component.onUsageToggle();

      expect(component.issuerEnabled).toBe(true);
      expect(component.getValidationErrors()).toContain('Pick at least one of Token exchange or Cross App Access.');
    });

    it('shouldClearBothChildrenWhenTheOidcUsageIsUnchecked', () => {
      build({ ...trustedIssuer, spiffeTrustDomain: 'acme.org', crossAppAccess: { enabled: true } }, false);
      expect(component.tokenExchangeEnabled).toBe(true);

      component.issuerEnabled = false;
      component.onUsageToggle();

      expect(component.tokenExchangeEnabled).toBe(false);
      expect(component.crossAppAccessEnabled).toBe(false);
    });

    it('shouldKeepRequiringAnIssuerWhileTokenExchangeIsOn', () => {
      build(trustedIssuer, false);
      component.model.issuer = '';
      component.onIssuerChange();

      expect(component.crossAppAccessOnly).toBe(false);
      expect(component.getValidationErrors()).toContain('Issuer URL is required.');
    });

    it('shouldDropTheBlockWhenTheTrustedIssuerUsageIsUnchecked', () => {
      build({ ...trustedIssuer, spiffeTrustDomain: 'acme.org', crossAppAccess: { enabled: true } }, false);
      component.issuerEnabled = false;
      component.onUsageToggle();

      component.submit();

      expect(saved[0].crossAppAccess).toBeUndefined();
      expect(saved[0].issuer).toBeUndefined();
    });

    it('shouldClearTheBlockWhenTheUsageGoesOff', () => {
      build(
        {
          ...trustedIssuer,
          crossAppAccess: {
            enabled: true,
            audience: 'https://auth.acme.com',
            resourceServers: [{ id: 'rs-1', name: 'Acme Calendar', resource: 'https://calendar.acme.com' }],
          },
        },
        false,
      );
      component.crossAppAccessEnabled = false;
      component.onUsageToggle();

      component.submit();

      expect(saved[0].crossAppAccess).toBeUndefined();
    });

    it('shouldOmitTheBlockEntirelyWhenNothingWasEverConfigured', () => {
      build(trustedIssuer, false);
      component.submit();

      expect(saved[0].crossAppAccess).toBeUndefined();
    });

    it('shouldBeReadOnlyWithoutTheTrustedDomainUpdatePermission', () => {
      build({ ...trustedIssuer, crossAppAccess: { enabled: true } }, false, false);

      expect((testId('resourceServerNameInput') as HTMLInputElement).disabled).toBe(true);
      expect((testId('resourceServerResourceInput') as HTMLInputElement).disabled).toBe(true);
      expect((testId('audSubMappingInput') as HTMLInputElement).disabled).toBe(true);
      expect((testId('outboundExternalScopeInput') as HTMLInputElement).disabled).toBe(true);
      expect((testId('addResourceServerButton') as HTMLButtonElement).disabled).toBe(true);
      expect((testId('addOutboundScopeMappingButton') as HTMLButtonElement).disabled).toBe(true);
      expect(component.outboundDomainScopeCtrl.disabled).toBe(true);
      expect(testId('saveButton')).toBeFalsy();
    });
  });
});
