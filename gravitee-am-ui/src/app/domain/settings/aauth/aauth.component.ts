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

import { SnackbarService } from '../../../services/snackbar.service';
import { DomainService } from '../../../services/domain.service';
import { AuthService } from '../../../services/auth.service';
import { DomainStoreService } from '../../../stores/domain.store';
import { ProviderService } from '../../../services/provider.service';

@Component({
  selector: 'app-aauth',
  templateUrl: './aauth.component.html',
  standalone: false,
})
export class AauthComponent implements OnInit {
  domainId: string;
  domain: any = {};
  formChanged = false;
  editMode: boolean;
  domainIdps: any[] = [];

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
    private providerService: ProviderService,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.subscribe((domain) => (this.domain = deepClone(domain)));
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_aauth_update']);
    this.loadIdentityProviders();
  }

  private loadIdentityProviders() {
    this.providerService.findByDomain(this.domainId).subscribe((idps) => {
      const defaults = this.domain.aauth?.defaultIdentityProviders || [];
      this.domainIdps = idps.map((idp) => ({
        ...idp,
        selected: defaults.includes(idp.id),
      }));
    });
  }

  save() {
    this.domainService.patchAauthSettings(this.domainId, this.domain).subscribe((data) => {
      this.domainStore.set(data);
      this.domain = data;
      this.formChanged = false;
      this.snackbarService.open('AAUTH configuration updated');
    });
  }

  enableAAuth(event) {
    if (!this.domain.aauth) {
      this.domain.aauth = {};
    }
    this.domain.aauth.enabled = event.checked;
    if (event.checked && this.domain.aauth.authTokenLifespan == null) {
      this.domain.aauth.authTokenLifespan = 300;
      this.domain.aauth.autoRegisterAgents = true;
    }
    this.formChanged = true;
  }

  isAAuthEnabled(): boolean {
    return this.domain.aauth?.enabled;
  }

  toggleIdp(idp: any) {
    if (!this.domain.aauth) {
      this.domain.aauth = {};
    }
    if (!this.domain.aauth.defaultIdentityProviders) {
      this.domain.aauth.defaultIdentityProviders = [];
    }
    const idx = this.domain.aauth.defaultIdentityProviders.indexOf(idp.id);
    if (idx >= 0) {
      this.domain.aauth.defaultIdentityProviders.splice(idx, 1);
      idp.selected = false;
    } else {
      this.domain.aauth.defaultIdentityProviders.push(idp.id);
      idp.selected = true;
    }
    this.formChanged = true;
  }

  formChange() {
    this.formChanged = true;
  }
}
