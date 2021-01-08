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
import { TestBed, inject } from '@angular/core/testing';

import { AuditService } from './audit.service';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AnalyticsService } from './analytics.service';
import { AppConfig } from '../../config/app.config';
import { OrganizationService } from './organization.service';

jest.mock('moment', () => {
  return () => jest.requireActual('moment')('2021-01-04T00:00:00.000Z');
});

describe('AuditService', () => {
  let httpTestingController: HttpTestingController;
  let auditService: AuditService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuditService, OrganizationService],
      imports: [HttpClientTestingModule],
    });

    httpTestingController = TestBed.get(HttpTestingController);
    auditService = TestBed.get(AuditService);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  describe('findByDomain', () => {
    it('calls domain audits endpoint', (done) => {
      const domainId = 'domain-1234';
      const page = 0;
      const size = 10;
      const auditResponse = { currentPage: 0, data: [], totalCount: 0 };

      auditService.findByDomain(domainId, page, size)
        .subscribe(result => {
          expect(result).toEqual(auditResponse);
          done();
        });

      httpTestingController
        .expectOne({
          method: 'GET',
          url: `${AppConfig.settings.domainBaseURL}${domainId}/audits?page=${page}&size=${size}&from=1609632000000&to=1609718400000`,
        })
        .flush(auditResponse);
    });
  });

  describe('search', () => {
    it('calls domain audits endpoint', (done) => {
      const domainId = 'domain-1234';
      const page = 0;
      const size = 10;
      const type = 'TYPE';
      const user = 'user-123';
      const status = 'ACTIVATED';
      const from = 1609632000000;
      const to = 1609718400000;
      const auditResponse = { currentPage: 0, data: [], totalCount: 0 };

      auditService.search(domainId, page, size, type, status, user, from, to, false)
        .subscribe(result => {
          expect(result).toEqual(auditResponse);
          done();
        });

      httpTestingController
        .expectOne({
          method: 'GET',
          url: `${AppConfig.settings.domainBaseURL}${domainId}/audits?page=${page}&size=${size}&type=${type}&status=${status}&user=${user}&from=${from}&to=${to}`,
        })
        .flush(auditResponse);
    });

  });
});
