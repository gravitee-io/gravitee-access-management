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
import {Component, OnInit} from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-application-forms',
  templateUrl: './forms.component.html',
  styleUrls: ['./forms.component.scss']
})
export class ApplicationFormsComponent implements OnInit {
  forms: any[];
  domain: any;
  application: any;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.application = this.route.snapshot.data['application'];
    this.forms = this.getForms();
  }

  getForms() {
    return [
      {
        'name': 'Login',
        'description': 'Login page to authenticate users',
        'template': 'LOGIN',
        'icon': 'account_box',
        'enabled': this.applicationSettingsValid()
      },
      {
        'name': 'WebAuthn Register',
        'description': 'Passwordless page to register authenticators (devices)',
        'template': 'WEBAUTHN_REGISTER',
        'icon': 'fingerprint',
        'enabled': this.applicationSettingsValid()
      },
      {
        'name': 'WebAuthn Login',
        'description': 'Passwordless page to authenticate users',
        'template': 'WEBAUTHN_LOGIN',
        'icon': 'fingerprint',
        'enabled': this.applicationSettingsValid()
      },
      {
        'name': 'Registration',
        'description': 'Registration page to create an account',
        'template': 'REGISTRATION',
        'icon': 'person_add',
        'enabled': this.applicationSettingsValid() && this.allowRegister()
      },
      {
        'name': 'Registration confirmation',
        'description': 'Register page to confirm user account',
        'template': 'REGISTRATION_CONFIRMATION',
        'icon': 'how_to_reg',
        'enabled': this.applicationSettingsValid()
      },
      {
        'name': 'Forgot password',
        'description': 'Forgot password to recover account',
        'template': 'FORGOT_PASSWORD',
        'icon': 'lock',
        'enabled': this.applicationSettingsValid() && this.allowResetPassword()
      },
      {
        'name': 'Reset password',
        'description': 'Reset password page to make a new password',
        'template': 'RESET_PASSWORD',
        'icon': 'lock_open',
        'enabled': this.applicationSettingsValid() && this.allowResetPassword()
      },
      {
        'name': 'User consent',
        'description': 'User consent to acknowledge and accept data access',
        'template': 'OAUTH2_USER_CONSENT',
        'icon': 'playlist_add_check',
        'enabled': this.applicationSettingsValid()
      },
      {
        'name': 'MFA Enroll',
        'description': 'Multi-factor authentication settings page',
        'template': 'MFA_ENROLL',
        'icon': 'rotate_right',
        'enabled': this.applicationSettingsValid()
      },
      {
        'name': 'MFA Challenge',
        'description': 'Multi-factor authentication verify page',
        'template': 'MFA_CHALLENGE',
        'icon': 'check_circle_outline',
        'enabled': this.applicationSettingsValid()
      },
      {
        'name': 'Error',
        'description': 'Error page to display a message describing the problem',
        'template': 'ERROR',
        'icon': 'error_outline',
        'enabled': true
      }
    ]
  }

  applicationSettingsValid() {
    if (this.application.type) {
      return this.application.type !== 'service';
    }
    if (this.application.settings && this.application.settings.oauth && this.application.settings.oauth.grantTypes) {
      return this.application.settings.oauth.grantTypes.includes('authorization_code')
        || this.application.settings.oauth.grantTypes.includes('implicit');
    }
    return false;
  }

  allowRegister() {
    return this.domain.loginSettings && this.domain.loginSettings.registerEnabled;
  }

  allowResetPassword() {
    return this.domain.loginSettings && this.domain.loginSettings.forgotPasswordEnabled;
  }
}
