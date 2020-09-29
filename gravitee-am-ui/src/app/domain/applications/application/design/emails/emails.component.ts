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
  selector: 'app-application-emails',
  templateUrl: './emails.component.html',
  styleUrls: ['./emails.component.scss']
})
export class ApplicationEmailsComponent {
  emails: any[];
  application: any;
  domain: any;

  constructor(private route: ActivatedRoute) { }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.application = this.route.snapshot.data['application'];
    this.emails = this.getEmails();
  }

  getEmails() {
    return [
      {
        'name': 'Registration confirmation',
        'description': 'Registration email to confirm user account',
        'template': 'REGISTRATION_CONFIRMATION',
        'icon' : 'how_to_reg',
        'enabled': this.applicationSettingsValid()
      },
      {
        'name': 'Reset password',
        'description': 'Reset password email to ask for a new one',
        'template': 'RESET_PASSWORD',
        'icon': 'lock_open',
        'enabled': this.applicationSettingsValid() && this.allowResetPassword()
      },
      {
        'name': 'Blocked account',
        'description': 'Recover account after it has been blocked',
        'template': 'BLOCKED_ACCOUNT',
        'icon': 'person_add_disabled',
        'enabled': this.applicationSettingsValid()
      }
    ]
  }

  applicationSettingsValid() {
    if (this.application.type) {
      return this.application.type !== 'service';
    }
    if (this.application.settings && this.application.settings.oauth && this.application.settings.oauth.grantTypes) {
      return this.application.settings.oauth.grantTypes.includes('authorization_code') || this.application.settings.oauth.grantTypes.includes("implicit");
    }
    return false;
  }

  allowResetPassword() {
    return this.domain.loginSettings && this.domain.loginSettings.forgotPasswordEnabled;
  }
}
