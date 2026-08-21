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
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { DialogService } from '../../../services/dialog.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { TrustDomainService } from '../../../services/trust-domain.service';

import { DomainSettingsTrustDomainsComponent } from './trust-domains.component';
import { TrustDomain } from './trust-domain.types';

describe('DomainSettingsTrustDomainsComponent', () => {
  let component: DomainSettingsTrustDomainsComponent;
  let fixture: ComponentFixture<DomainSettingsTrustDomainsComponent>;
  let trustDomainServiceStub: TrustDomainService;

  const spiffeEntry: TrustDomain = {
    id: 'td-spiffe',
    kind: 'SPIFFE',
    name: 'shared.example',
    keyMaterial: { source: 'JWKS_URL', jwksUrl: 'https://spire.example/keys' },
  };
  const tokenExchangeEntry: TrustDomain = {
    id: 'td-te',
    kind: 'TOKEN_EXCHANGE',
    name: 'shared.example',
    keyMaterial: { source: 'PEM', certificate: 'cert' },
    tokenExchange: { issuer: 'https://issuer.example.com' },
  };

  beforeEach(waitForAsync(() => {
    trustDomainServiceStub = {
      delete: jest.fn().mockReturnValue(of(void 0)),
      list: jest.fn().mockReturnValue(of([spiffeEntry])),
    } as Partial<TrustDomainService> as TrustDomainService;

    TestBed.configureTestingModule({
      declarations: [DomainSettingsTrustDomainsComponent],
      providers: [
        { provide: TrustDomainService, useValue: trustDomainServiceStub },
        { provide: DialogService, useValue: { confirm: jest.fn().mockReturnValue(of(true)) } },
        { provide: SnackbarService, useValue: { open: jest.fn() } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { data: { domain: { id: 'domain-1' }, trustDomains: [spiffeEntry, tokenExchangeEntry] } },
          },
        },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DomainSettingsTrustDomainsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shouldListBothKindsSideBySideEvenWhenTheyShareAName', () => {
    expect(component.trustDomains).toHaveLength(2);
    expect(component.trustDomains.map((td) => td.name)).toEqual(['shared.example', 'shared.example']);
    expect(component.trustDomains.map((td) => component.kindLabel(td))).toEqual(['SPIFFE', 'Token Exchange']);
  });

  it('shouldShowTheIssuerAsTheSubtitleOfATokenExchangeEntry', () => {
    expect(component.subtitle(tokenExchangeEntry)).toBe('https://issuer.example.com');
  });

  it('shouldShowTheJwksUrlAsTheSubtitleOfASpiffeEntry', () => {
    expect(component.subtitle(spiffeEntry)).toBe('https://spire.example/keys');
  });

  it('shouldPreferTheDescriptionOverTheKindSpecificSubtitle', () => {
    expect(component.subtitle({ ...spiffeEntry, description: 'Production SPIRE' })).toBe('Production SPIRE');
  });

  it('shouldLabelTheKeySourceOfEachEntry', () => {
    expect(component.keySourceLabel(spiffeEntry)).toBe('JWKS URL');
    expect(component.keySourceLabel(tokenExchangeEntry)).toBe('PEM Certificate');
  });

  it('shouldDeleteByIdentifierAndRefreshTheList', () => {
    component.delete('td-te', 'shared.example', new Event('click'));

    expect(trustDomainServiceStub.delete).toHaveBeenCalledWith('domain-1', 'td-te');
    expect(component.trustDomains).toEqual([spiffeEntry]);
  });
});
