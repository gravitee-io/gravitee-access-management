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
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconTestingModule } from '@angular/material/icon/testing';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { of } from 'rxjs';

import { OrganizationService } from '../../../../services/organization.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { ExtensionGrantService } from '../../../../services/extension-grant.service';
import { DialogService } from '../../../../services/dialog.service';
import { AuthService } from '../../../../services/auth.service';
import { PluginFeatureService } from '../../../../services/plugin-feature.service';

import { ExtensionGrantComponent } from './extension-grant.component';

const JWT_BEARER_GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:jwt-bearer';

describe('ExtensionGrantComponent', () => {
  let fixture: ComponentFixture<ExtensionGrantComponent>;
  let element: HTMLElement;

  async function render(type: string): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [
        FormsModule,
        NoopAnimationsModule,
        MatDividerModule,
        MatFormFieldModule,
        MatIconTestingModule,
        MatInputModule,
        MatSelectModule,
        MatSlideToggleModule,
      ],
      declarations: [ExtensionGrantComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              data: {
                domain: { id: 'domain-id' },
                extensionGrant: {
                  id: 'grant-id',
                  name: 'a grant',
                  type,
                  grantType: JWT_BEARER_GRANT_TYPE,
                  userExists: false,
                  configuration: '{}',
                },
                identityProviders: [{ id: 'idp-id', name: 'Enterprise IdP' }],
              },
            },
          },
        },
        { provide: Router, useValue: {} },
        {
          provide: OrganizationService,
          useValue: { extensionGrantSchema: jest.fn().mockReturnValue(of({ id: 'schema', properties: {} })) },
        },
        { provide: ExtensionGrantService, useValue: {} },
        { provide: SnackbarService, useValue: {} },
        { provide: DialogService, useValue: {} },
        { provide: AuthService, useValue: { hasPermissions: jest.fn().mockReturnValue(true) } },
        {
          provide: PluginFeatureService,
          useValue: {
            getFeature$: jest.fn().mockReturnValue(of(undefined)),
            isMissingFeatureForType$: jest.fn().mockReturnValue(of(false)),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ExtensionGrantComponent);
    element = fixture.nativeElement;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function saveButton(): HTMLButtonElement {
    return Array.from(element.querySelectorAll('button')).find((button) => button.textContent.includes('SAVE'));
  }

  describe('for a Cross App Access grant', () => {
    beforeEach(async () => render('cross-app-access-am-extension-grant'));

    it('shouldNotOfferAnIdentityProviderSelect', () => {
      expect(element.querySelector('mat-select[name="identityProvider"]')).toBeNull();
    });

    it('shouldExplainThatTheIdentityProviderIsResolvedFromTheAssertionIssuer', () => {
      const hint = element.querySelector('[data-testid="identityProviderResolutionHint"]');

      expect(hint).not.toBeNull();
      expect(hint.classList).toContain('gv-form-hint');
      expect(hint.textContent).toContain('issuer');
    });

    it('shouldKeepBothUserToggles', () => {
      const toggles = Array.from(element.querySelectorAll('mat-slide-toggle')).map((toggle) => toggle.textContent.trim());

      expect(toggles).toEqual(['Create user account', 'User existence']);
    });

    it('shouldEnableSaveAfterTheCheckUserToggleChanges', async () => {
      expect(saveButton().disabled).toBe(true);

      element.querySelectorAll<HTMLButtonElement>('mat-slide-toggle button')[1].click();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(fixture.componentInstance.extensionGrant.userExists).toBe(true);
      expect(saveButton().disabled).toBe(false);
    });
  });

  describe('for a jwt-bearer grant', () => {
    beforeEach(async () => render('jwtbearer-am-extension-grant'));

    it('shouldOfferTheIdentityProviderSelect', () => {
      expect(element.querySelector('mat-select[name="identityProvider"]')).not.toBeNull();
    });

    it('shouldNotShowTheIssuerResolutionHint', () => {
      expect(element.querySelector('[data-testid="identityProviderResolutionHint"]')).toBeNull();
    });

    it('shouldKeepBothUserToggles', () => {
      const toggles = Array.from(element.querySelectorAll('mat-slide-toggle')).map((toggle) => toggle.textContent.trim());

      expect(toggles).toEqual(['Create user account', 'User existence']);
    });
  });
});
