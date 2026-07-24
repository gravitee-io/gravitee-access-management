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

import { UserPreferencesService } from './user-preferences.service';

describe('UserPreferencesService', () => {
  let httpTestingController: HttpTestingController;
  let service: UserPreferencesService;
  const preferencesUrl = `${AppConfig.settings.baseURL}/user/preferences`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      teardown: { destroyAfterEach: false },
      providers: [UserPreferencesService, provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()],
    });

    httpTestingController = TestBed.get(HttpTestingController);
    service = TestBed.get(UserPreferencesService);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  describe('load', () => {
    it('stores fetched preferences and exposes them via preferences()', (done) => {
      const response = { defaultDomainId: 'domain-1', defaultEnvironmentId: 'env-1', pinnedDomainIds: ['domain-1', 'domain-2'] };

      service.load().subscribe(() => {
        expect(service.preferences()).toEqual(response);
        done();
      });

      httpTestingController.expectOne({ method: 'GET', url: preferencesUrl }).flush(response);
    });

    it('falls back to an empty object when the server returns nothing', (done) => {
      service.load().subscribe(() => {
        expect(service.preferences()).toEqual({});
        done();
      });

      httpTestingController.expectOne({ method: 'GET', url: preferencesUrl }).flush(null);
    });
  });

  describe('pinnedDomainIds / isPinned', () => {
    it('returns an empty array and false when nothing has been loaded yet', () => {
      expect(service.pinnedDomainIds()).toEqual([]);
      expect(service.isPinned('domain-1')).toBe(false);
    });
  });

  describe('isDefault / defaultDomainId', () => {
    it('returns null/false when nothing has been loaded yet', () => {
      expect(service.defaultDomainId()).toBeNull();
      expect(service.isDefault('domain-1')).toBe(false);
    });
  });

  describe('togglePin', () => {
    it('adds a domain id when it is not yet pinned', (done) => {
      service.togglePin('domain-1').subscribe(() => {
        expect(service.pinnedDomainIds()).toEqual(['domain-1']);
        done();
      });

      const req = httpTestingController.expectOne({ method: 'PUT', url: preferencesUrl });
      expect(req.request.body.pinnedDomainIds).toEqual(['domain-1']);
      req.flush({ pinnedDomainIds: ['domain-1'] });
    });

    it('removes a domain id when it is already pinned, leaving the others untouched', (done) => {
      service.load().subscribe();
      httpTestingController.expectOne({ method: 'GET', url: preferencesUrl }).flush({ pinnedDomainIds: ['domain-1', 'domain-2'] });

      service.togglePin('domain-1').subscribe(() => {
        expect(service.pinnedDomainIds()).toEqual(['domain-2']);
        done();
      });

      const req = httpTestingController.expectOne({ method: 'PUT', url: preferencesUrl });
      expect(req.request.body.pinnedDomainIds).toEqual(['domain-2']);
      req.flush({ pinnedDomainIds: ['domain-2'] });
    });

    it('sends a full-object PUT that carries the existing default alongside the new pin', (done) => {
      service.load().subscribe();
      httpTestingController
        .expectOne({ method: 'GET', url: preferencesUrl })
        .flush({ defaultDomainId: 'domain-9', defaultEnvironmentId: 'env-1' });

      service.togglePin('domain-1').subscribe(() => done());

      const req = httpTestingController.expectOne({ method: 'PUT', url: preferencesUrl });
      expect(req.request.body).toEqual({ defaultDomainId: 'domain-9', defaultEnvironmentId: 'env-1', pinnedDomainIds: ['domain-1'] });
      req.flush({});
    });
  });

  describe('toggleDefaultDomain', () => {
    it('sets the domain as default when it is not currently the default', (done) => {
      service.toggleDefaultDomain('domain-1', 'env-1').subscribe(() => done());

      const req = httpTestingController.expectOne({ method: 'PUT', url: preferencesUrl });
      expect(req.request.body).toEqual({ defaultDomainId: 'domain-1', defaultEnvironmentId: 'env-1' });
      req.flush({ defaultDomainId: 'domain-1', defaultEnvironmentId: 'env-1' });
    });

    it('unsets the default when the domain is already the default', (done) => {
      service.load().subscribe();
      httpTestingController
        .expectOne({ method: 'GET', url: preferencesUrl })
        .flush({ defaultDomainId: 'domain-1', defaultEnvironmentId: 'env-1' });

      service.toggleDefaultDomain('domain-1', 'env-1').subscribe(() => done());

      const req = httpTestingController.expectOne({ method: 'PUT', url: preferencesUrl });
      expect(req.request.body).toEqual({ defaultDomainId: null, defaultEnvironmentId: null });
      req.flush({});
    });
  });

  describe('consumeDefaultDomainRedirect', () => {
    it('returns the default domain id exactly once after load, then null on every subsequent call', () => {
      service.load().subscribe();
      httpTestingController.expectOne({ method: 'GET', url: preferencesUrl }).flush({ defaultDomainId: 'domain-1' });

      expect(service.consumeDefaultDomainRedirect()).toBe('domain-1');
      expect(service.consumeDefaultDomainRedirect()).toBeNull();
      expect(service.consumeDefaultDomainRedirect()).toBeNull();
    });

    it('returns null when load found no default domain', () => {
      service.load().subscribe();
      httpTestingController.expectOne({ method: 'GET', url: preferencesUrl }).flush({});

      expect(service.consumeDefaultDomainRedirect()).toBeNull();
    });

    it('returns null before load has ever been called', () => {
      expect(service.consumeDefaultDomainRedirect()).toBeNull();
    });
  });
});
