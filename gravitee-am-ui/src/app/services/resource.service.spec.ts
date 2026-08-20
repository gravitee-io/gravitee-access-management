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
import { SKIP_ERROR_SNACKBAR } from '../interceptors/http-request.interceptor';

import { ResourceService } from './resource.service';

describe('ResourceService', () => {
  let httpTestingController: HttpTestingController;
  let resourceService: ResourceService;

  const domainId = 'domain-1234';
  const resourcesUrl = `${AppConfig.settings.domainBaseURL}${domainId}/resources`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      teardown: { destroyAfterEach: false },
      imports: [],
      providers: [ResourceService, provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    });

    httpTestingController = TestBed.get(HttpTestingController);
    resourceService = TestBed.get(ResourceService);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  describe('findByDomainWhenPermitted', () => {
    it('returns the resources when the user is permitted', (done) => {
      const resources = [{ id: 'resource-1', name: 'my-smtp', type: 'smtp-am-resource' }];

      resourceService.findByDomainWhenPermitted(domainId).subscribe((result) => {
        expect(result).toEqual(resources);
        done();
      });

      httpTestingController.expectOne({ method: 'GET', url: resourcesUrl }).flush(resources);
    });

    it('returns an empty list when the user lacks DOMAIN_RESOURCE[LIST]', (done) => {
      resourceService.findByDomainWhenPermitted(domainId).subscribe((result) => {
        expect(result).toEqual([]);
        done();
      });

      const req = httpTestingController.expectOne({ method: 'GET', url: resourcesUrl });
      expect(req.request.context.get(SKIP_ERROR_SNACKBAR)).toBe(true);
      req.flush({ message: 'Permission denied' }, { status: 403, statusText: 'Forbidden' });
    });

    it('returns an empty list when the request fails for any other reason', (done) => {
      resourceService.findByDomainWhenPermitted(domainId).subscribe({
        next: (result) => {
          expect(result).toEqual([]);
          done();
        },
        error: (error: unknown) => done(error),
      });

      httpTestingController
        .expectOne({ method: 'GET', url: resourcesUrl })
        .flush({ message: 'Server error' }, { status: 500, statusText: 'Internal Server Error' });
    });
  });
});
