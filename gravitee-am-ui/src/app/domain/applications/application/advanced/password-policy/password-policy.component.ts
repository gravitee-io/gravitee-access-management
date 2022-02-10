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
import {Component, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../../../services/snackbar.service';
import {ApplicationService} from '../../../../../services/application.service';
import {AuthService} from '../../../../../services/auth.service';

@Component({
  selector: 'password-policy',
  templateUrl: './password-policy.component.html',
  styleUrls: ['./password-policy.component.scss']
})
export class PasswordPolicyComponent implements OnInit {
  @ViewChild('applicationForm', {static: true}) form: any;
  private domainId: string;
  domain: any;
  application: any;
  formChanged = false;
  editMode: boolean;

  passwordSettings: any = {};
  minLength: number;
  maxLength: number;
  includeNumbers: boolean;
  includeSpecialCharacters: boolean;
  lettersInMixedCase: boolean;
  inherited: boolean;
  maxConsecutiveLetters: number;
  excludePasswordsInDictionary: boolean;
  excludeUserProfileInfoInPassword: boolean;
  expiryDuration: number;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private snackbarService: SnackbarService,
              private applicationService: ApplicationService,
              private authService: AuthService) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainId = this.domain.id;
    this.application = this.route.snapshot.data['application'];
    this.passwordSettings = this.application.settings.passwordSettings;
    if (this.passwordSettings == null) {
      this.inherited = true;
    } else {
      this.inherited = this.passwordSettings.inherited;
      this.minLength = this.passwordSettings.minLength;
      this.maxLength = this.passwordSettings.maxLength;
      this.includeNumbers = this.passwordSettings.includeNumbers;
      this.includeSpecialCharacters = this.passwordSettings.includeSpecialCharacters;
      this.lettersInMixedCase = this.passwordSettings.lettersInMixedCase;
      this.maxConsecutiveLetters = this.passwordSettings.maxConsecutiveLetters;
      this.excludePasswordsInDictionary = this.passwordSettings.excludePasswordsInDictionary;
      this.excludeUserProfileInfoInPassword = this.passwordSettings.excludeUserProfileInfoInPassword;
      this.expiryDuration = this.passwordSettings.expiryDuration;
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

  setInheritConfigurationValue(e) {
    this.inherited = e.checked;
  }

  setExcludePasswordsInDictionary(e) {
    this.excludePasswordsInDictionary = e.checked;
  }

  setExcludeUserProfileInfoInPassword(e) {
    this.excludeUserProfileInfoInPassword = e.checked;
  }

  update() {
    const data: any = {};
    data.settings = {};
    data.settings.passwordSettings = {
      'inherited': this.inherited,
      'minLength': this.minLength,
      'maxLength': this.maxLength,
      'includeNumbers': this.includeNumbers,
      'includeSpecialCharacters': this.includeSpecialCharacters,
      'lettersInMixedCase': this.lettersInMixedCase,
      'maxConsecutiveLetters': this.maxConsecutiveLetters,
      'excludePasswordsInDictionary': this.excludePasswordsInDictionary,
      'excludeUserProfileInfoInPassword': this.excludeUserProfileInfoInPassword,
      'expiryDuration': this.expiryDuration
    };
    this.applicationService.patch(this.domainId, this.application.id, data).subscribe(response => {
      this.formChanged = false;
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { 'reload': true }});
    });
  }
}
