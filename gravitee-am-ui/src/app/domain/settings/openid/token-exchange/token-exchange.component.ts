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
import { Component, OnInit } from '@angular/core';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { AuthService } from '../../../../services/auth.service';
import { DomainService } from '../../../../services/domain.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { DomainStoreService } from '../../../../stores/domain.store';

@Component({
  selector: 'app-oidc-token-exchange',
  templateUrl: './token-exchange.component.html',
  styleUrls: ['./token-exchange.component.scss'],
  standalone: false,
})
export class TokenExchangeComponent implements OnInit {
  domainId: string;
  domain: any = {};
  formChanged = false;
  editMode: boolean;

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.subscribe((domain) => (this.domain = deepClone(domain)));
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_openid_update']);
    if (!this.domain.oidc.tokenExchangeSettings) {
      this.domain.oidc.tokenExchangeSettings = {
        enabled: false,
        allowImpersonation: false,
        allowDelegation: false,
        requireClientAuthentication: false,
        allowScopeDownscoping: false,
        tokenLifetimeMultiplier: 1.0,
      };
    }
  }

  save() {
    this.domainService.patchOpenidDCRSettings(this.domainId, this.domain).subscribe((data) => {
      this.domainStore.set(data);
      this.domain = data;
      this.formChanged = false;
      this.snackbarService.open('Token Exchange configuration updated');
    });
  }

  enableTokenExchange(event) {
    if (!this.domain.oidc.tokenExchangeSettings) {
      this.domain.oidc.tokenExchangeSettings = {};
    }
    this.domain.oidc.tokenExchangeSettings.enabled = event.checked;
    this.formChanged = true;
  }

  isTokenExchangeEnabled(): boolean {
    return this.domain.oidc.tokenExchangeSettings?.enabled;
  }

  modelChanged(): void {
    this.formChanged = true;
  }
}
