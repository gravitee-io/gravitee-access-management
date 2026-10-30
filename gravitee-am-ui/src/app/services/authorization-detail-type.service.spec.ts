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

import { AuthorizationDetailTypeService } from './authorization-detail-type.service';

describe('AuthorizationDetailTypeService', () => {
  const baseUrl = `${AppConfig.settings.domainBaseURL}domain-1/authorization-detail-types`;
  let http: HttpTestingController;
  let service: AuthorizationDetailTypeService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      teardown: { destroyAfterEach: false },
      providers: [AuthorizationDetailTypeService, provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AuthorizationDetailTypeService);
  });

  afterEach(() => http.verify());

  it('should request a page number with an uppercase sort direction', () => {
    service.list('domain-1', 10, 2, { prop: 'name', dir: 'desc' }).subscribe();

    http.expectOne(`${baseUrl}?page=2&limit=10&sort=name&dir=DESC`).flush({ data: [] });
  });

  it('should encode the query, whose wildcards stay readable', () => {
    service.list('domain-1', 10, 0, { prop: 'type', dir: 'asc' }, '*a&b#c*').subscribe();

    http.expectOne(`${baseUrl}?page=0&limit=10&sort=type&dir=ASC&q=*a%26b%23c*`).flush({ data: [] });
  });

  it('should follow nextCursor from the base URL', () => {
    const nextCursor = '/organizations/o/environments/e/domains/domain-1/authorization-detail-types?cursor=abc&page=1';

    service.next(nextCursor).subscribe();

    http.expectOne(AppConfig.settings.baseURL + nextCursor).flush({ data: [] });
  });

  it('should send a patch as given', () => {
    service.patch('domain-1', 'id-1', { discovery: true }).subscribe();

    const request = http.expectOne(`${baseUrl}/id-1`);
    expect(request.request.method).toEqual('PATCH');
    expect(request.request.body).toEqual({ discovery: true });
    request.flush({});
  });
});
