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
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

import { AppConfig } from '../../config/app.config';

import { TrustDomainService } from './trust-domain.service';

describe('TrustDomainService', () => {
  const domainId = 'domain-1';
  let httpTestingController: HttpTestingController;
  let trustDomainService: TrustDomainService;

  const trustedDomainsUrl = (query: string) => `${AppConfig.settings.domainBaseURL}${domainId}/trusted-domains?${query}`;
  const nextCursor = `/organizations/DEFAULT/environments/DEFAULT/domains/${domainId}/trusted-domains/_cursor?page=1&cursor=abc`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      teardown: { destroyAfterEach: false },
      providers: [TrustDomainService, provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    });

    httpTestingController = TestBed.inject(HttpTestingController);
    trustDomainService = TestBed.inject(TrustDomainService);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('shouldRequestAPageForAUsageAndFlattenItsEntries', (done) => {
    trustDomainService.list(domainId, 2, 10, { usage: 'SPIFFE' }).subscribe((result) => {
      expect(result.page).toBe(2);
      expect(result.totalCount).toBe(21);
      expect(result.data.map((td) => td.spiffeTrustDomain)).toEqual(['acme']);
      done();
    });

    httpTestingController
      .expectOne({ method: 'GET', url: trustedDomainsUrl('page=2&limit=10&usage=SPIFFE') })
      .flush({ page: 2, totalCount: 21, data: [{ id: 'td-1', name: 'acme', spiffe: { spiffeTrustDomain: 'acme' } }] });
  });

  it('shouldRequestAPageSortedByName', (done) => {
    trustDomainService.list(domainId, 0, 10, { sort: { prop: 'name', dir: 'desc' } }).subscribe((result) => {
      expect(result.data.map((td) => td.name)).toEqual(['bravo', 'alpha']);
      done();
    });

    httpTestingController.expectOne({ method: 'GET', url: trustedDomainsUrl('page=0&limit=10&sort=name&dir=desc') }).flush({
      page: 0,
      totalCount: 2,
      data: [
        { id: 'td-2', name: 'bravo' },
        { id: 'td-1', name: 'alpha' },
      ],
    });
  });

  it('shouldFollowTheNextCursor', (done) => {
    trustDomainService.next(nextCursor).subscribe((result) => {
      expect(result.page).toBe(1);
      expect(result.data.map((td) => td.spiffeTrustDomain)).toEqual(['acme']);
      done();
    });

    httpTestingController
      .expectOne({ method: 'GET', url: AppConfig.settings.baseURL + nextCursor })
      .flush({ page: 1, totalCount: 11, data: [{ id: 'td-1', name: 'acme', spiffe: { spiffeTrustDomain: 'acme' } }] });
  });

  it('shouldWalkEveryPageWhenListingAll', (done) => {
    trustDomainService.listAll(domainId, 'SPIFFE').subscribe((result) => {
      expect(result.map((td) => td.name)).toEqual(['first', 'second']);
      done();
    });

    httpTestingController
      .expectOne({ method: 'GET', url: trustedDomainsUrl('page=0&limit=100&usage=SPIFFE') })
      .flush({ page: 0, totalCount: 150, nextCursor, data: [{ id: 'td-1', name: 'first' }] });
    httpTestingController
      .expectOne({ method: 'GET', url: AppConfig.settings.baseURL + nextCursor })
      .flush({ page: 1, totalCount: 150, data: [{ id: 'td-2', name: 'second' }] });
  });
});
