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
import {AuthService} from "../../../services/auth.service";
import {DomainService} from "../../../services/domain.service";
import {SnackbarService} from "../../../services/snackbar.service";


@Component({
  selector: 'password-policy',
  templateUrl: './domain-password-policy.component.html',
  styleUrls: ['./domain-password-policy.component.scss']
})
export class DomainPasswordPolicyComponent implements OnInit {
  @ViewChild('applicationForm', {static: true}) form: any;
  private domainId: string;
  domain: any;
  formChanged = false;
  editMode: boolean;
  passwordSettings: any;
  minLength: string;
  includeNumbers: boolean;
  includeSpecialCharacters: boolean;
  lettersInMixedCase: boolean;
  maxConsecutiveLetters: number;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private authService: AuthService,
              private snackbarService: SnackbarService,
              private domainService: DomainService
  ) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.domainId = this.domain.id;
    this.passwordSettings = this.domain.passwordSettings;
    if (this.passwordSettings != null) {
      this.minLength = this.passwordSettings.minLength;
      this.includeNumbers = this.passwordSettings.includeNumbers;
      this.includeSpecialCharacters = this.passwordSettings.includeSpecialCharacters;
      this.lettersInMixedCase = this.passwordSettings.lettersInMixedCase;
      this.maxConsecutiveLetters = this.passwordSettings.maxConsecutiveLetters;
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

  update() {
    const data: any = {};
    data.passwordSettings = {
      'minLength': this.minLength,
      'includeNumbers': this.includeNumbers,
      'includeSpecialCharacters': this.includeSpecialCharacters,
      'lettersInMixedCase': this.lettersInMixedCase,
      'maxConsecutiveLetters': this.maxConsecutiveLetters,
    };
    this.domainService.patchPasswordSettings(this.domainId, data).subscribe(data => {
      this.domain = data;
      this.form.reset(data.passwordSettings);
      this.formChanged = false;
      this.snackbarService.open('Password settings configuration updated');
    });
  }
}
