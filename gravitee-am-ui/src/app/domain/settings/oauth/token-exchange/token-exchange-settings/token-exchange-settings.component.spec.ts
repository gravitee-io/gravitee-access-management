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
import { of } from 'rxjs';

import { AuthService } from '../../../../../services/auth.service';
import { DomainService } from '../../../../../services/domain.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { DomainStoreService } from '../../../../../stores/domain.store';

jest.mock('@gravitee/ui-components/src/lib/utils', () => ({
  deepClone: (value: unknown) => JSON.parse(JSON.stringify(value)),
}));

import { TokenExchangeSettingsComponent } from './token-exchange-settings.component';

describe('TokenExchangeSettingsComponent', () => {
  let component: TokenExchangeSettingsComponent;
  let fixture: ComponentFixture<TokenExchangeSettingsComponent>;
  let domainServiceStub: DomainService;

  const domain = {
    id: 'domain-1',
    tokenExchangeSettings: {
      enabled: true,
      trustedIssuers: [{ issuer: 'https://issuer.example.com', keyResolutionMethod: 'pem', certificate: 'cert' }],
    },
  };

  beforeEach(waitForAsync(() => {
    domainServiceStub = {
      patchTokenExchangeSettings: jest.fn().mockReturnValue(of(domain)),
    } as Partial<DomainService> as DomainService;

    TestBed.configureTestingModule({
      declarations: [TokenExchangeSettingsComponent],
      providers: [
        { provide: DomainService, useValue: domainServiceStub },
        { provide: SnackbarService, useValue: { open: jest.fn() } },
        { provide: AuthService, useValue: { hasPermissions: () => true } },
        { provide: DomainStoreService, useValue: { domain$: of(domain), set: jest.fn() } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(TokenExchangeSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shouldNotWriteTrustedIssuersWhenSavingSettings', () => {
    component.save();

    const [, payload] = (domainServiceStub.patchTokenExchangeSettings as jest.Mock).mock.calls[0];
    expect(payload.tokenExchangeSettings).not.toHaveProperty('trustedIssuers');
  });
});
