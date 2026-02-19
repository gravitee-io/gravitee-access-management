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
import { extractXsrfTokenAndActionResponse, performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import { clearEmails, getLastEmail } from '@utils-commands/email-commands';
import { User } from '@management-models/User';

const REDIRECT_URI = 'http://localhost:4000';

export interface ForgotPasswordContext {
  domainHrid: string;
  clientId: string;
  openIdConfiguration: {
    authorization_endpoint: string;
  };
  user: User;
}

const buildAuthParams = (clientId: string) => {
  return `?response_type=token&client_id=${clientId}&redirect_uri=${REDIRECT_URI}`;
};

const getCookieAndXsrfToken = async (authorizationEndpoint: string, clientId: string) => {
  const params = buildAuthParams(clientId);
  const authResponse = await performGet(authorizationEndpoint, params).expect(302);
  return extractXsrfTokenAndActionResponse(authResponse);
};

export const requestForgotPassword = async (context: ForgotPasswordContext, settings) => {
  const uri = `/${context.domainHrid}/forgotPassword${buildAuthParams(context.clientId)}`;
  if (settings.accountSettings.resetPasswordCustomForm && !settings.accountSettings.resetPasswordConfirmIdentity) {
    const forgotResponse = await performGet(process.env.AM_GATEWAY_URL, uri).expect(200);
    const dom = cheerio.load(forgotResponse.text);
    expect(dom('#username').length).toEqual(1);
    expect(dom('#email').length).toEqual(1);
  }

  const { headers, token } = await getCookieAndXsrfToken(context.openIdConfiguration.authorization_endpoint, context.clientId);
  const postResponse = await performFormPost(
    process.env.AM_GATEWAY_URL,
    uri,
    {
      'X-XSRF-TOKEN': token,
      email: context.user.email,
      client_id: context.clientId,
    },
    {
      Cookie: headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  if (settings.accountSettings.resetPasswordCustomForm && settings.accountSettings.resetPasswordConfirmIdentity) {
    expect(postResponse.headers['location']).toContain('forgot_password_confirm');
    const confirmResponse = await performGet(postResponse.headers['location'], '', {
      Cookie: headers['set-cookie'],
    }).expect(200);
    const dom = cheerio.load(confirmResponse.text);
    expect(dom('#username').length).toEqual(1);
    expect(dom('#email').length).toEqual(1);

    await performFormPost(
      process.env.AM_GATEWAY_URL,
      uri,
      {
        'X-XSRF-TOKEN': token,
        username: context.user.username,
        email: context.user.email,
        client_id: context.clientId,
      },
      {
        Cookie: headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);
  }
};

export const retrieveEmailLinkForReset = async (email: string) => {
  const confirmationLink = (await getLastEmail(1000, email)).extractLink();
  expect(confirmationLink).toMatch(/^https?:\/\//);
  await clearEmails(email);
  return confirmationLink;
};
