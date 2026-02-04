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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { EmailTemplateFactoryService } from '../../../../../services/email.template.factory.service';
import { DomainStoreService } from '../../../../../stores/domain.store';
import { PlatformCapabilitiesService } from '../../../../../services/platform-capabilities.service';

@Component({
  selector: 'app-application-emails',
  templateUrl: './emails.component.html',
  styleUrls: ['./emails.component.scss'],
  standalone: false,
})
export class ApplicationEmailsComponent implements OnInit {
  emails: any[];
  application: any;
  domain: any;
  private emailTemplateFactoryService: EmailTemplateFactoryService;

  private magicLinkDeployed = true;

  constructor(
    private route: ActivatedRoute,
    private domainStore: DomainStoreService,
    emailTemplateFactoryService: EmailTemplateFactoryService,
    private platformCapabilitiesService: PlatformCapabilitiesService,
  ) {
    this.emailTemplateFactoryService = emailTemplateFactoryService;
  }

  ngOnInit() {
    this.domain = deepClone(this.domainStore.current);
    this.application = this.route.snapshot.data['application'];

    this.platformCapabilitiesService.get().subscribe((caps) => {
      this.magicLinkDeployed = !!caps?.magicLinkAuthenticatorDeployed;
    });
  }

  getEmails() {
    return this.emailTemplateFactoryService
      .findBy((email) => email.template !== 'CERTIFICATE_EXPIRATION')
      .map((email) => {
        if (email.template === 'RESET_PASSWORD') {
          email.enabled = this.allowResetPassword();
          return email;
        }

        if (email.template === 'MAGIC_LINK') {
          email.enabled = this.allowMagicLink();
          return email;
        }

        email.enabled = this.applicationSettingsValid();
        return email;
      });
  }

  private allowMagicLink(): boolean {
    return this.magicLinkDeployed;
  }

  applicationSettingsValid() {
    if (this.application.type) {
      return this.application.type !== 'service';
    }
    if (this.application.settings?.oauth?.grantTypes) {
      return (
        this.application.settings.oauth.grantTypes.includes('authorization_code') ||
        this.application.settings.oauth.grantTypes.includes('implicit')
      );
    }
    return false;
  }

  allowResetPassword(): boolean {
    if (this.application.settings?.login && !this.application.settings.login.inherited) {
      return this.application.settings.login.forgotPasswordEnabled;
    }
    return this.domain.loginSettings?.forgotPasswordEnabled;
  }
}
