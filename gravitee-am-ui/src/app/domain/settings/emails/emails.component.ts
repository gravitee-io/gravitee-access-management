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

import { EmailTemplateFactoryService } from '../../../services/email.template.factory.service';

@Component({
  selector: 'app-domain-emails',
  templateUrl: './emails.component.html',
  styleUrls: ['./emails.component.scss'],
  standalone: false,
})
export class DomainSettingsEmailsComponent implements OnInit {
  domain: any;
  private emailTemplateFactoryService: EmailTemplateFactoryService;

  constructor(
    private route: ActivatedRoute,
    emailTemplateFactoryService: EmailTemplateFactoryService,
  ) {
    this.emailTemplateFactoryService = emailTemplateFactoryService;
  }

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
  }

  getEmails() {
    return this.emailTemplateFactoryService.findAll().map((email) => {
      if (email.template === 'RESET_PASSWORD') {
        email.enabled = this.allowResetPassword();
        return email;
      }

      if (email.template === 'MAGIC_LINK') {
        email.enabled = this.allowMagicLink();
        return email;
      }

      email.enabled = true;
      return email;
    });
  }

  private allowMagicLink(): boolean {
    return this.domain.loginSettings?.magicLinkAuthEnabled;
  }

  private allowResetPassword(): boolean {
    return this.domain.loginSettings?.forgotPasswordEnabled;
  }
}
