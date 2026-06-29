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
jest.mock('@gravitee/ui-components/wc/gv-design', () => ({}));

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';

import { OrganizationService } from '../../../../../services/organization.service';
import { ApplicationService } from '../../../../../services/application.service';
import { ProtectedResourceService } from '../../../../../services/protected-resource.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { DialogService } from '../../../../../services/dialog.service';

import { ApplicationFlowsComponent } from './flows.component';

describe('ApplicationFlowsComponent (MCP server / token-only context)', () => {
  let component: ApplicationFlowsComponent;
  let fixture: ComponentFixture<ApplicationFlowsComponent>;

  const allFlows = [
    { type: 'root', name: 'ALL', pre: [], post: [] },
    { type: 'login', name: 'LOGIN', pre: [], post: [] },
    { type: 'token', name: 'TOKEN', pre: [], post: [] },
  ];

  const flowSchema = JSON.stringify({
    properties: {
      type: {
        enum: ['root', 'login', 'token'],
        default: 'root',
        'x-schema-form': {
          titleMap: { root: 'ALL', login: 'LOGIN', token: 'TOKEN' },
        },
      },
    },
  });

  const mockApplicationService = { updateFlows: jest.fn(), patch: jest.fn() };
  const mockProtectedResourceService = { updateFlows: jest.fn(), patch: jest.fn() };
  const mockOrganizationService = {};
  const mockSnackbarService = { open: jest.fn() };
  const mockDialogService = { confirm: jest.fn() };

  const mockActivatedRoute = {
    snapshot: {
      data: {
        domain: { id: 'domain-id' },
        mcpServer: { id: 'mcp-1', name: 'My MCP' },
        flows: allFlows,
        flowSettingsForm: flowSchema,
        policies: [],
        factors: [],
        flowsContext: 'mcp',
      },
      parent: { data: {}, parent: { data: {} } },
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ApplicationFlowsComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
      providers: [
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
        { provide: OrganizationService, useValue: mockOrganizationService },
        { provide: ApplicationService, useValue: mockApplicationService },
        { provide: ProtectedResourceService, useValue: mockProtectedResourceService },
        { provide: SnackbarService, useValue: mockSnackbarService },
        { provide: DialogService, useValue: mockDialogService },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    jest.clearAllMocks();
    fixture = TestBed.createComponent(ApplicationFlowsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should expose only the token flow', () => {
    expect(component.definition.flows.map((f) => f.type)).toEqual(['token']);
  });

  it('should restrict the flow schema to the token type', () => {
    const parsed = JSON.parse(component.flowSchema);
    expect(parsed.properties.type.enum).toEqual(['token']);
    expect(parsed.properties.type.default).toEqual('token');
    expect(parsed.properties.type['x-schema-form'].titleMap).toEqual({ token: 'TOKEN' });
  });

  it('should report the MCP context', () => {
    expect(component.isMcpContext()).toBe(true);
  });

  it('should default inherit configuration to enabled for an MCP server without persisted advanced settings', () => {
    expect(component.isInherited()).toBe(true);
  });

  it('should persist inherit changes through the protected resource service with full settings', () => {
    mockDialogService.confirm.mockReturnValue(of(true));
    mockProtectedResourceService.patch.mockReturnValue(of({ id: 'mcp-1', settings: { advanced: { flowsInherited: false } } }));

    component.enableInheritMode({ checked: false, source: { checked: false } });

    expect(mockProtectedResourceService.patch).toHaveBeenCalledWith('domain-id', 'mcp-1', {
      settings: { advanced: { flowsInherited: false } },
    });
    expect(mockApplicationService.patch).not.toHaveBeenCalled();
  });

  it('should persist flows through the protected resource service', async () => {
    const updated = [{ type: 'token', name: 'TOKEN', pre: [], post: [] }];
    mockProtectedResourceService.updateFlows.mockReturnValue(of(updated));
    (component as any).gvDesignComponent = { nativeElement: { validate: jest.fn().mockResolvedValue(undefined), saved: jest.fn() } };

    await component.onSubmit();

    expect(mockProtectedResourceService.updateFlows).toHaveBeenCalledWith('domain-id', 'mcp-1', expect.any(Array));
    expect(mockApplicationService.updateFlows).not.toHaveBeenCalled();
    expect(mockSnackbarService.open).toHaveBeenCalledWith('Flows updated');
  });
});
