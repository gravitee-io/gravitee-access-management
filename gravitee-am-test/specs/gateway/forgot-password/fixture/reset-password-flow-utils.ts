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
import { expect } from '@jest/globals';
import cheerio from 'cheerio';
import { performFormPost, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { waitFor } from '@management-commands/domain-management-commands';
import { User } from '@management-models/User';

export interface ResetPasswordContext {
  openIdConfiguration: {
    introspection_endpoint: string;
  };
  application: any;
  userSessionToken: string;
  user: User;
  resetPasswordFailed: string;
  invalidPasswordValue: string;
}

const getResetPasswordForm = async (confirmationLink: string) => {
  const confirmationLinkResponse = await performGet(confirmationLink);
  const headers = confirmationLinkResponse.headers['set-cookie'];
  const dom = cheerio.load(confirmationLinkResponse.text);
  const action = dom('form').attr('action');
  const xsrfToken = dom('[name=X-XSRF-TOKEN]').val();
  const resetPwdToken = dom('[name=token]').val();
  return { headers, action, xsrfToken, resetPwdToken };
};

export const resetPassword = async (
  confirmationLink: string,
  password: string,
  expectedMessage: string,
  setting,
  context: ResetPasswordContext,
) => {
  const { headers, action, xsrfToken, resetPwdToken } = await getResetPasswordForm(confirmationLink);
  const resetResponse = await performFormPost(
    action,
    '',
    {
      'X-XSRF-TOKEN': xsrfToken,
      token: resetPwdToken,
      password: password,
      'confirm-password': password,
    },
    {
      Cookie: headers,
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
  expect(resetResponse.headers['location']).toContain(expectedMessage);
  if (expectedMessage !== context.resetPasswordFailed && expectedMessage !== context.invalidPasswordValue) {
    if (setting.accountSettings.autoLoginAfterResetPassword) {
      const sessionJwt = resetResponse.headers['set-cookie'][0].split('=')[1].split(';')[0].split('.')[1];
      const decoded = JSON.parse(atob(sessionJwt));
      expect(decoded.userId).toEqual(context.user.id);
    }
    await waitFor(8000);
    const response = await performPost(context.openIdConfiguration.introspection_endpoint, '', `token=${context.userSessionToken}`, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(context.application),
    }).expect(200);
    if (setting.accountSettings.resetPasswordInvalidateTokens) {
      expect(response.body.active).toBeFalsy();
    } else {
      expect(response.body.active).toBeTruthy();
    }
  }
};
