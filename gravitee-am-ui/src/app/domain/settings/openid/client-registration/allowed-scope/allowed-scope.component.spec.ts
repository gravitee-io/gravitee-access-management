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
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';

import { AuthService } from '../../../../../services/auth.service';
import { DomainService } from '../../../../../services/domain.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { DomainStoreService } from '../../../../../stores/domain.store';

import { ClientRegistrationAllowedScopeComponent } from './allowed-scope.component';

describe('ClientRegistrationAllowedScopeComponent', () => {
  const domain = {
    id: 'domain-id',
    name: 'Test domain',
    oidc: {
      clientRegistrationSettings: {
        isDynamicClientRegistrationEnabled: true,
        isAllowedScopesEnabled: false,
        allowedScopes: ['openid'],
      },
    },
  };
  const updatedDomain = {
    ...domain,
    oidc: {
      clientRegistrationSettings: {
        ...domain.oidc.clientRegistrationSettings,
        isAllowedScopesEnabled: true,
      },
    },
  };

  let component: ClientRegistrationAllowedScopeComponent;
  let domainService: DomainService;
  let domainStore: DomainStoreService;

  beforeEach(() => {
    domainService = {
      patchOpenidDCRSettings: jest.fn().mockReturnValue(of(updatedDomain)),
      notify: jest.fn(),
    } as Partial<DomainService> as DomainService;
    domainStore = {
      domain$: new BehaviorSubject(domain),
      set: jest.fn(),
    } as Partial<DomainStoreService> as DomainStoreService;

    component = new ClientRegistrationAllowedScopeComponent(
      domainService,
      {} as ActivatedRoute,
      { open: jest.fn() } as Partial<SnackbarService> as SnackbarService,
      { hasPermissions: jest.fn().mockReturnValue(true) } as Partial<AuthService> as AuthService,
      domainStore,
    );
    component.ngOnInit();
  });

  afterEach(() => component.ngOnDestroy());

  it('should preserve configured scopes and synchronize the domain store when only the restriction changes', () => {
    component.enableAllowedScopesFilter({ checked: true });
    component.patch();

    expect(domainService.patchOpenidDCRSettings).toHaveBeenCalledWith('domain-id', {
      oidc: {
        clientRegistrationSettings: {
          allowedScopes: ['openid'],
          isAllowedScopesEnabled: true,
        },
      },
    });
    expect(domainStore.set).toHaveBeenCalledWith(updatedDomain);
    expect(domainService.notify).toHaveBeenCalledWith(updatedDomain);
  });
});
