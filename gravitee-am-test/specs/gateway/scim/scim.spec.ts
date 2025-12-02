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
import { afterAll, beforeAll, describe, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { createDomain, safeDeleteDomain, patchDomain, startDomain, waitForDomainSync } from '@management-commands/domain-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { extractXsrfToken, getWellKnownOpenIdConfiguration, performFormPost, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { clearEmails, getLastEmail } from '@utils-commands/email-commands';
import { getUserPage } from '@management-commands/user-management-commands';
import { uniqueName } from '@utils-commands/misc';

let mngAccessToken: string;
let scimAccessToken: string;
let domain: Domain;
let scimClient: Application;
let scimEndpoint: string;
let testUserEmail: string;
let testUserName: string;

jest.setTimeout(200000);

beforeAll(async function () {
  mngAccessToken = await requestAdminAccessToken();
  expect(mngAccessToken).toBeDefined();

  domain = await createDomain(mngAccessToken, uniqueName('scim', true), 'Domain used to test SCIM requests');
  expect(domain).toBeDefined();

  // enable SCIM
  await patchDomain(domain.id, mngAccessToken, {
    scim: {
      enabled: true,
      idpSelectionEnabled: false,
    },
  });

  const appName = uniqueName('SCIM App', true);
  const applicationDefinition: Application = {
    name: appName,
    type: 'SERVICE',
    settings: {
      oauth: {
        grantTypes: ['client_credentials'],
        scopeSettings: [
          {
            scope: 'scim',
            defaultScope: true,
          },
        ],
      },
    },
  };

  scimClient = await createApplication(domain.id, mngAccessToken, applicationDefinition);
  expect(scimClient).toBeDefined();
  // we need to call update app as the CREATE order is not taking the scope settings into account
  // NOTE: we do not override the scimClient to keep reference of the clientSecret
  await updateApplication(domain.id, mngAccessToken, applicationDefinition, scimClient.id);

  await startDomain(domain.id, mngAccessToken);

  await waitForDomainSync(domain.id, mngAccessToken);
  const openIdConfiguration = await getWellKnownOpenIdConfiguration(domain.hrid);

  // generate SCIM access token
  const response = await performPost(openIdConfiguration.body.token_endpoint, '', 'grant_type=client_credentials', {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: 'Basic ' + applicationBase64Token(scimClient),
  });
  scimAccessToken = response.body.access_token;
  scimEndpoint = process.env.AM_GATEWAY_URL + `/${domain.hrid}/scim`;
});

afterAll(async function () {
  await safeDeleteDomain(domain?.id, mngAccessToken);
});
describe('SCIM with custom parameters', () => {
  it('should create SCIM user with preRegistration and confirm registration', async () => {
    // Generate unique identifiers to avoid conflicts in parallel execution
    testUserEmail = `${uniqueName('user', true)}@user.com`;
    testUserName = uniqueName('barbara', true);

    const request = {
      schemas: ['urn:ietf:params:scim:schemas:extension:custom:2.0:User', 'urn:ietf:params:scim:schemas:core:2.0:User'],
      externalId: '70198412321242223922423',
      userName: testUserName,
      password: null,
      name: {
        formatted: 'Ms. Barbara J Jensen, III',
        familyName: 'Jensen',
        givenName: 'Barbara',
        middleName: 'Jane',
        honorificPrefix: 'Ms.',
        honorificSuffix: 'III',
      },
      displayName: 'Babs Jensen',
      nickName: 'Babs',
      emails: [
        {
          value: testUserEmail,
          type: 'work',
          primary: true,
        },
      ],
      userType: 'Employee',
      title: 'Tour Guide',
      preferredLanguage: 'en-US',
      locale: 'en-US',
      timezone: 'America/Los_Angeles',
      active: true,
      'urn:ietf:params:scim:schemas:extension:custom:2.0:User': {
        preRegistration: true,
        forceResetPassword: true,
      },
    };

    // Clear emails for this specific recipient BEFORE creating the user to avoid interference from other tests
    await clearEmails(testUserEmail);

    const response = await performPost(scimEndpoint, '/Users', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(201);
    const createdUser = response.body;

    expect(createdUser).toBeDefined();
    // Pre registered user are not enabled.
    // They have to provide a password first.
    expect(createdUser.enabled).toBeFalsy();
    expect(createdUser.registrationUserUri).not.toBeDefined();
    expect(createdUser.registrationAccessToken).not.toBeDefined();

    // Retrieve confirmation email and confirm registration in the same test
    const confirmationLink = (await getLastEmail(1000, testUserEmail)).extractLink();
    expect(confirmationLink).toBeDefined();
    await clearEmails(testUserEmail);

    // Confirm registration
    const url = new URL(confirmationLink);
    const resetPwdToken = url.searchParams.get('token');
    expect(resetPwdToken).toBeDefined();
    const baseUrlConfirmRegister = confirmationLink.substring(0, confirmationLink.indexOf('?'));

    const { headers, token: xsrfToken } = await extractXsrfToken(baseUrlConfirmRegister, '?token=' + resetPwdToken);

    const postConfirmRegistration = await performFormPost(
      baseUrlConfirmRegister,
      '',
      {
        'X-XSRF-TOKEN': xsrfToken,
        token: resetPwdToken,
        password: '#CoMpL3X-P@SsW0Rd',
      },
      {
        Cookie: headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    expect(postConfirmRegistration.headers['location']).toBeDefined();
    expect(postConfirmRegistration.headers['location']).toContain('success=registration_completed');
  });

  it('must be enabled', async () => {
    // Fetch user by email
    const users = await getUserPage(domain.id, mngAccessToken);
    const user = users.data.find((u) => u.email === testUserEmail || u.username === testUserName);
    expect(user).toBeDefined();
    expect(user.enabled).toBeTruthy();
  });

  it('must contain forceResetPassword', async () => {
    // Fetch user by email
    const users = await getUserPage(domain.id, mngAccessToken);
    const user = users.data.find((u) => u.email === testUserEmail || u.username === testUserName);
    expect(user).toBeDefined();
    expect(user.forceResetPassword).toBeTruthy();
  });

  it('should send email with client information when client is specified', async () => {
    // Generate unique identifiers
    const userEmail = `${uniqueName('user-client', true)}@user.com`;
    const userName = uniqueName('john-client', true);

    const request = {
      schemas: ['urn:ietf:params:scim:schemas:extension:custom:2.0:User', 'urn:ietf:params:scim:schemas:core:2.0:User'],
      externalId: '70198412321242223922424',
      userName: userName,
      password: null,
      name: {
        formatted: 'Mr. John Doe',
        familyName: 'Doe',
        givenName: 'John',
      },
      displayName: 'John Doe',
      emails: [
        {
          value: userEmail,
          type: 'work',
          primary: true,
        },
      ],
      active: true,
      'urn:ietf:params:scim:schemas:extension:custom:2.0:User': {
        preRegistration: true,
        client: scimClient.id,
      },
    };

    // Clear emails for this specific recipient
    await clearEmails(userEmail);

    const response = await performPost(scimEndpoint, '/Users', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(201);
    const createdUser = response.body;

    expect(createdUser).toBeDefined();
    expect(createdUser.enabled).toBeFalsy();

    // Retrieve confirmation email
    const email = await getLastEmail(1000, userEmail);
    const confirmationLink = email.extractLink();
    expect(confirmationLink).toBeDefined();

    // Verify email contains client_id parameter
    const url = new URL(confirmationLink);
    const clientIdParam = url.searchParams.get('client_id');
    expect(clientIdParam).toBeDefined();
    expect(clientIdParam).toBe(scimClient.settings.oauth.clientId);

    // Verify user was created with client
    const users = await getUserPage(domain.id, mngAccessToken);
    const user = users.data.find((u) => u.email === userEmail);
    expect(user).toBeDefined();
    expect(user.client).toBe(scimClient.id);

    await clearEmails(userEmail);
  });
});
