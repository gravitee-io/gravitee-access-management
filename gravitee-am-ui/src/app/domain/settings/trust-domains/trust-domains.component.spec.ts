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
    name: 'spire-prod',
    spiffeTrustDomain: 'shared.example',
    keyMaterial: { source: 'JWKS_URL', jwksUrl: 'https://spire.example/keys' },
  };
  const issuerEntry: TrustDomain = {
    id: 'td-te',
    name: 'external-idp',
    issuer: 'https://issuer.example.com',
    keyMaterial: { source: 'PEM', certificate: 'cert' },
  };
  const bothEntry: TrustDomain = {
    id: 'td-both',
    name: 'acme-corp',
    spiffeTrustDomain: 'acme.org',
    issuer: 'https://sso.acme.com',
    keyMaterial: { source: 'JWKS_URL', jwksUrl: 'https://sso.acme.com/keys' },
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
            snapshot: { data: { domain: { id: 'domain-1' }, trustDomains: [spiffeEntry, issuerEntry, bothEntry] } },
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

  it('shouldLabelEachEntryWithTheUsagesItDeclares', () => {
    expect(component.trustDomains).toHaveLength(3);
    expect(component.trustDomains.map((td) => component.usagesLabel(td))).toEqual([
      'SPIFFE',
      'OIDC - Trusted Issuer',
      'SPIFFE, OIDC - Trusted Issuer',
    ]);
  });

  it('shouldShowTheIssuerAsTheSubtitleOfATrustedIssuerEntry', () => {
    expect(component.subtitle(issuerEntry)).toBe('https://issuer.example.com');
  });

  it('shouldShowTheSpiffeTrustDomainAsTheSubtitleOfASpiffeEntry', () => {
    expect(component.subtitle(spiffeEntry)).toBe('shared.example');
  });

  it('shouldPreferTheDescriptionOverTheMatcherSubtitle', () => {
    expect(component.subtitle({ ...spiffeEntry, description: 'Production SPIRE' })).toBe('Production SPIRE');
  });

  it('shouldLabelTheKeySourceOfEachEntry', () => {
    expect(component.keySourceLabel(spiffeEntry)).toBe('JWKS URL');
    expect(component.keySourceLabel(issuerEntry)).toBe('PEM Certificate');
  });

  it('shouldDeleteByIdentifierAndRefreshTheList', () => {
    component.delete('td-te', 'shared.example', new Event('click'));

    expect(trustDomainServiceStub.delete).toHaveBeenCalledWith('domain-1', 'td-te');
    expect(component.trustDomains).toEqual([spiffeEntry]);
  });
});
