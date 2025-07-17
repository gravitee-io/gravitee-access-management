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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';

@Component({
  selector: 'app-login-settings',
  templateUrl: './login-settings.component.html',
  styleUrls: ['./login-settings.component.scss'],
  standalone: false,
})
export class LoginSettingsComponent implements OnChanges {
  // eslint-disable-next-line @angular-eslint/no-output-on-prefix
  @Output() onSavedLoginSettings = new EventEmitter<any>();
  @Input() loginSettings: any;
  @Input() inheritMode = false;
  @Input() readonly = false;
  @ViewChild('loginForm', { static: true }) form: any;
  formChanged = false;

  constructor() {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.loginSettings.previousValue && changes.loginSettings.currentValue) {
      this.loginSettings = changes.loginSettings.currentValue;
    }
  }

  save(): void {
    let loginSettings = { ...this.loginSettings };
    if (loginSettings.inherited) {
      loginSettings = { inherited: true };
    }
    this.onSavedLoginSettings.emit(loginSettings);
    this.formChanged = false;
  }

  enableInheritMode(event) {
    this.loginSettings.inherited = event.checked;
    this.formChanged = true;
  }

  isInherited(): boolean {
    return this.loginSettings?.inherited;
  }

  enableRegistration(event) {
    this.loginSettings.registerEnabled = event.checked;
    this.formChanged = true;
  }

  isRegistrationEnabled(): boolean {
    return this.loginSettings?.registerEnabled;
  }

  enableForgotPassword(event) {
    this.loginSettings.forgotPasswordEnabled = event.checked;
    this.formChanged = true;
  }

  isForgotPasswordEnabled() {
    return this.loginSettings?.forgotPasswordEnabled;
  }

  enableResetPasswordOnExpiration(event) {
    this.loginSettings.resetPasswordOnExpiration = event.checked;
    this.formChanged = true;
  }

  isResetPasswordOnExpiration() {
    return this.loginSettings?.resetPasswordOnExpiration;
  }

  enablePasswordless(event) {
    this.loginSettings.passwordlessEnabled = event.checked;
    this.formChanged = true;
  }

  isPasswordlessEnabled() {
    return this.loginSettings?.passwordlessEnabled;
  }

  enablePasswordlessRememberDevice(event) {
    this.loginSettings.passwordlessRememberDeviceEnabled = event.checked;
    this.formChanged = true;
  }

  isPasswordlessRememberDeviceEnabled(): boolean {
    return this.loginSettings?.passwordlessRememberDeviceEnabled;
  }

  enablePasswordlessEnforcePassword(event) {
    this.loginSettings.passwordlessEnforcePasswordEnabled = event.checked;
    this.formChanged = true;
  }

  isPasswordlessEnforcePasswordEnabled(): boolean {
    return this.loginSettings?.passwordlessEnforcePasswordEnabled;
  }

  enablePasswordlessDeviceNaming(event) {
    this.loginSettings.passwordlessDeviceNamingEnabled = event.checked;
    this.formChanged = true;
  }

  isPasswordlessDeviceNamingEnabled(): boolean {
    return this.loginSettings?.passwordlessDeviceNamingEnabled;
  }

  enableHideForm(event) {
    this.loginSettings.hideForm = event.checked;
    this.formChanged = true;
  }

  isHideFormEnabled(): boolean {
    return this.loginSettings?.hideForm;
  }

  setEnforcePasswordMaxAge(value) {
    this.loginSettings.passwordlessEnforcePasswordMaxAge = value;
    this.formChanged = true;
  }

  enableIdentifierFirstLogin(event) {
    this.loginSettings.identifierFirstEnabled = event.checked;
    if (event.checked) {
      this.loginSettings.hideForm = !event.checked;
    }
    this.formChanged = true;
  }

  isIdentifierFirstLoginEnabled(): boolean {
    return this.loginSettings?.identifierFirstEnabled;
  }

  isFormValid(): boolean {
    return this.form.disabled ? true : this.form.valid;
  }
}
