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
import { ActivatedRoute } from '@angular/router';

import { EmailTemplateFactoryService } from '../../../../../services/email.template.factory.service';

@Component({
  selector: 'app-application-emails',
  templateUrl: './emails.component.html',
  styleUrls: ['./emails.component.scss'],
})
export class ApplicationEmailsComponent {
  emails: any[];
  application: any;
  domain: any;
  private emailTemplateFactoryService: EmailTemplateFactoryService;

  constructor(private route: ActivatedRoute, emailTemplateFactoryService: EmailTemplateFactoryService) {
    this.emailTemplateFactoryService = emailTemplateFactoryService;
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.application = this.route.snapshot.data['application'];
  }

  getEmails() {
    return this.emailTemplateFactoryService
      .findBy((email) => email.template !== 'CERTIFICATE_EXPIRATION')
      .map((email) => {
        email.enabled = email.template === 'RESET_PASSWORD' ? this.allowResetPassword() : this.applicationSettingsValid();
        return email;
      });
  }

  applicationSettingsValid() {
    if (this.application.type) {
      return this.application.type !== 'service';
    }
    if (this.application.settings && this.application.settings.oauth && this.application.settings.oauth.grantTypes) {
      return (
        this.application.settings.oauth.grantTypes.includes('authorization_code') ||
        this.application.settings.oauth.grantTypes.includes('implicit')
      );
    }
    return false;
  }

  allowResetPassword() {
    if (this.application.settings && this.application.settings.login && !this.application.settings.login.inherited) {
      return this.application.settings.login.forgotPasswordEnabled;
    }
    return this.domain.loginSettings && this.domain.loginSettings.forgotPasswordEnabled;
  }
}
