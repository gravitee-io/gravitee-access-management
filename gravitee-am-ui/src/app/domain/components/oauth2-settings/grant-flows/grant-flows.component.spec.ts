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
jest.mock('@gravitee/ui-components/src/lib/utils', () => ({
  deepClone: (x: unknown) => (x !== null && typeof x === 'object' ? JSON.parse(JSON.stringify(x)) : x),
}));

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatTooltipModule } from '@angular/material/tooltip';
import { of } from 'rxjs';

import { DomainStoreService } from '../../../../stores/domain.store';
import { TrustDomainService } from '../../../../services/trust-domain.service';
import { DEVICE_CODE_GRANT_TYPE } from '../../../settings/openid/device-flow/device-flow.types';

import { GrantFlowsComponent } from './grant-flows.component';

/** Token exchange grant type URI (component uses private constant). */
const TOKEN_EXCHANGE_GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:token-exchange';

describe('GrantFlowsComponent', () => {
  let component: GrantFlowsComponent;
  let fixture: ComponentFixture<GrantFlowsComponent>;
  let mockDomainStoreService: { current: Record<string, unknown> };

  function defaultDomainCurrent(): Record<string, unknown> {
    return {
      id: 'domain-id',
      tokenExchangeSettings: { enabled: true },
      oidc: { cibaSettings: { enabled: false } },
    };
  }

  function createFixtureWithMcpContext(oauthGrantTypes: string[]): void {
    fixture = TestBed.createComponent(GrantFlowsComponent);
    component = fixture.componentInstance;
    component.context = component.MCP_SERVER_CONTEXT;
    component.oauthSettings = { grantTypes: oauthGrantTypes };
    component.customGrantTypes = [];
    component.secretSettings = [];
    fixture.detectChanges();
  }

  beforeEach(async () => {
    mockDomainStoreService = { current: defaultDomainCurrent() };

    await TestBed.configureTestingModule({
      imports: [CommonModule, FormsModule, MatTooltipModule],
      declarations: [GrantFlowsComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
      providers: [
        { provide: DomainStoreService, useValue: mockDomainStoreService },
        { provide: TrustDomainService, useValue: { list: () => of([]) } },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(GrantFlowsComponent);
    component = fixture.componentInstance;
    component.oauthSettings = { grantTypes: [] };
    component.customGrantTypes = [];
    component.secretSettings = [];
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose MCP_SERVER_CONTEXT and CLIENT_CREDENTIALS_GRANT_TYPE constants', () => {
    expect(component.MCP_SERVER_CONTEXT).toBe('McpServer');
    expect(component.CLIENT_CREDENTIALS_GRANT_TYPE).toBe('client_credentials');
  });

  describe('filteredGrantTypes', () => {
    it('should return only client_credentials and token exchange when context is McpServer', () => {
      component.context = component.MCP_SERVER_CONTEXT;
      const filtered = component.filteredGrantTypes;
      expect(filtered).toHaveLength(2);
      expect(filtered.map((g) => g.value)).toContain(component.CLIENT_CREDENTIALS_GRANT_TYPE);
      expect(filtered.map((g) => g.value)).toContain(TOKEN_EXCHANGE_GRANT_TYPE);
    });

    it('should return all grant types when context is Application', () => {
      component.context = 'Application';
      const filtered = component.filteredGrantTypes;
      expect(filtered).toHaveLength(component.grantTypes.length);
    });
  });

  describe('modelChanged (MCP Server context)', () => {
    it('should emit grantTypes including client_credentials and token exchange when both selected', () => {
      component.context = component.MCP_SERVER_CONTEXT;
      const clientCreds = component.grantTypes.find((g) => g.value === component.CLIENT_CREDENTIALS_GRANT_TYPE);
      const tokenExchange = component.grantTypes.find((g) => g.value === TOKEN_EXCHANGE_GRANT_TYPE);
      clientCreds!.checked = true;
      tokenExchange!.checked = true;

      const emitSpy = jest.spyOn(component.settingsChange, 'emit');
      component.modelChanged();

      expect(emitSpy).toHaveBeenCalledTimes(1);
      const emitted = emitSpy.mock.calls[0][0];
      expect(emitted.grantTypes).toContain(component.CLIENT_CREDENTIALS_GRANT_TYPE);
      expect(emitted.grantTypes).toContain(TOKEN_EXCHANGE_GRANT_TYPE);
    });

    it('should not include other grant types in emitted grantTypes for McpServer context', () => {
      component.context = component.MCP_SERVER_CONTEXT;
      const authCode = component.grantTypes.find((g) => g.value === 'authorization_code');
      authCode!.checked = true;

      const emitSpy = jest.spyOn(component.settingsChange, 'emit');
      component.modelChanged();

      const emitted = emitSpy.mock.calls[0][0];
      expect(emitted.grantTypes).not.toContain('authorization_code');
    });
  });

  describe('selectGrantType', () => {
    it('should update grant type checked state and emit settingsChange', () => {
      const emitSpy = jest.spyOn(component.settingsChange, 'emit');
      const event = {
        source: { value: component.CLIENT_CREDENTIALS_GRANT_TYPE },
        checked: true,
      };

      component.selectGrantType(event);

      const clientCreds = component.grantTypes.find((g) => g.value === component.CLIENT_CREDENTIALS_GRANT_TYPE);
      expect(clientCreds?.checked).toBe(true);
      expect(emitSpy).toHaveBeenCalled();
    });
  });

  describe('initGrantTypes (via ngOnInit)', () => {
    it('should check token exchange when context is McpServer and grantTypes include token exchange', () => {
      createFixtureWithMcpContext([component.CLIENT_CREDENTIALS_GRANT_TYPE, TOKEN_EXCHANGE_GRANT_TYPE]);

      const tokenExchange = component.grantTypes.find((g) => g.value === TOKEN_EXCHANGE_GRANT_TYPE);
      expect(tokenExchange?.checked).toBe(true);
    });

    it('should disable token exchange when domain has token exchange disabled', () => {
      mockDomainStoreService.current = {
        ...defaultDomainCurrent(),
        tokenExchangeSettings: { enabled: false },
      };
      createFixtureWithMcpContext([]);

      const tokenExchange = component.grantTypes.find((g) => g.value === TOKEN_EXCHANGE_GRANT_TYPE);
      expect(tokenExchange?.disabled).toBe(true);
    });
  });

  describe('formChanged', () => {
    it('should emit formChanged when modelChanged is called', () => {
      const formChangedSpy = jest.spyOn(component.formChanged, 'emit');
      component.modelChanged();
      expect(formChangedSpy).toHaveBeenCalledWith(true);
    });
  });

  describe('Token Exchange scope handling', () => {
    it('isTokenExchangeFlowSelected returns false when token exchange is not checked', () => {
      expect(component.isTokenExchangeFlowSelected()).toBe(false);
    });

    it('isTokenExchangeFlowSelected returns true when token exchange grant is checked', () => {
      const te = component.grantTypes.find((g) => g.value === TOKEN_EXCHANGE_GRANT_TYPE);
      te!.checked = true;
      expect(component.isTokenExchangeFlowSelected()).toBe(true);
    });

    it('tokenExchangeScopeHandlingChanged updates nested scopeHandling and emits', () => {
      const emitSpy = jest.spyOn(component.settingsChange, 'emit');
      component.tokenExchangeScopeHandlingChanged('permissive');
      expect(component.oauthSettings.tokenExchangeOAuthSettings.scopeHandling).toBe('permissive');
      expect(emitSpy).toHaveBeenCalled();
      const emitted = emitSpy.mock.calls[0][0];
      expect(emitted.tokenExchangeOAuthSettings.scopeHandling).toBe('permissive');
    });

    it('isTokenExchangeInherited returns true by default', () => {
      expect(component.isTokenExchangeInherited()).toBe(true);
    });

    it('enableTokenExchangeInherit sets inherited=false and emits when toggled off', () => {
      const emitSpy = jest.spyOn(component.settingsChange, 'emit');
      component.enableTokenExchangeInherit({ checked: false });
      expect(component.oauthSettings.tokenExchangeOAuthSettings.inherited).toBe(false);
      expect(component.isTokenExchangeInherited()).toBe(false);
      expect(emitSpy).toHaveBeenCalled();
    });

    it('enableTokenExchangeInherit sets inherited=true and emits when toggled on', () => {
      component.oauthSettings.tokenExchangeOAuthSettings = { inherited: false, scopeHandling: 'downscoping' };
      const emitSpy = jest.spyOn(component.settingsChange, 'emit');
      component.enableTokenExchangeInherit({ checked: true });
      expect(component.oauthSettings.tokenExchangeOAuthSettings.inherited).toBe(true);
      expect(component.isTokenExchangeInherited()).toBe(true);
      expect(emitSpy).toHaveBeenCalled();
    });

    it('enableTokenExchangeInherit seeds the domain claim mappings when toggled off', () => {
      mockDomainStoreService.current = {
        ...defaultDomainCurrent(),
        tokenExchangeSettings: {
          enabled: true,
          tokenExchangeOAuthSettings: {
            scopeHandling: 'permissive',
            claimMappings: [{ source: 'subject_token', sourceClaim: 'tenant', tokenClaim: 'business_id' }],
          },
        },
      };

      component.enableTokenExchangeInherit({ checked: false });

      expect(component.oauthSettings.tokenExchangeOAuthSettings.claimMappings).toEqual([
        { source: 'subject_token', sourceClaim: 'tenant', tokenClaim: 'business_id' },
      ]);
      expect(component.oauthSettings.tokenExchangeOAuthSettings.scopeHandling).toBe('permissive');
    });

    it('enableTokenExchangeInherit keeps existing mappings on a later toggle', () => {
      component.oauthSettings.tokenExchangeOAuthSettings = {
        inherited: false,
        scopeHandling: 'downscoping',
        claimMappings: [{ source: 'actor_token', sourceClaim: 'agent_id', tokenClaim: 'agent' }],
      };
      mockDomainStoreService.current = {
        ...defaultDomainCurrent(),
        tokenExchangeSettings: {
          enabled: true,
          tokenExchangeOAuthSettings: { claimMappings: [{ source: 'subject_token', sourceClaim: 'tenant', tokenClaim: 'business_id' }] },
        },
      };

      component.enableTokenExchangeInherit({ checked: false });

      expect(component.oauthSettings.tokenExchangeOAuthSettings.claimMappings).toEqual([
        { source: 'actor_token', sourceClaim: 'agent_id', tokenClaim: 'agent' },
      ]);
    });

    it('addClaimMapping appends the trimmed mapping and resets the form', () => {
      component.newClaimMapping = { source: 'subject_token', sourceClaim: '  tenant  ', tokenClaim: '  business_id  ' };

      component.addClaimMapping();

      expect(component.tokenExchangeClaimMappings).toEqual([{ source: 'subject_token', sourceClaim: 'tenant', tokenClaim: 'business_id' }]);
      expect(component.newClaimMapping).toEqual({ source: 'subject_token', sourceClaim: '', tokenClaim: '' });
    });

    it('removeClaimMapping drops the mapping at the given index', () => {
      component.oauthSettings.tokenExchangeOAuthSettings = {
        inherited: false,
        scopeHandling: 'downscoping',
        claimMappings: [
          { source: 'subject_token', sourceClaim: 'a', tokenClaim: 'one' },
          { source: 'subject_token', sourceClaim: 'b', tokenClaim: 'two' },
        ],
      };

      component.removeClaimMapping(0);

      expect(component.tokenExchangeClaimMappings).toEqual([{ source: 'subject_token', sourceClaim: 'b', tokenClaim: 'two' }]);
    });

    it('isNewClaimMappingValid rejects a reserved target claim', () => {
      component.newClaimMapping = { source: 'subject_token', sourceClaim: 'tenant', tokenClaim: 'sub' };

      expect(component.isNewClaimMappingValid()).toBe(false);
      expect(component.newClaimMappingError()).toBe('"sub" is reserved by Access Management and cannot be a target claim.');
    });

    it('isNewClaimMappingValid rejects a target claim that is already mapped', () => {
      component.oauthSettings.tokenExchangeOAuthSettings = {
        inherited: false,
        scopeHandling: 'downscoping',
        claimMappings: [{ source: 'subject_token', sourceClaim: 'tenant', tokenClaim: 'business_id' }],
      };
      component.newClaimMapping = { source: 'actor_token', sourceClaim: 'agent_id', tokenClaim: 'business_id' };

      expect(component.isNewClaimMappingValid()).toBe(false);
      expect(component.newClaimMappingError()).toBe('"business_id" is already mapped.');
    });

    it('isNewClaimMappingValid accepts an unused, unreserved target claim', () => {
      component.newClaimMapping = { source: 'subject_token', sourceClaim: 'tenant', tokenClaim: 'business_id' };

      expect(component.isNewClaimMappingValid()).toBe(true);
      expect(component.newClaimMappingError()).toBeNull();
    });

    it('ngOnInit initialises tokenExchangeOAuthSettings when absent', () => {
      component.oauthSettings = { grantTypes: [] };
      component.ngOnInit();
      expect(component.oauthSettings.tokenExchangeOAuthSettings).toBeDefined();
      expect(component.oauthSettings.tokenExchangeOAuthSettings.inherited).toBe(true);
      expect(component.oauthSettings.tokenExchangeOAuthSettings.scopeHandling).toBe('downscoping');
    });
  });

  describe('isAgentPrefixCapable', () => {
    it('returns true for AGENT application with HOSTED_DELEGATED kind', () => {
      component.applicationType = 'AGENT';
      component.applicationKind = 'HOSTED_DELEGATED';
      expect(component.isAgentPrefixCapable()).toBe(true);
    });

    it('returns true for AGENT application with AUTONOMOUS kind', () => {
      component.applicationType = 'AGENT';
      component.applicationKind = 'AUTONOMOUS';
      expect(component.isAgentPrefixCapable()).toBe(true);
    });

    it('returns false for AGENT application with USER_EMBEDDED kind', () => {
      component.applicationType = 'AGENT';
      component.applicationKind = 'USER_EMBEDDED';
      expect(component.isAgentPrefixCapable()).toBe(false);
    });

    it('returns false for AGENT application without a kind', () => {
      component.applicationType = 'AGENT';
      component.applicationKind = null;
      expect(component.isAgentPrefixCapable()).toBe(false);
    });

    it('returns false for non-AGENT applications even with an agent kind', () => {
      component.applicationType = 'SERVICE';
      component.applicationKind = 'HOSTED_DELEGATED';
      expect(component.isAgentPrefixCapable()).toBe(false);
    });

    it('matches applicationType case-insensitively', () => {
      component.applicationType = 'agent';
      component.applicationKind = 'HOSTED_DELEGATED';
      expect(component.isAgentPrefixCapable()).toBe(true);
    });
  });

  describe('spiffeChanged', () => {
    it('emits a copy of the current spiffeSettings (including subjectMatchMode)', () => {
      const emitted: unknown[] = [];
      component.spiffeSettingsChange.subscribe((s: unknown) => emitted.push(s));
      component.spiffeSettings = {
        trustDomain: 'acme',
        subject: 'spiffe://acme/hotel-agent',
        subjectMatchMode: 'prefix',
      };

      component.spiffeChanged();

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toEqual({
        trustDomain: 'acme',
        subject: 'spiffe://acme/hotel-agent',
        subjectMatchMode: 'prefix',
      });
      // emitted object should be a shallow copy, not the same reference
      expect(emitted[0]).not.toBe(component.spiffeSettings);
    });
  });

  describe('Device flow grant type', () => {
    function createFixtureWithDomain(domainCurrent: Record<string, unknown>, oauthSettings: Record<string, unknown> = {}): void {
      mockDomainStoreService.current = domainCurrent;
      fixture = TestBed.createComponent(GrantFlowsComponent);
      component = fixture.componentInstance;
      component.oauthSettings = { grantTypes: [], ...oauthSettings };
      component.customGrantTypes = [];
      component.secretSettings = [];
      fixture.detectChanges();
    }

    function deviceCodeGrantType() {
      return component.grantTypes.find((g) => g.value === DEVICE_CODE_GRANT_TYPE);
    }

    it('should offer the device code grant type', () => {
      expect(deviceCodeGrantType()).toBeDefined();
    });

    it('should disable it when the domain has no device flow settings', () => {
      createFixtureWithDomain({ ...defaultDomainCurrent(), oidc: {} });
      expect(deviceCodeGrantType()?.disabled).toBe(true);
    });

    it('should disable it when the domain has device flow off', () => {
      createFixtureWithDomain({ ...defaultDomainCurrent(), oidc: { deviceFlowSettings: { enabled: false } } });
      expect(deviceCodeGrantType()?.disabled).toBe(true);
    });

    it('should make it selectable once the domain enables device flow', () => {
      createFixtureWithDomain({ ...defaultDomainCurrent(), oidc: { deviceFlowSettings: { enabled: true } } });
      expect(deviceCodeGrantType()?.disabled).toBe(false);
    });

    it('should explain why it is disabled', () => {
      createFixtureWithDomain({ ...defaultDomainCurrent(), oidc: { deviceFlowSettings: { enabled: false } } });
      expect(component.grantTypeDisabledReason(deviceCodeGrantType())).toContain('Device Flow');
    });

    it('should give no reason for a selectable grant type', () => {
      createFixtureWithDomain({ ...defaultDomainCurrent(), oidc: { deviceFlowSettings: { enabled: true } } });
      expect(component.grantTypeDisabledReason(deviceCodeGrantType())).toBeNull();
    });

    it('should check it when the application already carries it', () => {
      createFixtureWithDomain(
        { ...defaultDomainCurrent(), oidc: { deviceFlowSettings: { enabled: true } } },
        {
          grantTypes: [DEVICE_CODE_GRANT_TYPE],
        },
      );
      expect(deviceCodeGrantType()?.checked).toBe(true);
    });

    it('should emit the device code grant type once selected', () => {
      createFixtureWithDomain({ ...defaultDomainCurrent(), oidc: { deviceFlowSettings: { enabled: true } } });
      const emitSpy = jest.spyOn(component.settingsChange, 'emit');

      component.selectGrantType({ source: { value: DEVICE_CODE_GRANT_TYPE }, checked: true });

      expect(emitSpy.mock.calls[0][0].grantTypes).toContain(DEVICE_CODE_GRANT_TYPE);
    });

    it('should not show it as checked while disabled', () => {
      createFixtureWithDomain(
        { ...defaultDomainCurrent(), oidc: { deviceFlowSettings: { enabled: false } } },
        {
          grantTypes: [DEVICE_CODE_GRANT_TYPE],
        },
      );
      expect(component.isGrantTypeVisuallyChecked(deviceCodeGrantType())).toBe(false);
    });
  });

  describe('Device flow per-application override', () => {
    const DOMAIN_WITH_DEVICE_FLOW = {
      id: 'domain-id',
      tokenExchangeSettings: { enabled: true },
      oidc: { deviceFlowSettings: { enabled: true, deviceCodeExpiry: 900, pollingInterval: 10 } },
    };

    function createFixture(domainCurrent: Record<string, unknown>, oauthSettings: Record<string, unknown> = {}): void {
      mockDomainStoreService.current = domainCurrent;
      fixture = TestBed.createComponent(GrantFlowsComponent);
      component = fixture.componentInstance;
      component.oauthSettings = { grantTypes: [DEVICE_CODE_GRANT_TYPE], ...oauthSettings };
      component.customGrantTypes = [];
      component.secretSettings = [];
      fixture.detectChanges();
    }

    it('should be hidden when device flow is off on the domain', () => {
      createFixture({ ...defaultDomainCurrent(), oidc: { deviceFlowSettings: { enabled: false } } });
      expect(component.isDeviceFlowEnabledAtDomain()).toBe(false);
    });

    it('should be shown when device flow is on at the domain', () => {
      createFixture(DOMAIN_WITH_DEVICE_FLOW);
      expect(component.isDeviceFlowEnabledAtDomain()).toBe(true);
    });

    it('should report inheriting when the application has no override', () => {
      createFixture(DOMAIN_WITH_DEVICE_FLOW);
      expect(component.isDeviceFlowOverridden()).toBe(false);
    });

    it('should expose the inherited domain values while inheriting', () => {
      createFixture(DOMAIN_WITH_DEVICE_FLOW);
      expect(component.inheritedDeviceFlowSettings).toEqual({ deviceCodeExpiry: 900, pollingInterval: 10 });
    });

    it('should fall back to the standard timings when the domain stored none', () => {
      createFixture({ ...defaultDomainCurrent(), oidc: { deviceFlowSettings: { enabled: true } } });
      expect(component.inheritedDeviceFlowSettings).toEqual({ deviceCodeExpiry: 600, pollingInterval: 5 });
    });

    it('should report overriding when the application carries an override', () => {
      createFixture(DOMAIN_WITH_DEVICE_FLOW, { deviceFlowSettings: { deviceCodeExpiry: 120, pollingInterval: 2 } });
      expect(component.isDeviceFlowOverridden()).toBe(true);
    });

    it('should pre-populate the override with the effective domain values when ticked', () => {
      createFixture(DOMAIN_WITH_DEVICE_FLOW);
      const emitSpy = jest.spyOn(component.settingsChange, 'emit');

      component.enableDeviceFlowOverride({ checked: true });

      expect(component.oauthSettings.deviceFlowSettings).toEqual({ deviceCodeExpiry: 900, pollingInterval: 10 });
      expect(emitSpy.mock.calls[0][0].deviceFlowSettings).toEqual({ deviceCodeExpiry: 900, pollingInterval: 10 });
    });

    it('should stay untickable-off for an application that no longer carries the grant type', () => {
      createFixture(DOMAIN_WITH_DEVICE_FLOW, { grantTypes: [], deviceFlowSettings: { deviceCodeExpiry: 120, pollingInterval: 2 } });

      expect(component.isDeviceFlowSelected()).toBe(false);
      expect(component.isDeviceFlowOverridden()).toBe(true);

      component.enableDeviceFlowOverride({ checked: false });

      expect(component.oauthSettings.deviceFlowSettings).toBeNull();
    });

    it('should clear the override when unticked', () => {
      createFixture(DOMAIN_WITH_DEVICE_FLOW, { deviceFlowSettings: { deviceCodeExpiry: 120, pollingInterval: 2 } });
      const emitSpy = jest.spyOn(component.settingsChange, 'emit');

      component.enableDeviceFlowOverride({ checked: false });

      expect(component.oauthSettings.deviceFlowSettings).toBeNull();
      expect(component.isDeviceFlowOverridden()).toBe(false);
      expect(emitSpy.mock.calls[0][0].deviceFlowSettings).toBeNull();
    });

    it('should emit the edited override timings', () => {
      createFixture(DOMAIN_WITH_DEVICE_FLOW, { deviceFlowSettings: { deviceCodeExpiry: 120, pollingInterval: 2 } });
      const emitSpy = jest.spyOn(component.settingsChange, 'emit');

      component.oauthSettings.deviceFlowSettings.deviceCodeExpiry = 300;
      component.modelChanged();

      expect(emitSpy.mock.calls[0][0].deviceFlowSettings.deviceCodeExpiry).toBe(300);
    });

    describe('validation', () => {
      it('should be valid while inheriting', () => {
        createFixture(DOMAIN_WITH_DEVICE_FLOW);
        expect(component.isDeviceFlowOverrideValid()).toBe(true);
      });

      it('should be valid for sane override timings', () => {
        createFixture(DOMAIN_WITH_DEVICE_FLOW, { deviceFlowSettings: { deviceCodeExpiry: 120, pollingInterval: 2 } });
        expect(component.isDeviceFlowOverrideValid()).toBe(true);
      });

      it('should be invalid for a zero expiry', () => {
        createFixture(DOMAIN_WITH_DEVICE_FLOW, { deviceFlowSettings: { deviceCodeExpiry: 0, pollingInterval: 2 } });
        expect(component.isDeviceFlowOverrideValid()).toBe(false);
      });

      it('should be invalid when polling is slower than the code lives', () => {
        createFixture(DOMAIN_WITH_DEVICE_FLOW, { deviceFlowSettings: { deviceCodeExpiry: 30, pollingInterval: 60 } });
        expect(component.isDeviceFlowOverrideValid()).toBe(false);
      });

      it('should be invalid for a fractional interval', () => {
        createFixture(DOMAIN_WITH_DEVICE_FLOW, { deviceFlowSettings: { deviceCodeExpiry: 120, pollingInterval: 1.5 } });
        expect(component.isDeviceFlowOverrideValid()).toBe(false);
      });
    });
  });
});
