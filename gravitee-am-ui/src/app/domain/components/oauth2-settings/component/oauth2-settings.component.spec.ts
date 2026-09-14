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
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { ScopeService } from '../../../../services/scope.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { AuthService } from '../../../../services/auth.service';
import { OAUTH2_SETTINGS_SERVICE } from '../../../../services/oauth2-settings.service';

import { OAuth2SettingsComponent } from './oauth2-settings.component';

describe('OAuth2SettingsComponent', () => {
  let component: OAuth2SettingsComponent;
  let fixture: ComponentFixture<OAuth2SettingsComponent>;
  let update: jest.Mock;
  let settings: any;

  function savedOAuthSettings(): any {
    return update.mock.calls[0][3];
  }

  beforeEach(async () => {
    settings = { grantTypes: ['authorization_code'] };
    update = jest.fn().mockReturnValue(of({}));

    await TestBed.configureTestingModule({
      imports: [CommonModule],
      declarations: [OAuth2SettingsComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { data: {} } } },
        { provide: Router, useValue: { navigate: jest.fn() } },
        { provide: SnackbarService, useValue: { open: jest.fn() } },
        { provide: AuthService, useValue: { hasPermissions: () => true } },
        { provide: ScopeService, useValue: {} },
        {
          provide: OAUTH2_SETTINGS_SERVICE,
          useValue: {
            getPermission: () => 'application_openid_update',
            getContext: () => 'Application',
            getSettings: () => ({ domainId: 'domain-1', resourceId: 'app-1', resource: { type: 'WEB' }, settings }),
            update,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OAuth2SettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shouldCarryCrossAppAccessSettingsFromChildIntoTheSavedPayload', () => {
    const crossAppAccessSettings = {
      enabled: true,
      resourceServers: [{ trustDomainId: 'td-1', resourceServerId: 'rs-1', clientId: 'calendar-client' }],
    };

    component.updateSettings({ ...component.oauthSettings, crossAppAccessSettings, idJagValiditySeconds: 120 });
    component.save();

    expect(savedOAuthSettings().crossAppAccessSettings).toEqual(crossAppAccessSettings);
    expect(savedOAuthSettings().idJagValiditySeconds).toBe(120);
  });

  it('shouldCarryCrossAppAccessSettingsMutatedInPlaceIntoTheSavedPayload', () => {
    component.oauthSettings.crossAppAccessSettings = { enabled: true, resourceServers: [] };
    component.oauthSettings.idJagValiditySeconds = 300;

    component.save();

    expect(savedOAuthSettings().crossAppAccessSettings).toEqual({ enabled: true, resourceServers: [] });
    expect(savedOAuthSettings().idJagValiditySeconds).toBe(300);
  });

  it('shouldNotInventCrossAppAccessSettingsWhenTheApplicationHasNone', () => {
    component.save();

    expect(savedOAuthSettings().crossAppAccessSettings).toBeUndefined();
  });
});
