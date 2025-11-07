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

global.fetch = fetch;

import { jest, afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, patchDomain, startDomain, waitForDomainStart, waitForDomainSync, waitForDomainSyncWithVerification } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createUser, getUser } from '@management-commands/user-management-commands';
import { createApplication, getApplication, patchApplication, updateApplication } from '@management-commands/application-management-commands';
import {
  extractXsrfTokenAndActionResponse,
  getWellKnownOpenIdConfiguration,
  logoutUser,
  performFormPost,
  performGet,
} from '@gateway-commands/oauth-oidc-commands';

let domain;
let managementApiAccessToken;
let openIdConfiguration;
let application;
let user;

jest.setTimeout(200000);

beforeAll(async () => {
  managementApiAccessToken = await requestAdminAccessToken();
  expect(managementApiAccessToken).toBeDefined();
  domain = await createDomain(managementApiAccessToken, uniqueName('enduser-logout', true), 'test end-user logout');
  expect(domain).toBeDefined();

  const domainStarted = await startDomain(domain.id, managementApiAccessToken);
  const domainWithOidc = await waitForDomainStart(domainStarted);
  domain = domainWithOidc.domain;

  // Create the application
  const idpSet = await getAllIdps(domain.id, managementApiAccessToken);
  const appClientId = uniqueName('app-logout', true);
  const appClientSecret = uniqueName('app-logout', true);
  const appName = uniqueName('my-client', true);
  application = await createApplication(domain.id, managementApiAccessToken, {
    name: appName,
    type: 'WEB',
    clientId: appClientId,
    clientSecret: appClientSecret,
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      managementApiAccessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://callback'],
            grantTypes: ['authorization_code'],
          },
        },
        identityProviders: [{ identity: idpSet.values().next().value.id, priority: -1 }],
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
  expect(application).toBeDefined();
  
  // Wait for application to sync to gateway
  await waitForDomainSync();

  // Create a User
  const username = uniqueName('LogoutUser', true);
  user = {
    username: username,
    password: 'SomeP@ssw0rd',
    firstName: 'Logout',
    lastName: 'User',
    email: 'logoutuser@acme.fr',
    preRegistration: false,
  };
  const createdUser = await createUser(domain.id, managementApiAccessToken, user);
  // Wait for user to be synced by verifying user exists
  await waitForDomainSyncWithVerification(
    () => getUser(domain.id, managementApiAccessToken, createdUser.id),
    (user) => user !== null && user.id === createdUser.id,
    { timeoutMillis: 30000, intervalMillis: 500 }
  );

  // Use the OIDC config from waitForDomainStart if available, otherwise fetch it
  openIdConfiguration = domainWithOidc.oidcConfig || (await getWellKnownOpenIdConfiguration(domain.hrid).expect(200)).body;
  expect(openIdConfiguration).toBeDefined();
});

describe('OAuth2 - Logout tests', () => {
  describe('Default settings - target_uri is not restricted', () => {
    it('After sign-in a user can logout without target_uri', async () => {
      const postLoginRedirect = await signInUser();
      const response = await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
      expect(response.headers['location']).toEqual('/');
    });

    it('After sign-in a user can logout with any target_uri', async () => {
      const postLoginRedirect = await signInUser();
      const response = await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect, 'https://somewhere/after/logout');
      expect(response.headers['location']).toEqual('https://somewhere/after/logout');
    });
  });

  describe('Domain Settings - target_uri is restricted', () => {
    it('Update domain settings', async () => {
      await patchDomain(domain.id, managementApiAccessToken, {
        oidc: {
          postLogoutRedirectUris: ['https://somewhere/after/logout'],
        },
      });
      // Wait for domain settings to sync (configurable timeout should be sufficient)
      await waitForDomainSync();
    });

    it('After sign-in a user can logout without target_uri', async () => {
      const postLoginRedirect = await signInUser();
      const response = await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
      expect(response.headers['location']).toEqual('/');
    });

    it('After sign-in a user can NOT logout with an unknown target_uri', async () => {
      const postLoginRedirect = await signInUser();
      const response = await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect, 'https://someunkown/location');
      expect(response.headers['location']).toContain('/error');
      expect(response.headers['location']).toContain('error=invalid_request');
    });

    it('After sign-in a user can logout with a declared target_uri', async () => {
      const postLoginRedirect = await signInUser();
      const response = await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect, 'https://somewhere/after/logout');
      expect(response.headers['location']).toEqual('https://somewhere/after/logout');
    });
  });

  describe('Application Settings - target_uri is restricted', () => {
    it('Update application settings', async () => {
      await patchApplication(
        domain.id,
        managementApiAccessToken,
        {
          settings: {
            oauth: {
              postLogoutRedirectUris: ['https://somewhere/after/app/logout'],
            },
          },
        },
        application.id,
      );
      // Wait for application settings to sync by verifying settings are updated
      await waitForDomainSyncWithVerification(
        () => getApplication(domain.id, managementApiAccessToken, application.id),
        (app) => app.settings?.oauth?.postLogoutRedirectUris?.includes('https://somewhere/after/app/logout') === true,
        { timeoutMillis: 30000, intervalMillis: 500 }
      );
    });

    it('After sign-in a user can logout without target_uri', async () => {
      const postLoginRedirect = await signInUser();
      const response = await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
      expect(response.headers['location']).toEqual('/');
    });

    it('After sign-in a user can NOT logout with an unknown target_uri', async () => {
      const postLoginRedirect = await signInUser();
      const response = await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect, 'https://someunkown/location');
      expect(response.headers['location']).toContain('/error');
      expect(response.headers['location']).toContain('error=invalid_request');
    });

    it('After sign-in a user can NOT logout with a target_uri only declared in at domain level', async () => {
      const postLoginRedirect = await signInUser();
      const response = await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect, 'https://somewhere/after/logout');
      expect(response.headers['location']).toContain('/error');
      expect(response.headers['location']).toContain('error=invalid_request');
    });

    it('After sign-in a user can logout with a target_uri declared at app level', async () => {
      const postLoginRedirect = await signInUser();
      const response = await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect, 'https://somewhere/after/app/logout');
      expect(response.headers['location']).toEqual('https://somewhere/after/app/logout');
    });
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, managementApiAccessToken);
  }
});

async function signInUser() {
  const clientId = application.settings.oauth.clientId;
  const params = `?response_type=code&client_id=${clientId}&redirect_uri=https://callback`;

  // Initiate the Login Flow
  const authResponse = await performGet(openIdConfiguration.authorization_endpoint, params).expect(302);
  const loginLocation = authResponse.headers['location'];

  expect(loginLocation).toBeDefined();
  expect(loginLocation).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/login`);
  expect(loginLocation).toContain(`client_id=${clientId}`);

  // Redirect to /login
  const loginResult = await extractXsrfTokenAndActionResponse(authResponse);
  // Authentication
  const postLogin = await performFormPost(
    loginResult.action,
    '',
    {
      'X-XSRF-TOKEN': loginResult.token,
      username: user.username,
      password: user.password,
      client_id: clientId,
    },
    {
      Cookie: loginResult.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  // Post authentication
  const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
    Cookie: postLogin.headers['set-cookie'],
  }).expect(302);

  expect(postLoginRedirect.headers['location']).toBeDefined();
  expect(postLoginRedirect.headers['location']).toContain(`https://callback`);
  expect(postLoginRedirect.headers['location']).toMatch(/code=[-_a-zA-Z0-9]+&?/);
  return postLoginRedirect;
}
