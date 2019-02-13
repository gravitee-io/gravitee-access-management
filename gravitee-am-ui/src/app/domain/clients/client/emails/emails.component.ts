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
  selector: 'app-client-emails',
  templateUrl: './emails.component.html',
  styleUrls: ['./emails.component.scss']
})
export class ClientEmailsComponent {
  emails: any[];
  client: any;
  domain: any;

  constructor(private route: ActivatedRoute) { }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.client = this.route.snapshot.parent.data['client'];
    this.emails = this.getEmails();
  }

  getEmails() {
    return [
      {
        'name': 'Registration confirmation',
        'description': 'Registration email to confirm user account',
        'template': 'REGISTRATION_CONFIRMATION',
        'enabled': this.clientSettingsValid()
      },
      {
        'name': 'Reset password',
        'description': 'Reset password email to ask for a new one',
        'template': 'RESET_PASSWORD',
        'enabled': this.clientSettingsValid() && this.allowResetPassword()
      }
    ]
  }

  clientSettingsValid() {
    let authorizedGrantTypes: string[] = this.client.authorizedGrantTypes
    return authorizedGrantTypes && (authorizedGrantTypes.includes('authorization_code') || authorizedGrantTypes.includes("implicit"));
  }

  allowResetPassword() {
    return this.domain.loginSettings && this.domain.loginSettings.forgotPasswordEnabled;
  }
}
