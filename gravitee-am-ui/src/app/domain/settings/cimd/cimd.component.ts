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

@Component({
  selector: 'app-cimd',
  templateUrl: './cimd.component.html',
  styleUrls: ['./cimd.component.scss'],
  standalone: false,
})
export class CimdSettingsComponent implements OnInit {
  domain: any = {};
  domainId: string;
  cimdSettings: any = {};
  formChanged = false;
  editMode: boolean;
  allowedDomainInputValue = '';

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.subscribe((domain) => (this.domain = deepClone(domain)));
    this.domainId = this.domain.id;
    this.cimdSettings = deepClone(this.domain.oidc?.cimdSettings) || {};
    this.editMode = this.authService.hasPermissions(['domain_openid_update']);
  }

  save() {
    this.domainService.patchCimdSettings(this.domainId, this.cimdSettings).subscribe((data) => {
      this.domainStore.set(data);
      this.domain = data;
      this.cimdSettings = deepClone(data.oidc?.cimdSettings) || {};
      this.formChanged = false;
      this.snackbarService.open('CIMD configuration updated');
    });
  }

  enableCIMD(event) {
    this.cimdSettings.enabled = event.checked;
    this.formChanged = true;
  }

  isCIMDEnabled(): boolean {
    return this.cimdSettings.enabled;
  }

  addAllowedDomain(event) {
    event.preventDefault();
    if (this.allowedDomainInputValue) {
      this.cimdSettings.allowedDomains = [...(this.cimdSettings.allowedDomains || []), this.allowedDomainInputValue];
      this.allowedDomainInputValue = '';
      this.formChanged = true;
    }
  }

  removeAllowedDomain(domain: string) {
    this.cimdSettings.allowedDomains = (this.cimdSettings.allowedDomains || []).filter((d) => d !== domain);
    this.formChanged = true;
  }

  onFormChange() {
    this.formChanged = true;
  }
}
