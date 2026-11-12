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

import { OrganizationService } from './organization.service';
import { UserService } from './user.service';

describe('UserService', () => {
  const consentsUrl = `${AppConfig.settings.domainBaseURL}domain-1/users/user-1/consents`;
  let http: HttpTestingController;
  let service: UserService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      teardown: { destroyAfterEach: false },
      providers: [
        UserService,
        { provide: OrganizationService, useValue: {} },
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(UserService);
  });

  afterEach(() => http.verify());

  it('should list and revoke scope consents on the scopes path', () => {
    service.consents('domain-1', 'user-1', 'client-1').subscribe();
    service.revokeConsents('domain-1', 'user-1', 'client-1').subscribe();
    service.revokeConsent('domain-1', 'user-1', 'consent-1').subscribe();

    http.expectOne({ method: 'GET', url: `${consentsUrl}/scopes?clientId=client-1` }).flush([]);
    http.expectOne({ method: 'DELETE', url: `${consentsUrl}/scopes?clientId=client-1` }).flush(null);
    http.expectOne({ method: 'DELETE', url: `${consentsUrl}/scopes/consent-1` }).flush(null);
  });

  it('should request a page of authorization detail consents with encoded filters', () => {
    service
      .authorizationDetailConsents('domain-1', 'user-1', { clientId: 'client 1', type: 'a&b' }, 10, 2, { prop: 'expiresAt', dir: 'asc' })
      .subscribe();

    http
      .expectOne(`${consentsUrl}/authorization-details?page=2&limit=10&sort=expiresAt&dir=ASC&clientId=client%201&type=a%26b`)
      .flush({ data: [] });
  });

  it('should list every authorization detail consent by following nextCursor until the last page', () => {
    const nextCursor = '/organizations/o/environments/e/domains/domain-1/users/user-1/consents/authorization-details?cursor=abc&page=1';
    let result: any[];

    service.allAuthorizationDetailConsents('domain-1', 'user-1').subscribe((consents) => (result = consents));

    http
      .expectOne(`${consentsUrl}/authorization-details?page=0&limit=100&sort=updatedAt&dir=DESC`)
      .flush({ data: [{ id: 'a' }], nextCursor });
    http.expectOne(AppConfig.settings.baseURL + nextCursor).flush({ data: [{ id: 'b' }] });
    expect(result).toEqual([{ id: 'a' }, { id: 'b' }]);
  });
});
