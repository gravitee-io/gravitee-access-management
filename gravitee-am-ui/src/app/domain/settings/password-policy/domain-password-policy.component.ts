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
import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { AuthService } from '../../../services/auth.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { PasswordPolicyService } from '../../../services/passwordPolicy.service';

import { DomainPasswordPolicy } from './domain-password-policy.model';

@Component({
  selector: 'password-policy',
  templateUrl: './domain-password-policy.component.html',
  styleUrls: ['./domain-password-policy.component.scss'],
})
export class DomainPasswordPolicyComponent implements OnInit {
  @ViewChild('applicationForm') form: any;
  private domainId: string;
  domain: any;
  formChanged = false;
  editMode: boolean;

  @Input() passwordPolicy: DomainPasswordPolicy;

  constructor(
    private route: ActivatedRoute,
    private authService: AuthService,
    private snackbarService: SnackbarService,
    private passwordPolicyService: PasswordPolicyService,
  ) {}

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainId = this.domain.id;
    this.passwordPolicy = {};
    this.editMode = this.authService.hasPermissions(['domain_settings_update']);
  }

  formChange(): void {
    this.formChanged = true;
  }

  setIncludeNumbers(e) {
    this.passwordPolicy.includeNumbers = e.checked;
  }

  setIncludeSpecialCharacters(e) {
    this.passwordPolicy.includeSpecialCharacters = e.checked;
  }

  setLettersInMixedValue(e) {
    this.passwordPolicy.lettersInMixedCase = e.checked;
  }

  setExcludePasswordsInDictionary(e) {
    this.passwordPolicy.excludePasswordsInDictionary = e.checked;
  }

  setExcludeUserProfileInfoInPassword(e) {
    this.passwordPolicy.excludeUserProfileInfoInPassword = e.checked;
  }

  setPasswordHistoryEnabled(e) {
    this.passwordPolicy.passwordHistoryEnabled = e.checked;
  }

  save() {
    if (
      this.passwordPolicy.passwordHistoryEnabled &&
      (!this.passwordPolicy.oldPasswords || this.passwordPolicy.oldPasswords < 1 || this.passwordPolicy.oldPasswords > 24)
    ) {
      this.snackbarService.open('Number of old passwords must be within the range [1, 24]');
      return;
    }
    if (this.passwordPolicy.minLength && this.passwordPolicy.minLength <= 0) {
      this.snackbarService.open('Min length must be greater than zero and smaller than Max length');
      return;
    }

    if (this.passwordPolicy.maxLength && this.passwordPolicy.maxLength <= 0) {
      this.snackbarService.open('Max length must be greater than zero');
      return;
    }
    this.passwordPolicyService.create(this.domainId, this.passwordPolicy).subscribe((response) => {
      this.passwordPolicy = response;
      this.form.reset(this.passwordPolicy);
      this.formChanged = false;
      this.snackbarService.open('Password settings configuration updated');
    });
  }
}
