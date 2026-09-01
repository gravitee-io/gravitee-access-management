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
import { of } from 'rxjs';

import { AuthService } from '../../../../services/auth.service';
import { DomainService } from '../../../../services/domain.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { DomainStoreService } from '../../../../stores/domain.store';

jest.mock('@gravitee/ui-components/src/lib/utils', () => ({
  deepClone: (value: unknown) => JSON.parse(JSON.stringify(value)),
}));

import { TrustDomainKeyRetrievalComponent } from './trust-domain-key-retrieval.component';

describe('TrustDomainKeyRetrievalComponent', () => {
  let component: TrustDomainKeyRetrievalComponent;
  let fixture: ComponentFixture<TrustDomainKeyRetrievalComponent>;
  let domainServiceStub: DomainService;

  const domain = {
    id: 'domain-1',
    oidc: { workloadIdentitySettings: { enabled: true } },
    keyRetrievalSettings: { allowUnsecuredHttpUri: false, fetchTimeoutMs: 5000 },
  };

  function createComponent(storedDomain: unknown) {
    domainServiceStub = {
      patch: jest.fn().mockReturnValue(of(storedDomain)),
    } as Partial<DomainService> as DomainService;

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      declarations: [TrustDomainKeyRetrievalComponent],
      providers: [
        { provide: DomainService, useValue: domainServiceStub },
        { provide: SnackbarService, useValue: { open: jest.fn() } },
        { provide: AuthService, useValue: { hasPermissions: () => true } },
        { provide: DomainStoreService, useValue: { domain$: of(storedDomain), set: jest.fn() } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      teardown: { destroyAfterEach: false },
    });
    fixture = TestBed.createComponent(TrustDomainKeyRetrievalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(waitForAsync(() => createComponent(domain)));

  it('shouldSendKeyRetrievalSettingsOnly', () => {
    component.toggleAllowPrivateIpAddress({ checked: true });
    component.save();

    expect(domainServiceStub.patch).toHaveBeenCalledWith('domain-1', {
      keyRetrievalSettings: { allowUnsecuredHttpUri: false, allowPrivateIpAddress: true, fetchTimeoutMs: 5000 },
    });
  });

  it('shouldSeedDefaultsWhenTheDomainCarriesNoKeyRetrievalSettings', () => {
    createComponent({ id: 'domain-2' });

    expect(component.domain.keyRetrievalSettings).toEqual({
      allowUnsecuredHttpUri: false,
      allowPrivateIpAddress: false,
      fetchTimeoutMs: 5000,
      maxResponseSizeKb: 32,
      cacheTtlSeconds: 300,
      cacheMaxEntries: 50,
    });
  });
});
