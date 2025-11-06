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
import fetch from 'cross-fetch';
import * as faker from 'faker';
import { afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, deleteDomain, setupDomainForTest, startDomain } from '@management-commands/domain-management-commands';
import { preview } from '@management-commands/form-management-commands';
import { uniqueName } from '@utils-commands/misc';

const path = require('path');
const fs = require('fs');

global.fetch = fetch;

let accessToken;
let domain;

let customDraftTheme = {
  name: faker.address.country(),
  locale: faker.address.countryCode(),
  primaryTextColorHex: '#FFFFFF',
  primaryButtonColorHex: '#000000',
  secondaryTextColorHex: '#FFFFFF',
  secondaryButtonColorHex: '#000000',
};

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await setupDomainForTest(uniqueName('domain-themes-preview'), { accessToken, waitForStart: false }).then((it) => it.domain);
});

async function testRequestPreview(template: String, content: String, theme?: any) {
  const previewResult = await preview(domain.id, accessToken, {
    type: 'FORM',
    template: template,
    content: content,
    theme: theme,
  });
  expect(previewResult).toBeDefined();
  return previewResult;
}

const TEMPLATE_DIRECTORY =
  '../../../gravitee-am-gateway/gravitee-am-gateway-handler/gravitee-am-gateway-handler-core/src/main/resources/webroot/views/';

const templateTest = (description: string, template: string) => {
  describe(description, () => {
    const file = path.join(__dirname, TEMPLATE_DIRECTORY, template + '.html');
    const content = fs.readFileSync(file, 'utf8', function (err: any, data: any) {
      return data;
    });

    requestPreview(template, content);
  });
};

describe('Testing preview form api...', () => {
  templateTest('With LOGIN template', 'login');
  templateTest('With ERROR template', 'error');
  templateTest('With RESET_PASSWORD template', 'reset_password');
  templateTest('With OAUTH2_USER_CONSENT template', 'oauth2_user_consent');
  templateTest('With MFA_CHALLENGE_ALTERNATIVES template', 'mfa_challenge_alternatives');
  templateTest('With MFA_RECOVERY_CODE template', 'mfa_recovery_code');
  templateTest('With WEBAUTHN_REGISTER template', 'webauthn_register');
  templateTest('With WEBAUTHN_LOGIN template', 'webauthn_login');
  templateTest('With IDENTIFIER_FIRST_LOGIN template', 'identifier_first_login');
  templateTest('With MFA_CHALLENGE template', 'mfa_challenge');
  templateTest('With MFA_ENROLL template', 'mfa_enroll');
  templateTest('With FORGOT_PASSWORD template', 'forgot_password');
  templateTest('With REGISTRATION_CONFIRMATION template', 'registration_confirmation');
  templateTest('With REGISTRATION template', 'registration');
});

describe('Testing invalid preview form api...', () => {
  describe('With unknown variable into the template', () => {
    it('must return an error', async () => {
      await expect(
        testRequestPreview(
          'login',
          `<!DOCTYPE html>
                <html lang="en" xmlns:th="http://www.thymeleaf.org">
                <head>
                    <!-- Favicon and touch icons -->
                    <link rel="shortcut icon" th:href="\${unknowntheme.faviconUrl}">
                </head>
                <body>
                <div class="container">
                    <img class="logo" th:src="\${unknown} ?: @{assets/images/gravitee-logo.svg}">
                </div>
                </body>
                </html>
                    `,
        )
      ).rejects.toMatchObject({
        response: { status: 400 }
      });
    });
  });

  describe('With unknown template', () => {
    it('must return an error', async () => {
      await expect(
        testRequestPreview('unknown', 'content')
      ).rejects.toMatchObject({
        response: { status: 400 }
      });
    });
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await deleteDomain(domain.id, accessToken);
  }
});

function requestPreview(template: String, content: String) {
  it('must render the form with default theme', async () => {
    let preview = await testRequestPreview(template, content);
    expect(preview).toBeDefined;
    expect(preview['content']).toBeDefined;
    expect(preview['content']).toContain('#6A4FF7');
  });

  it('must render the form with draft theme', async () => {
    let preview = await testRequestPreview(template, content, customDraftTheme);
    expect(preview).toBeDefined;
    expect(preview['content']).toBeDefined;
    expect(preview['content']).toContain('#FFFFFF');
  });
}
