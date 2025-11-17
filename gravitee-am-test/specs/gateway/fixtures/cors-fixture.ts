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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { createUser } from '@management-commands/user-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { Application } from '@management-models/Application';
import { CorsSettings } from '@management-models/CorsSettings';
import { Domain } from '@management-models/Domain';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { User } from '@management-models/User';
import { delay, uniqueName } from '@utils-commands/misc';
import { applicationBase64Token } from '@gateway-commands/utils';
import { getWellKnownOpenIdConfiguration, performPost } from '@gateway-commands/oauth-oidc-commands';

export interface CorsFixture {
  domain: Domain;
  application: Application;
  user: User;
  cleanup: () => Promise<void>;
  getAccessToken: () => Promise<string>;
  updateCorsSettings: (corsSettings: Partial<CorsSettings>) => Promise<void>;
  userinfoPath: string;
}

// Test constants
const TEST_CONSTANTS = {
  USER_PASSWORD: 'CorsP@ssw0rd123!',
  REDIRECT_URI: 'https://example.com/callback',
} as const;

// Helper functions for test setup
async function setupTestEnvironment() {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  // Create and start domain
  const domain = await createDomain(accessToken, uniqueName('cors-test', true), 'CORS test domain');
  expect(domain).toBeDefined();
  expect(domain.id).toBeDefined();

  const startedDomain = await startDomain(domain.id, accessToken);
  // Wait for domain to be ready before getting IDPs
  const domainReady = await waitForDomainStart(startedDomain);
  await waitForDomainSync(domainReady.domain.id, accessToken);

  // Get default IDP
  const idpSet = await getAllIdps(domainReady.domain.id, accessToken);
  const defaultIdp = idpSet.values().next().value;
  expect(defaultIdp).toBeDefined();

  return { domain: domainReady.domain, defaultIdp, accessToken };
}

async function createTestApplication(domain: Domain, defaultIdp: IdentityProvider, accessToken: string) {
  const appName = uniqueName('cors-app', true);
  const application = await createTestApp(appName, domain, accessToken, 'WEB', {
    settings: {
      oauth: {
        redirectUris: [TEST_CONSTANTS.REDIRECT_URI],
        grantTypes: ['authorization_code', 'password'],
        scopeSettings: [{ scope: 'openid', defaultScope: true }],
      },
      advanced: {
        skipConsent: true,
      },
    },
    identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
  });

  expect(application).toBeDefined();
  expect(application.id).toBeDefined();
  expect(application.settings.oauth.clientId).toBeDefined();

  return application;
}

async function createTestUser(domain: Domain, application: Application, defaultIdp: IdentityProvider, accessToken: string) {
  const username = uniqueName('corsuser', true);
  const testUser = await createUser(domain.id, accessToken, {
    firstName: 'Cors',
    lastName: 'User',
    email: `${username}@test.com`,
    username: username,
    password: TEST_CONSTANTS.USER_PASSWORD,
    client: application.id,
    source: defaultIdp.id,
    preRegistration: false,
  });

  expect(testUser).toBeDefined();
  return testUser;
}

/**
 * Create a complete CORS test environment
 */
export const setupCorsFixture = async (): Promise<CorsFixture> => {
  // Setup test environment
  const { domain, defaultIdp, accessToken } = await setupTestEnvironment();

  // Create test application
  const application = await createTestApplication(domain, defaultIdp, accessToken);

  // Create test user
  const user = await createTestUser(domain, application, defaultIdp, accessToken);

  // Ensure application and user are synced before using them
  await waitForDomainSync(domain.id, accessToken);

  // Get OIDC config
  const openIdConfigurationResponse = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
  const openIdConfiguration = openIdConfigurationResponse.body;
  expect(openIdConfiguration).toBeDefined();
  expect(openIdConfiguration.authorization_endpoint).toBeDefined();
  expect(openIdConfiguration.token_endpoint).toBeDefined();
  expect(openIdConfiguration.userinfo_endpoint).toBeDefined();
  const userinfoPath = openIdConfiguration.userinfo_endpoint.replace(process.env.AM_GATEWAY_URL, '');

  // Helper function to update CORS settings
  const updateCorsSettings = async (corsSettings: Partial<CorsSettings>) => {
    await patchDomain(domain.id, accessToken, {
      path: domain.path,
      vhostMode: false,
      vhosts: [],
      corsSettings,
    });
    await waitForDomainSync(domain.id, accessToken, { stabilityMillis: 5000 });
  };

  // Helper function to get access token via password grant
  const getAccessToken = async (): Promise<string> => {
    const tokenParams = new URLSearchParams({
      grant_type: 'password',
      username: user.username,
      password: TEST_CONSTANTS.USER_PASSWORD,
      scope: 'openid',
    });

    const tokenResponse = await performPost(openIdConfiguration.token_endpoint, '', tokenParams.toString(), {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${applicationBase64Token(application)}`,
    }).expect(200);

    expect(tokenResponse.body.access_token).toBeDefined();
    return tokenResponse.body.access_token;
  };

  // Cleanup function
  const cleanup = async () => {
    await safeDeleteDomain(domain.id, accessToken);
  };

  return {
    domain,
    application,
    user,
    cleanup,
    getAccessToken,
    updateCorsSettings,
    userinfoPath,
  };
};
