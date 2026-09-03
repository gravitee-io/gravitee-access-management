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
import { CommonModule } from '@angular/common';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { OAUTH2_SETTINGS_SERVICE, OAuth2Context } from '../../../../services/oauth2-settings.service';
import { AuthService } from '../../../../services/auth.service';
import { ScopeService } from '../../../../services/scope.service';
import { SnackbarService } from '../../../../services/snackbar.service';

import { OAuth2SettingsComponent } from './oauth2-settings.component';

describe('OAuth2SettingsComponent', () => {
  let component: OAuth2SettingsComponent;
  let fixture: ComponentFixture<OAuth2SettingsComponent>;
  let update: jest.Mock;
  let snackbar: { open: jest.Mock };

  async function createComponent(context: OAuth2Context, oauthSettings: Record<string, unknown>): Promise<void> {
    update = jest.fn().mockReturnValue(of({}));
    snackbar = { open: jest.fn() };
    const application = { id: 'app-id', type: 'web', settings: { oauth: oauthSettings } };

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [CommonModule],
      declarations: [OAuth2SettingsComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { data: { domain: { id: 'domain-id' }, application } } } },
        { provide: Router, useValue: { navigate: jest.fn() } },
        { provide: SnackbarService, useValue: snackbar },
        { provide: AuthService, useValue: { hasPermissions: () => true } },
        { provide: ScopeService, useValue: {} },
        {
          provide: OAUTH2_SETTINGS_SERVICE,
          useValue: {
            getPermission: () => 'application_openid_update',
            getContext: () => context,
            getSettings: () => ({
              domainId: 'domain-id',
              resourceId: 'app-id',
              resource: application,
              settings: application.settings.oauth,
            }),
            update,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OAuth2SettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function savedOAuthSettings(): any {
    return update.mock.calls[0][3];
  }

  it('should send the device flow override an application carries', async () => {
    await createComponent('Application', { grantTypes: [], deviceFlowSettings: { deviceCodeExpiry: 120, pollingInterval: 2 } });

    component.save();

    expect(savedOAuthSettings().deviceFlowSettings).toEqual({ deviceCodeExpiry: 120, pollingInterval: 2 });
  });

  it('should keep the override the grant flows form emitted', async () => {
    await createComponent('Application', { grantTypes: [] });

    component.updateSettings({ grantTypes: [], deviceFlowSettings: { deviceCodeExpiry: 300, pollingInterval: 3 } });
    component.save();

    expect(savedOAuthSettings().deviceFlowSettings).toEqual({ deviceCodeExpiry: 300, pollingInterval: 3 });
  });

  it('should send an explicit null to clear an override, so the application goes back to inheriting', async () => {
    await createComponent('Application', { grantTypes: [], deviceFlowSettings: { deviceCodeExpiry: 120, pollingInterval: 2 } });

    component.updateSettings({ grantTypes: [], deviceFlowSettings: null });
    component.save();

    expect(savedOAuthSettings().deviceFlowSettings).toBeNull();
  });

  it('should leave an existing override alone when a sibling tab emits no device flow settings', async () => {
    await createComponent('Application', { grantTypes: [], deviceFlowSettings: { deviceCodeExpiry: 120, pollingInterval: 2 } });

    component.updateSettings({ grantTypes: [], scopeSettings: [] });
    component.save();

    expect(savedOAuthSettings().deviceFlowSettings).toEqual({ deviceCodeExpiry: 120, pollingInterval: 2 });
  });

  it('should not send device flow settings for a protected resource', async () => {
    await createComponent('McpServer', { grantTypes: [] });

    component.save();

    expect(savedOAuthSettings()).not.toHaveProperty('deviceFlowSettings');
  });

  it('should refuse to save an override whose polling interval outlives the device code', async () => {
    await createComponent('Application', { grantTypes: [], deviceFlowSettings: { deviceCodeExpiry: 30, pollingInterval: 60 } });

    component.save();

    expect(update).not.toHaveBeenCalled();
    expect(snackbar.open).toHaveBeenCalledWith(expect.stringContaining('polling interval'));
  });

  it('should refuse to save an override with a zero device code expiry', async () => {
    await createComponent('Application', { grantTypes: [], deviceFlowSettings: { deviceCodeExpiry: 0, pollingInterval: 5 } });

    component.save();

    expect(update).not.toHaveBeenCalled();
  });
});
