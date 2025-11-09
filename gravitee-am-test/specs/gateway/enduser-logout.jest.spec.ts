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

globalThis.fetch = fetch;

import { jest, afterAll, afterEach, beforeAll, beforeEach, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, patchDomain, startDomain, waitForDomainStart, waitForDomainSync, waitForApplicationSync } from '@management-commands/domain-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { createApplication, deleteApplication, patchApplication, updateApplication } from '@management-commands/application-management-commands';
import {
  extractXsrfTokenAndActionResponse,
  logoutUser,
  performFormPost,
  performGet,
} from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';

let domain;
let managementApiAccessToken;
let openIdConfiguration;
let application;
let user;
let defaultIdp;

jest.setTimeout(200000);

beforeAll(async () => {
  managementApiAccessToken = await requestAdminAccessToken();
  expect(managementApiAccessToken).toBeDefined();
  domain = await createDomain(managementApiAccessToken, uniqueName('enduser-logout', true), 'test end-user logout');
  expect(domain).toBeDefined();

  // Get default IDP for application creation
  const idpSet = await getAllIdps(domain.id, managementApiAccessToken);
  defaultIdp = idpSet.values().next().value;
  expect(defaultIdp).toBeDefined();

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
  await createUser(domain.id, managementApiAccessToken, user);
  await waitForDomainSync(domain.id, managementApiAccessToken);

  // Start domain and wait for it to be ready to serve requests
  // This ensures the OpenID configuration endpoint is available
  const started = await startDomain(domain.id, managementApiAccessToken).then(waitForDomainStart);
  domain = started.domain;
  openIdConfiguration = started.oidcConfig;
  expect(openIdConfiguration).toBeDefined();
});

// Helper function to create a fresh application for each test
async function createTestApplication() {
  const appClientId = uniqueName('app-logout', true);
  const appClientSecret = uniqueName('app-logout', true);
  const appName = uniqueName('my-client', true);
  const app = await createApplication(domain.id, managementApiAccessToken, {
    name: appName,
    type: 'WEB',
    clientId: appClientId,
    clientSecret: appClientSecret,
    redirectUris: ['https://callback'],
  }).then((createdApp) =>
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
        identityProviders: [{ identity: defaultIdp.id, priority: -1 }],
      },
      createdApp.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = createdApp.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
  await waitForDomainSync(domain.id, managementApiAccessToken);
  return app;
}

describe('OAuth2 - Logout tests', () => {
  describe('Default settings - target_uri is not restricted', () => {
    beforeEach(async () => {
      // Create a fresh application for each test to ensure isolation
      application = await createTestApplication();
    });

    afterEach(async () => {
      // Clean up application after each test
      if (application?.id) {
        await deleteApplication(domain.id, managementApiAccessToken, application.id);
        await waitForDomainSync(domain.id, managementApiAccessToken);
      }
    });
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
    beforeEach(async () => {
      // Create a fresh application for each test to ensure isolation
      application = await createTestApplication();
    });

    afterEach(async () => {
      // Clean up application after each test
      if (application?.id) {
        await deleteApplication(domain.id, managementApiAccessToken, application.id);
        await waitForDomainSync(domain.id, managementApiAccessToken);
      }
    });

    it('Update domain settings', async () => {
      await patchDomain(domain.id, managementApiAccessToken, {
        oidc: {
          postLogoutRedirectUris: ['https://somewhere/after/logout'],
        },
      });
      await waitForDomainSync(domain.id, managementApiAccessToken);
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
    beforeAll(async () => {
      // Create the application once for all tests in this describe block
      // Set postLogoutRedirectUris to empty array initially so gateway knows
      // app-level restrictions are in place (won't fall back to domain-level)
      // When app-level postLogoutRedirectUris is set (even as empty array),
      // the gateway will use only app-level URIs and NOT fall back to domain-level
      application = await createTestApplication();
      // Set empty array to ensure app-level restrictions are active
      await patchApplication(
        domain.id,
        managementApiAccessToken,
        {
          settings: {
            oauth: {
              postLogoutRedirectUris: [],
            },
          },
        },
        application.id,
      );
      await waitForApplicationSync(domain.id, managementApiAccessToken, application.id);
    });

    afterAll(async () => {
      // Clean up application after all tests in this describe block
      if (application?.id) {
        await deleteApplication(domain.id, managementApiAccessToken, application.id);
        await waitForDomainSync(domain.id, managementApiAccessToken);
      }
    });

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
      // Wait for application sync to ensure ApplicationEvent has been processed
      // This avoids race conditions where domain sync completes but the Client object
      // in the gateway hasn't been updated yet
      await waitForApplicationSync(domain.id, managementApiAccessToken, application.id);
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
  if (domain?.id) {
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
