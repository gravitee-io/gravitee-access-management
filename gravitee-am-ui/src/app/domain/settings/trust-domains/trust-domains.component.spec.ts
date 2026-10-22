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
import { of, Subject, throwError } from 'rxjs';

import { DialogService } from '../../../services/dialog.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { TrustDomainPage, TrustDomainService } from '../../../services/trust-domain.service';

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
    tokenExchangeEnabled: true,
    keyMaterial: { source: 'PEM', certificate: 'cert' },
  };
  const bothEntry: TrustDomain = {
    id: 'td-both',
    name: 'acme-corp',
    spiffeTrustDomain: 'acme.org',
    issuer: 'https://sso.acme.com',
    tokenExchangeEnabled: true,
    keyMaterial: { source: 'JWKS_URL', jwksUrl: 'https://sso.acme.com/keys' },
  };
  const crossAppAccessEntry: TrustDomain = {
    id: 'td-xaa',
    name: 'acme-suite',
    issuer: 'https://auth.acme.com',
    crossAppAccess: {
      enabled: true,
      resourceServers: [{ name: 'Acme Calendar', resource: 'https://calendar.acme.com' }],
    },
  };
  const issuerAndCrossAppAccessEntry: TrustDomain = {
    id: 'td-te-xaa',
    name: 'acme-sso',
    issuer: 'https://sso.acme.com',
    tokenExchangeEnabled: true,
    keyMaterial: { source: 'PEM', certificate: 'cert' },
    crossAppAccess: { enabled: true },
  };

  beforeEach(waitForAsync(() => {
    trustDomainServiceStub = {
      delete: jest.fn().mockReturnValue(of(void 0)),
      list: jest.fn().mockReturnValue(of({ data: [spiffeEntry], page: 0, totalCount: 1 })),
      next: jest.fn().mockReturnValue(of({ data: [issuerEntry], page: 1, totalCount: 12 })),
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
            snapshot: {
              data: {
                domain: { id: 'domain-1' },
                trustDomains: {
                  data: [spiffeEntry, issuerEntry, bothEntry, crossAppAccessEntry, issuerAndCrossAppAccessEntry],
                  page: 0,
                  totalCount: 12,
                },
              },
            },
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

  it('shouldTakeTheTotalCountFromTheResolvedPage', () => {
    expect(component.page).toEqual({ pageNumber: 0, size: 10, totalElements: 12 });
  });

  it('shouldLoadTheRequestedPage', () => {
    component.setPage({ offset: 1 });

    expect(trustDomainServiceStub.list).toHaveBeenCalledWith('domain-1', 1, 10, { sort: { prop: 'name', dir: 'asc' } });
    expect(component.trustDomains).toEqual([spiffeEntry]);
    expect(component.page.totalElements).toBe(1);
  });

  it('shouldFollowTheNextCursorWhenMovingToTheNextPage', () => {
    component.nextCursor = 'next-cursor';

    component.setPage({ offset: 1 });

    expect(trustDomainServiceStub.next).toHaveBeenCalledWith('next-cursor');
    expect(trustDomainServiceStub.list).not.toHaveBeenCalled();
    expect(component.trustDomains).toEqual([issuerEntry]);
  });

  it('shouldNotReuseTheNextCursorOfAPageWhoseSuccessorFailedToLoad', () => {
    component.nextCursor = 'cursor-of-page-0';
    (trustDomainServiceStub.next as jest.Mock).mockReturnValueOnce(throwError(() => new Error('boom')));

    component.setPage({ offset: 1 });
    component.setPage({ offset: 2 });

    expect(trustDomainServiceStub.next).toHaveBeenCalledTimes(1);
    expect(trustDomainServiceStub.list).toHaveBeenCalledWith('domain-1', 2, 10, { sort: { prop: 'name', dir: 'asc' } });
  });

  it('shouldNotReuseTheNextCursorOfAPreviousSortOrder', () => {
    component.nextCursor = 'cursor-sorted-ascending';
    (trustDomainServiceStub.list as jest.Mock).mockReturnValueOnce(new Subject<TrustDomainPage>());

    component.applySort({ sorts: [{ prop: 'name', dir: 'desc' }] });
    component.setPage({ offset: 1 });

    expect(trustDomainServiceStub.next).not.toHaveBeenCalled();
    expect(trustDomainServiceStub.list).toHaveBeenLastCalledWith('domain-1', 1, 10, { sort: { prop: 'name', dir: 'desc' } });
  });

  it('shouldIgnoreTheNextCursorWhenJumpingPages', () => {
    component.nextCursor = 'next-cursor';

    component.setPage({ offset: 2 });

    expect(trustDomainServiceStub.next).not.toHaveBeenCalled();
    expect(trustDomainServiceStub.list).toHaveBeenCalledWith('domain-1', 2, 10, { sort: { prop: 'name', dir: 'asc' } });
  });

  it('shouldKeepTheTableWhenAPageComesBackEmpty', () => {
    (trustDomainServiceStub.list as jest.Mock).mockReturnValue(of({ data: [], page: 2, totalCount: 12 }));

    component.setPage({ offset: 2 });
    fixture.detectChanges();

    expect(component.isEmpty()).toBe(false);
    expect(fixture.nativeElement.querySelector('[data-testid="trustDomainsTable"]')).not.toBeNull();
  });

  it('shouldShowTheEmptyStateWhenNoTrustedDomainIsLeft', () => {
    (trustDomainServiceStub.list as jest.Mock).mockReturnValue(of({ data: [], page: 0, totalCount: 0 }));

    component.applySort({ sorts: [{ prop: 'name', dir: 'asc' }] });

    expect(component.isEmpty()).toBe(true);
  });

  it('shouldCoverTheTableWithASpinnerWhileAPageIsLoading', () => {
    const response = new Subject<TrustDomainPage>();
    (trustDomainServiceStub.list as jest.Mock).mockReturnValue(response);
    const spinner = () => fixture.nativeElement.querySelector('[data-testid="trustDomainsLoading"]');

    component.setPage({ offset: 1 });
    fixture.detectChanges();
    expect(spinner()).not.toBeNull();

    response.next({ data: [spiffeEntry], page: 1, totalCount: 11 });
    response.complete();
    fixture.detectChanges();
    expect(spinner()).toBeNull();
  });

  it('shouldKeepOnlyTheLatestRequestedPage', () => {
    const stale = new Subject<TrustDomainPage>();
    const latest = new Subject<TrustDomainPage>();
    (trustDomainServiceStub.list as jest.Mock).mockReturnValueOnce(stale).mockReturnValueOnce(latest);

    component.setPage({ offset: 1 });
    component.setPage({ offset: 2 });
    latest.next({ data: [issuerEntry], page: 2, totalCount: 30 });
    stale.next({ data: [spiffeEntry], page: 1, totalCount: 30 });

    expect(component.trustDomains).toEqual([issuerEntry]);
  });

  it('shouldHideTheLoadingIndicatorWhenAPageFailsToLoad', () => {
    (trustDomainServiceStub.list as jest.Mock).mockReturnValue(throwError(() => new Error('boom')));

    component.applySort({ sorts: [{ prop: 'name', dir: 'desc' }] });

    expect(component.isLoading).toBe(false);
    expect(TestBed.inject(SnackbarService).open).toHaveBeenCalledWith('Failed to load trusted domains');
  });

  it('shouldReloadTheFirstPageInTheRequestedOrder', () => {
    component.page.pageNumber = 2;

    component.applySort({ sorts: [{ prop: 'name', dir: 'desc' }] });

    expect(trustDomainServiceStub.list).toHaveBeenCalledWith('domain-1', 0, 10, { sort: { prop: 'name', dir: 'desc' } });
    expect(component.page.pageNumber).toBe(0);
  });

  it('shouldLabelEachEntryWithTheUsagesItDeclares', () => {
    expect(component.trustDomains).toHaveLength(5);
    expect(component.trustDomains.map((td) => component.usagesLabel(td))).toEqual([
      'SPIFFE',
      'Token Exchange',
      'SPIFFE, Token Exchange',
      'Cross App Access',
      'Token Exchange, Cross App Access',
    ]);
  });

  it('shouldCollapseSeveralUsagesIntoACountedBadgeKeepingTheListInTheTooltip', () => {
    expect(component.usagesBadgeLabel(bothEntry)).toBe('2 usages');
    expect(component.usagesLabel(bothEntry)).toBe('SPIFFE, Token Exchange');
  });

  it('shouldNameTheUsageOnTheBadgeWhenOnlyOneIsDeclared', () => {
    expect(component.usagesBadgeLabel(spiffeEntry)).toBe('SPIFFE');
    expect(component.usagesBadgeLabel(crossAppAccessEntry)).toBe('Cross App Access');
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
    expect(trustDomainServiceStub.list).toHaveBeenCalledWith('domain-1', 0, 10, { sort: { prop: 'name', dir: 'asc' } });
    expect(component.trustDomains).toEqual([spiffeEntry]);
  });

  it('shouldStepBackAPageWhenDeletingTheLastEntryOfAPage', () => {
    component.page.pageNumber = 1;
    component.trustDomains = [issuerEntry];

    component.delete('td-te', 'shared.example', new Event('click'));

    expect(trustDomainServiceStub.list).toHaveBeenCalledWith('domain-1', 0, 10, { sort: { prop: 'name', dir: 'asc' } });
    expect(component.page.pageNumber).toBe(0);
  });
});
