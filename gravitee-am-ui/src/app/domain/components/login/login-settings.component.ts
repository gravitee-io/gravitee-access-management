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
import {Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';

@Component({
  selector: 'app-login-settings',
  templateUrl: './login-settings.component.html',
  styleUrls: ['./login-settings.component.scss']
})
export class LoginSettingsComponent implements OnInit, OnChanges {
  @Output() onSavedLoginSettings = new EventEmitter<any>();
  @Input() loginSettings: any;
  @Input() inheritMode = false;
  @Input() readonly = false;
  @ViewChild('loginForm', { static: true }) form: any;
  formChanged = false;
  private domainId: string;

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.parent.parent.params['domainId']
        ? this.route.snapshot.parent.parent.params['domainId']
        : this.route.snapshot.parent.parent.parent.params['domainId'];
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.loginSettings.previousValue && changes.loginSettings.currentValue) {
      this.loginSettings = changes.loginSettings.currentValue;
    }
  }

  save() {
    let loginSettings = Object.assign({}, this.loginSettings);
    if (loginSettings.inherited) {
      loginSettings = { 'inherited' : true };
    }
    this.onSavedLoginSettings.emit(loginSettings);
    this.formChanged = false;
  }

  enableInheritMode(event) {
    this.loginSettings.inherited = event.checked;
    this.formChanged = true;
  }

  isInherited() {
    return this.loginSettings && this.loginSettings.inherited;
  }

  enableRegistration(event) {
    this.loginSettings.registerEnabled = event.checked;
    this.formChanged = true;
  }

  isRegistrationEnabled() {
    return this.loginSettings && this.loginSettings.registerEnabled;
  }


  enableForgotPassword(event) {
    this.loginSettings.forgotPasswordEnabled = event.checked;
    this.formChanged = true;
  }

  isForgotPasswordEnabled() {
    return this.loginSettings && this.loginSettings.forgotPasswordEnabled;
  }

  enablePasswordless(event) {
    this.loginSettings.passwordlessEnabled = event.checked;
    this.formChanged = true;
  }

  isPasswordlessEnabled() {
    return this.loginSettings && this.loginSettings.passwordlessEnabled;
  }
}
