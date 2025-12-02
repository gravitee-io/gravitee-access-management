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
import {afterAll, beforeAll, describe, expect, jest} from '@jest/globals';
import {requestAdminAccessToken} from '@management-commands/token-management-commands';
import {Domain} from '@management-models/Domain';
import {Application} from '@management-models/Application';
import {
  createDomain,
  deleteDomain,
  patchDomain,
  startDomain,
  waitForDomainSync
} from '@management-commands/domain-management-commands';
import {createApplication, updateApplication} from '@management-commands/application-management-commands';
import {
  extractXsrfToken,
  getWellKnownOpenIdConfiguration,
  performFormPost,
  performPost
} from '@gateway-commands/oauth-oidc-commands';
import {applicationBase64Token} from '@gateway-commands/utils';
import {clearEmails, getLastEmail} from '@utils-commands/email-commands';
import {getUser, getUserPage} from '@management-commands/user-management-commands';

let mngAccessToken: string;
let scimAccessToken: string;
let domain: Domain;
let scimClient: Application;
let scimEndpoint: string;
let confirmationLink: string;
let createdUser;

jest.setTimeout(200000);

beforeAll(async function () {
  mngAccessToken = await requestAdminAccessToken();
  expect(mngAccessToken).toBeDefined();

  domain = await createDomain(mngAccessToken, 'scim', 'Domain used to test SCIM requests');
  expect(domain).toBeDefined();

  // enable SCIM
  await patchDomain(domain.id, mngAccessToken, {
    scim: {
      enabled: true,
      idpSelectionEnabled: false,
    },
  });

  const applicationDefinition: Application = {
    name: 'SCIM App',
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

  await waitForDomainSync();
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
  if (domain) {
    await deleteDomain(domain.id, mngAccessToken);
  }
});

describe('SCIM with custom parameters', () => {

  it('should create SCIM user with preRegistration and confirm registration', async () => {

    const request = {
      schemas: [
        'urn:ietf:params:scim:schemas:extension:custom:2.0:User',
        'urn:ietf:params:scim:schemas:core:2.0:User'
      ],
      externalId: '70198412321242223922423',
      userName: 'barbara',
      password: null,
      name: {
        formatted: 'Ms. Barbara J Jensen, III',
        familyName: 'Jensen',
        givenName: 'Barbara',
        middleName: 'Jane',
        honorificPrefix: 'Ms.',
        honorificSuffix: 'III'
      },
      displayName: 'Babs Jensen',
      nickName: 'Babs',
      emails: [
        {
          value: 'user@user.com',
          type: 'work',
          primary: true
        }
      ],
      userType: 'Employee',
      title: 'Tour Guide',
      preferredLanguage: 'en-US',
      locale: 'en-US',
      timezone: 'America/Los_Angeles',
      active: true,
      'urn:ietf:params:scim:schemas:extension:custom:2.0:User': {
        preRegistration: true
      }
    };


    const response = await performPost(scimEndpoint, '/Users', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(201);
    createdUser = response.body;

    expect(createdUser).toBeDefined();
    // Pre registered user are not enabled.
    // They have to provide a password first.
    expect(createdUser.enabled).toBeFalsy();
    expect(createdUser.registrationUserUri).not.toBeDefined();
    expect(createdUser.registrationAccessToken).not.toBeDefined();

  });

  it('must received an email', async () => {
    confirmationLink = (await getLastEmail()).extractLink();
    expect(confirmationLink).toBeDefined();
    await clearEmails();
  });

  it('must confirm the registration by providing a password', async () => {
    const url = new URL(confirmationLink);
    const resetPwdToken = url.searchParams.get('token');
    const baseUrlConfirmRegister = confirmationLink.substring(0, confirmationLink.indexOf('?'));

    const {headers, token: xsrfToken} = await extractXsrfToken(baseUrlConfirmRegister, '?token=' + resetPwdToken);

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
    let user = await getUser(domain.id, mngAccessToken, createdUser.id);
    expect(user).toBeDefined();
    // Pre registered user are not enabled.
    // They have to provide a password first.
    expect(user.enabled).toBeTruthy();
  });

  it('should send email with client information when client is specified', async () => {

    const request = {
      schemas: ['urn:ietf:params:scim:schemas:extension:custom:2.0:User', 'urn:ietf:params:scim:schemas:core:2.0:User'],
      externalId: '70198412321242223922424',
      userName: 'barbara1',
      password: null,
      name: {
        formatted: 'Mr. John Doe',
        familyName: 'Doe',
        givenName: 'John',
      },
      displayName: 'John Doe',
      emails: [
        {
          value: 'barbara@user.com',
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
    await clearEmails();

    const response = await performPost(scimEndpoint, '/Users', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(201);
    const createdUser = response.body;

    expect(createdUser).toBeDefined();
    expect(createdUser.enabled).toBeFalsy();

    // Retrieve confirmation email
    const email = await getLastEmail(1000);
    const confirmationLink = email.extractLink();
    expect(confirmationLink).toBeDefined();

    // Verify email contains client_id parameter
    const url = new URL(confirmationLink);
    const clientIdParam = url.searchParams.get('client_id');
    expect(clientIdParam).toBeDefined();
    expect(clientIdParam).toBe(scimClient.settings.oauth.clientId);

    // Verify user was created with client
    const users = await getUserPage(domain.id, mngAccessToken);
    const user = users.data.find((u) => u.email === 'barbara@user.com');
    expect(user).toBeDefined();
    expect(user.client).toBe(scimClient.id);

    await clearEmails();
  });
});
