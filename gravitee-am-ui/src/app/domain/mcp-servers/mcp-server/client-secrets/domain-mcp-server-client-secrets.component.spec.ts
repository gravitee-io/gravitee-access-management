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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';

import { SnackbarService } from '../../../../services/snackbar.service';
import { McpServersService } from '../../mcp-servers.service';
import { AuthService } from '../../../../services/auth.service';
import { DomainStoreService } from '../../../../stores/domain.store';
import { McpServerClientSecretService } from '../../../../services/client-secret.service';

import { DomainMcpServerClientSecretsComponent } from './domain-mcp-server-client-secrets.component';

describe('DomainMcpServerClientSecretsComponent', () => {
  let component: DomainMcpServerClientSecretsComponent;
  let fixture: ComponentFixture<DomainMcpServerClientSecretsComponent>;

  const mockSnackBarService = {
    open: jest.fn(),
  };

  const mockMcpServersService = {
    patch: jest.fn(),
  };

  const mockAuthService = {
    hasPermissions: jest.fn().mockReturnValue(true),
  };

  const mockDomainStoreService = {
    current: { id: 'domain-id' },
  };

  const mockMcpServerClientSecretService = {};

  const mockMatDialog = {
    open: jest.fn(),
  };

  const mockProtectedResource = {
    id: 'resource-id',
    secretSettings: [],
  };

  const mockActivatedRoute = {
    snapshot: {
      data: {
        mcpServer: mockProtectedResource,
      },
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [DomainMcpServerClientSecretsComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
      providers: [
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
        { provide: SnackbarService, useValue: mockSnackBarService },
        { provide: McpServersService, useValue: mockMcpServersService },
        { provide: AuthService, useValue: mockAuthService },
        { provide: DomainStoreService, useValue: mockDomainStoreService },
        { provide: McpServerClientSecretService, useValue: mockMcpServerClientSecretService },
        { provide: MatDialog, useValue: mockMatDialog },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(DomainMcpServerClientSecretsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should patch protected resource with secretSettings wrapped in an array', () => {
    const dialogRefSpyObj = {
      afterClosed: jest.fn().mockReturnValue(of({ some: 'settings' })),
    };
    mockMatDialog.open.mockReturnValue(dialogRefSpyObj as any);
    mockMcpServersService.patch.mockReturnValue(of({ secretSettings: [{ some: 'settings' }] }));

    const event = new MouseEvent('click');
    jest.spyOn(event, 'preventDefault');

    component.openSettings(event);

    expect(mockMcpServersService.patch).toHaveBeenCalledWith('domain-id', 'resource-id', { secretSettings: [{ some: 'settings' }] });
    expect(mockSnackBarService.open).toHaveBeenCalledWith('Secret settings updated');
  });
});
