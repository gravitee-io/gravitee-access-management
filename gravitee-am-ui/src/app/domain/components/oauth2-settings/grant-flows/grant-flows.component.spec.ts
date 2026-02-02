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
  deepClone: (x: unknown) => (x && typeof x === 'object' ? { ...x } : x),
}));

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommonModule } from '@angular/common';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';

import { DomainStoreService } from '../../../../stores/domain.store';

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
      imports: [CommonModule],
      declarations: [GrantFlowsComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
      providers: [{ provide: DomainStoreService, useValue: mockDomainStoreService }],
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
});
