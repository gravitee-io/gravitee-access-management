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
import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { AuthService } from '../../../services/auth.service';
import { DomainService } from '../../../services/domain.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { DialogService } from '../../../services/dialog.service';

@Component({
  selector: 'password-policy',
  templateUrl: './domain-password-policy.component.html',
  styleUrls: ['./domain-password-policy.component.scss'],
})
export class DomainPasswordPolicyComponent implements OnInit {
  @ViewChild('applicationForm', { static: true }) form: any;
  private domainId: string;
  domain: any;
  formChanged = false;
  editMode: boolean;
  passwordSettings: any;
  minLength: number;
  maxLength: number;
  includeNumbers: boolean;
  includeSpecialCharacters: boolean;
  lettersInMixedCase: boolean;
  maxConsecutiveLetters = 0;
  excludePasswordsInDictionary: boolean;
  excludeUserProfileInfoInPassword: boolean;
  expiryDuration: number;
  passwordHistoryEnabled: boolean;
  oldPasswords: number;

  constructor(
    private route: ActivatedRoute,
    private authService: AuthService,
    private snackbarService: SnackbarService,
    private domainService: DomainService,
    private dialogService: DialogService,
  ) {}

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainId = this.domain.id;
    this.passwordSettings = this.domain.passwordSettings;
    if (this.passwordSettings != null) {
      this.minLength = this.passwordSettings.minLength;
      this.maxLength = this.passwordSettings.maxLength;
      this.includeNumbers = this.passwordSettings.includeNumbers;
      this.includeSpecialCharacters = this.passwordSettings.includeSpecialCharacters;
      this.lettersInMixedCase = this.passwordSettings.lettersInMixedCase;
      if (this.passwordSettings.maxConsecutiveLetters) {
        this.maxConsecutiveLetters = this.passwordSettings.maxConsecutiveLetters;
      }
      this.excludePasswordsInDictionary = this.passwordSettings.excludePasswordsInDictionary;
      this.excludeUserProfileInfoInPassword = this.passwordSettings.excludeUserProfileInfoInPassword;
      this.expiryDuration = this.passwordSettings.expiryDuration;
      this.passwordHistoryEnabled = this.passwordSettings.passwordHistoryEnabled;
      this.oldPasswords = this.passwordSettings.oldPasswords;
    }
    this.editMode = this.authService.hasPermissions(['application_settings_update']);
  }

  formChange(): void {
    this.formChanged = true;
  }

  setIncludeNumbers(e) {
    this.includeNumbers = e.checked;
  }

  setIncludeSpecialCharacters(e) {
    this.includeSpecialCharacters = e.checked;
  }

  setLettersInMixedValue(e) {
    this.lettersInMixedCase = e.checked;
  }

  setExcludePasswordsInDictionary(e) {
    this.excludePasswordsInDictionary = e.checked;
  }

  setExcludeUserProfileInfoInPassword(e) {
    this.excludeUserProfileInfoInPassword = e.checked;
  }

  setPasswordHistoryEnabled(e) {
    this.passwordHistoryEnabled = e.checked;
  }

  update() {
    if (this.passwordHistoryEnabled && (!this.oldPasswords || this.oldPasswords < 1 || this.oldPasswords > 24)) {
      this.snackbarService.open('Number of old passwords must be within the range [1, 24]');
      return;
    }

    const data: any = {};

    if (this.minLength && this.minLength <= 0) {
      this.snackbarService.open('Min length must be greater than zero');
      return;
    }

    if (this.maxLength && this.maxLength <= 0) {
      this.snackbarService.open('Max length must be greater than zero');
      return;
    }
    data.passwordSettings = {
      inherited: false,
      minLength: this.minLength,
      maxLength: this.maxLength,
      includeNumbers: this.includeNumbers,
      includeSpecialCharacters: this.includeSpecialCharacters,
      lettersInMixedCase: this.lettersInMixedCase,
      maxConsecutiveLetters: this.maxConsecutiveLetters > 0 ? this.maxConsecutiveLetters : undefined,
      excludePasswordsInDictionary: this.excludePasswordsInDictionary,
      excludeUserProfileInfoInPassword: this.excludeUserProfileInfoInPassword,
      expiryDuration: this.expiryDuration,
      passwordHistoryEnabled: this.passwordHistoryEnabled,
      oldPasswords: this.oldPasswords,
    };
    this.domainService.patchPasswordSettings(this.domainId, data).subscribe((response) => {
      this.passwordSettings = response.passwordSettings;
      this.domain['passwordSettings'] = this.passwordSettings;
      this.form.reset(this.passwordSettings);
      this.formChanged = false;
      this.snackbarService.open('Password settings configuration updated');
    });
  }
  confirmResetDialog(event: any): void {
    event.preventDefault();
    this.dialogService
      .confirm('Reset password policy', 'Are you sure you want to reset your password policy settings and use the default one?')
      .subscribe((res) => {
        if (res) {
          this.reset();
        }
      });
  }
  reset(): void {
    this.domainService.patchPasswordSettings(this.domainId, { passwordSettings: { inherited: true } }).subscribe(() => {
      this.includeNumbers = false;
      this.includeSpecialCharacters = false;
      this.lettersInMixedCase = false;
      this.excludePasswordsInDictionary = false;
      this.excludeUserProfileInfoInPassword = false;
      this.passwordHistoryEnabled = false;
      this.maxConsecutiveLetters = undefined;
      this.passwordSettings = {};
      this.domain['passwordSettings'] = this.passwordSettings;
      this.form.reset(this.passwordSettings);
      this.formChanged = false;
      this.snackbarService.open('Password policy settings reset');
    });
  }
}
