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
import { Injectable } from '@angular/core';

@Injectable()
export class EmailTemplateFactoryService {
  private static readonly DEFAULT_EXPIRATION_SECONDS_10_MINUTES = 600;
  private static readonly DEFAULT_EXPIRATION_SECONDS_1_DAY = 86400;
  private static readonly DEFAULT_EXPIRATION_SECONDS_7_DAYS = 7 * EmailTemplateFactoryService.DEFAULT_EXPIRATION_SECONDS_1_DAY;
  private static readonly DEFAULT_EXPIRATION_SECONDS_15_MINUTES = 900;

  private static readonly emailTemplates = {
    registration_confirmation: {
      name: 'Registration confirmation',
      description: 'Registration email to confirm user account',
      template: 'REGISTRATION_CONFIRMATION',
      icon: 'how_to_reg',
      defaultExpirationSeconds: EmailTemplateFactoryService.DEFAULT_EXPIRATION_SECONDS_1_DAY,
    },
    reset_password: {
      name: 'Reset password',
      description: 'Reset password email to ask for a new one',
      template: 'RESET_PASSWORD',
      icon: 'lock_open',
      defaultExpirationSeconds: EmailTemplateFactoryService.DEFAULT_EXPIRATION_SECONDS_1_DAY,
    },
    blocked_account: {
      name: 'Blocked account',
      description: 'Recover account after it has been blocked',
      template: 'BLOCKED_ACCOUNT',
      icon: 'person_add_disabled',
      defaultExpirationSeconds: EmailTemplateFactoryService.DEFAULT_EXPIRATION_SECONDS_1_DAY,
    },
    mfa_challenge: {
      name: 'MFA Challenge',
      description: 'Multi-factor authentication verification code',
      template: 'MFA_CHALLENGE',
      icon: 'check_circle_outline',
      defaultExpirationSeconds: EmailTemplateFactoryService.DEFAULT_EXPIRATION_SECONDS_10_MINUTES,
    },
    certificate_expiration: {
      name: 'Certificate Expiration',
      description: 'Email notification about Certificate expiration',
      template: 'CERTIFICATE_EXPIRATION',
      icon: 'notifications',
      defaultExpirationSeconds: EmailTemplateFactoryService.DEFAULT_EXPIRATION_SECONDS_1_DAY,
    },
    client_secret_expiration: {
      name: 'Client Secret Expiration',
      description: 'Email notification about Client Secret expiration',
      template: 'CLIENT_SECRET_EXPIRATION',
      icon: 'notifications',
      defaultExpirationSeconds: EmailTemplateFactoryService.DEFAULT_EXPIRATION_SECONDS_1_DAY,
    },
    registration_verify: {
      name: 'Account registered verification',
      description: 'Email notification about Account verification after registration',
      template: 'REGISTRATION_VERIFY',
      icon: 'how_to_reg',
      defaultExpirationSeconds: EmailTemplateFactoryService.DEFAULT_EXPIRATION_SECONDS_7_DAYS,
    },
    magic_link: {
      name: 'Magic link',
      description: 'Email notification containing a magic link',
      template: 'MAGIC_LINK',
      icon: 'notifications',
      defaultExpirationSeconds: EmailTemplateFactoryService.DEFAULT_EXPIRATION_SECONDS_15_MINUTES,
    },
  };

  findAll(): any[] {
    return Object.values(EmailTemplateFactoryService.emailTemplates).map((template) => {
      return { ...template };
    });
  }

  findBy(predicate: (templateName: any) => boolean): any[] {
    return this.findAll().filter(predicate);
  }
  findByName(templateName: string): any {
    return { ...EmailTemplateFactoryService.emailTemplates[templateName.toLowerCase()] };
  }
}
