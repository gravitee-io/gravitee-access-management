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
import { Component } from '@angular/core';
import {ActivatedRoute} from "@angular/router";

@Component({
  selector: 'app-domain-forms',
  templateUrl: './forms.component.html',
  styleUrls: ['./forms.component.scss']
})
export class DomainSettingsFormsComponent {
  forms: any[];
  domain: any;

  constructor(private route: ActivatedRoute) { }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.forms = this.getForms();
  }

  getForms() {
    return [
      {
        'name': 'Login',
        'description': 'Login page to authenticate users',
        'template': 'LOGIN',
        'enabled': true
      },
      {
        'name': 'Registration',
        'description': 'Registration page to create an account',
        'template': 'REGISTRATION',
        'enabled': this.allowRegister()
      },
      {
        'name': 'Registration confirmation',
        'description': 'Register page to confirm user account',
        'template': 'REGISTRATION_CONFIRMATION',
        'enabled': true
      },
      {
        'name': 'Forgot password',
        'description': 'Forgot password to recover account',
        'template': 'FORGOT_PASSWORD',
        'enabled': this.allowResetPassword()
      },
      {
        'name': 'Reset password',
        'description': 'Reset password page to make a new password',
        'template': 'RESET_PASSWORD',
        'enabled': this.allowResetPassword()
      },
      {
        'name': 'User consent',
        'description': 'User consent to acknowledge and accept data access',
        'template': 'OAUTH2_USER_CONSENT',
        'enabled': true
      },
      {
        'name': 'Error',
        'description': 'Error page to display a message describing the problem',
        'template': 'ERROR',
        'enabled': true
      }
    ]
  }

  allowRegister() {
    return this.domain.loginSettings && this.domain.loginSettings.registerEnabled;
  }

  allowResetPassword() {
    return this.domain.loginSettings && this.domain.loginSettings.forgotPasswordEnabled;
  }
}
