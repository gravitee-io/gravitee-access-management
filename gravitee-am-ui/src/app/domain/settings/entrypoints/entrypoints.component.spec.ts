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
import { FormsModule } from '@angular/forms';
import { of, BehaviorSubject } from 'rxjs';
import { ActivatedRoute } from '@angular/router';

import { DomainService } from '../../../services/domain.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { AuthService } from '../../../services/auth.service';
import { DomainStoreService } from '../../../stores/domain.store';

jest.mock('@gravitee/ui-components/src/lib/utils', () => ({
  deepClone: (obj: any) => {
    if (obj) {
      return JSON.parse(JSON.stringify(obj));
    }
    return obj;
  },
}));

import { DomainSettingsEntrypointsComponent } from './entrypoints.component';

describe('DomainSettingsEntrypointsComponent', () => {
  let component: DomainSettingsEntrypointsComponent;
  let fixture: ComponentFixture<DomainSettingsEntrypointsComponent>;
  let domainServiceStub: DomainService;
  let snackbarServiceStub: SnackbarService;
  let authServiceStub: AuthService;
  let domainStoreStub: DomainStoreService;
  let activatedRouteStub: ActivatedRoute;

  beforeEach(waitForAsync(() => {
    domainServiceStub = {
      patchEntrypoints: jest.fn().mockReturnValue(of({ id: 'test-domain', name: 'Test Domain' })),
      notify: jest.fn(),
    } as Partial<DomainService> as DomainService;

    snackbarServiceStub = {
      open: jest.fn(),
    } as Partial<SnackbarService> as SnackbarService;

    authServiceStub = {
      hasPermissions: jest.fn().mockReturnValue(true),
    } as Partial<AuthService> as AuthService;

    domainStoreStub = {
      domain$: new BehaviorSubject({ id: 'test-domain', name: 'Test Domain', vhosts: [], corsSettings: {} }),
      set: jest.fn(),
      get current() {
        return { id: 'test-domain', name: 'Test Domain' };
      },
    } as Partial<DomainStoreService> as DomainStoreService;

    activatedRouteStub = {
      snapshot: {
        data: {
          entrypoint: {
            url: 'https://api.example.com',
          },
          environment: {
            domainRestrictions: [],
          },
        },
      },
    } as any as ActivatedRoute;

    TestBed.configureTestingModule({
      imports: [FormsModule],
      declarations: [DomainSettingsEntrypointsComponent],
      providers: [
        { provide: DomainService, useValue: domainServiceStub },
        { provide: SnackbarService, useValue: snackbarServiceStub },
        { provide: AuthService, useValue: authServiceStub },
        { provide: DomainStoreService, useValue: domainStoreStub },
        { provide: ActivatedRoute, useValue: activatedRouteStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    }).compileComponents();
  }));

  const createComponent = () => {
    fixture = TestBed.createComponent(DomainSettingsEntrypointsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  describe('component creation', () => {
    beforeEach(() => {
      createComponent();
    });

    it('should create', () => {
      expect(component).toBeTruthy();
    });
  });

  describe('hostPattern initialization', () => {
    const createComponentWithRestrictions = (domainRestrictions: string[]) => {
      activatedRouteStub.snapshot.data.environment.domainRestrictions = domainRestrictions;
      createComponent();
    };

    describe('with no domain restrictions', () => {
      beforeEach(() => {
        createComponentWithRestrictions([]);
      });

      it('should generate a pattern that accepts valid hostnames', () => {
        const regex = new RegExp(component.hostPattern);

        const validHosts = [
          'example.com',
          'subdomain.example.com',
          'my-domain.net',
          'host123.example.com',
          'example.com:8080',
          'sub.example.com:443',
          '_a.com',
          'very-long-subdomain-name-that-is-still-valid.example.com',
          'domain_with_underscores',
        ];

        validHosts.forEach((host) => {
          expect(regex.test(host)).toBe(true);
        });
      });

      it('should generate a pattern that rejects invalid hostnames', () => {
        const regex = new RegExp(component.hostPattern);

        const invalidHosts = [
          '-example.com', // starts with hyphen
          'example-.com', // ends with hyphen
          'example..com', // consecutive dots
          'example_.com', // underscore before dot
          'example.com.', // ends with dot
          '.example.com', // starts with dot
          'example.com:999999', // port too large
          'example.com.:8000', // dot before port
          'example.com:abc', // invalid port
          ' example.com:0', // leading space
          'example.com:0 ', // trailing space
          '', // empty
        ];

        invalidHosts.forEach((host) => {
          expect(regex.test(host)).toBe(false);
        });
      });

      it('should generate a pattern that accepts maximum length domain labels', () => {
        const regex = new RegExp(component.hostPattern);

        const longDomain = 'a'.repeat(63);
        expect(regex.test(longDomain)).toBe(true);
      });

      it('should generate a pattern that rejects too long domain labels', () => {
        const regex = new RegExp(component.hostPattern);
        const lengths = [64, 65, 1000];

        lengths.forEach((length) => {
          const longDomain = 'a'.repeat(length);
          expect(regex.test(longDomain)).toBe(false);
        });
      });
    });

    describe('with domain restrictions', () => {
      beforeEach(() => {
        createComponentWithRestrictions(['example.com', 'test.org', 'my-domain.net']);
      });

      it('should generate a pattern that accepts valid subdomains of restricted domains', () => {
        const regex = new RegExp(component.hostPattern);

        const validHosts = [
          'example.com',
          'subdomain.example.com',
          'my-domain.net',
          'host123.example.com',
          'example.com:8080',
          'sub.example.com:443',
          'very-long-subdomain-name-that-is-still-valid.example.com',
        ];

        validHosts.forEach((host) => {
          expect(regex.test(host)).toBe(true);
        });
      });

      it('should generate a pattern that rejects domains not in restrictions', () => {
        const regex = new RegExp(component.hostPattern);

        const invalidHosts = ['other.com', 'example.org', 'test.com', 'unauthorized.net'];

        invalidHosts.forEach((host) => {
          expect(regex.test(host)).toBe(false);
        });
      });

      it('should generate a pattern that rejects invalid hostnames even for restricted domains', () => {
        const regex = new RegExp(component.hostPattern);

        const invalidHosts = [
          '-example.com',
          'example-.com',
          'example..com',
          'example.com.',
          '.example.com',
          'bad-subdomain_.example.com',
          'example.com:999999',
          'example.com.:8000',
          'example.com:abc',
          ' example.com:0',
          'example.com:0 ',
        ];

        invalidHosts.forEach((host) => {
          expect(regex.test(host)).toBe(false);
        });
      });

      it('should generate a pattern that accepts maximum length domain labels', () => {
        const regex = new RegExp(component.hostPattern);

        const longDomain = 'a'.repeat(63) + '.test.org';
        expect(regex.test(longDomain)).toBe(true);
      });

      it('should generate a pattern that rejects too long domain labels', () => {
        const regex = new RegExp(component.hostPattern);
        const lengths = [64, 65, 1000];

        lengths.forEach((length) => {
          const longDomain = 'a'.repeat(length) + '.test.org';
          expect(regex.test(longDomain)).toBe(false);
        });
      });

      it('should escape special regex characters in domain restrictions', () => {
        expect(component.hostPattern).toBe(
          '^(?:(?!-)[A-Za-z0-9\\-_]{1,63}(?<![\\-_])\\.)*(?:example\\.com|test\\.org|my\\-domain\\.net)(?::[0-9]{1,5})?$',
        );
      });
    });
  });
});
