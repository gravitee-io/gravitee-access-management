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
import { ActivatedRoute } from '@angular/router';

import { SnackbarService } from '../../../services/snackbar.service';
import { DomainService } from '../../../services/domain.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-self-service-account',
  templateUrl: './self-service-account.component.html',
})
export class DomainSettingsSelfServiceAccountComponent implements OnInit {
  domainId: string;
  domain: any = {};
  resetPassword: any = {};
  formChanged = false;
  editMode: boolean;
  tokenAge: number;

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainId = this.domain.id;
    this.editMode = this.authService.hasPermissions(['domain_settings_update']);
    if (this.isSelfServiceAccountEnabled() && this.domain.selfServiceAccountManagementSettings.resetPassword) {
      this.tokenAge = this.domain.selfServiceAccountManagementSettings.resetPassword.tokenAge;
      this.resetPassword = this.domain.selfServiceAccountManagementSettings.resetPassword || {};
    }
  }

  save() {
    const selfServiceAccountSettings: any = {};
    selfServiceAccountSettings.enabled = this.isSelfServiceAccountEnabled();
    if (this.tokenAge < 0) {
      this.snackbarService.open('Max age must be greater or equals to zero');
      return;
    }
    this.resetPassword.tokenAge = this.tokenAge;
    selfServiceAccountSettings.resetPassword = this.resetPassword;
    this.domainService.patch(this.domainId, { selfServiceAccountManagementSettings: selfServiceAccountSettings }).subscribe((data) => {
      this.domain = data;
      this.formChanged = false;
      this.snackbarService.open('Self-service account configuration updated');
    });
  }

  enableSelfServiceAccount(event) {
    this.domain.selfServiceAccountManagementSettings = { enabled: event.checked };
    this.formChanged = true;
  }

  isSelfServiceAccountEnabled(): boolean {
    return this.domain.selfServiceAccountManagementSettings?.enabled;
  }

  oldPasswordRequired(event) {
    this.resetPassword.oldPasswordRequired = event.checked;
    this.formChanged = true;
  }

  isOldPasswordRequired() {
    return this.domain.selfServiceAccountManagementSettings && this.resetPassword.oldPasswordRequired;
  }

  setTokenAge() {
    this.formChanged = true;
  }
}
