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
import { createDomain, deleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { uniqueName } from '@utils-commands/misc';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { login } from '@gateway-commands/login-commands';
import { applicationBase64Token } from '@gateway-commands/utils';

export interface MobilePKCEFixture {
  domain: Domain;
  application: Application;
  user: any;
  defaultIdp: IdentityProvider;
  openIdConfiguration: any;
  accessToken: string;
  redirectUri: string;
  cleanup: () => Promise<void>;
  completeAuthorizationFlow: (codeChallenge: string) => Promise<string>;
  exchangeCodeForToken: (authCode: string, codeVerifier: string) => any;
  buildInvalidAuthUrl: (params: Record<string, string>) => string;
}

// Test constants
export const TEST_CONSTANTS = {
  DOMAIN_NAME_PREFIX: 'mobile-pkce-test',
  DOMAIN_DESCRIPTION: 'Mobile PKCE test domain',
  APP_NAME: 'mobile-pkce-app',
  APP_TYPE: 'NATIVE' as const,
  CLIENT_ID: 'mobile-pkce-client',
  CLIENT_SECRET: 'mobile-pkce-secret',
  STATE: 'test-state',
  USER_PASSWORD: 'MobileP@ssw0rd123!',
  USER_USERNAME: 'mobileuser',
  USER_EMAIL: 'mobile.user@test.com',
  USER_FIRST_NAME: 'Mobile',
  USER_LAST_NAME: 'User',
  CODE_CHALLENGE_METHOD: 'S256' as const,
} as const;

// Helper function to parse error messages from URL-encoded location headers
export function parseErrorFromLocation(location: string): { error: string; errorDescription: string } {
  const url = new URL(location);
  return {
    error: url.searchParams.get('error') || '',
    errorDescription: url.searchParams.get('error_description') || '',
  };
}

// Helper functions for PKCE flow
export function buildAuthorizationUrl(endpoint: string, clientId: string, redirectUri: string, codeChallenge: string): string {
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: clientId,
    redirect_uri: redirectUri,
    state: TEST_CONSTANTS.STATE,
    code_challenge: codeChallenge,
    code_challenge_method: TEST_CONSTANTS.CODE_CHALLENGE_METHOD,
  });
  return `${endpoint}?${params.toString()}`;
}

export function extractAuthorizationCode(redirectUrl: string): string {
  const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
  const match = codePattern.exec(redirectUrl);
  expect(match).toBeTruthy();
  return match[1];
}

export function validateTokenResponse(tokenResult: any): void {
  expect(tokenResult.body.access_token).toBeDefined();
  expect(tokenResult.body.token_type).toBeDefined();
  expect(tokenResult.body.token_type.toLowerCase()).toBe('bearer');
  expect(tokenResult.body.expires_in).toBeDefined();
  expect(tokenResult.body.scope).toBeDefined();
}

// Helper functions for test setup
async function setupTestEnvironment() {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  // Create and start domain
  const domain = await createDomain(accessToken, uniqueName(TEST_CONSTANTS.DOMAIN_NAME_PREFIX), TEST_CONSTANTS.DOMAIN_DESCRIPTION);
  expect(domain).toBeDefined();
  expect(domain.id).toBeDefined();

  const startedDomain = await startDomain(domain.id, accessToken);

  // Get default IDP
  const idpSet = await getAllIdps(startedDomain.id, accessToken);
  const defaultIdp = idpSet.values().next().value;
  expect(defaultIdp).toBeDefined();

  return { domain: startedDomain, defaultIdp, accessToken };
}

async function createTestApplication(domain: Domain, defaultIdp: IdentityProvider, accessToken: string, redirectUri: string) {
  const application = await createApplication(domain.id, accessToken, {
    name: TEST_CONSTANTS.APP_NAME,
    type: TEST_CONSTANTS.APP_TYPE,
    clientId: TEST_CONSTANTS.CLIENT_ID,
    clientSecret: TEST_CONSTANTS.CLIENT_SECRET,
    redirectUris: [redirectUri],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: [redirectUri],
            grantTypes: ['authorization_code'],
            forcePKCE: true,
            forceS256CodeChallengeMethod: true,
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
          advanced: {
            skipConsent: true,
          },
        },
        identityProviders: [{ identity: defaultIdp.id, priority: 0 }],
      },
      app.id,
    ).then((updatedApp) => {
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  // Verify application was created successfully
  expect(application).toBeDefined();
  expect(application.id).toBeDefined();
  expect(application.settings.oauth.clientId).toBe(TEST_CONSTANTS.CLIENT_ID);
  expect(application.settings.oauth.clientSecret).toBe(TEST_CONSTANTS.CLIENT_SECRET);
  expect(application.settings.oauth.grantTypes).toEqual(['authorization_code']);

  return application;
}

async function createTestUser(domain: Domain, application: Application, defaultIdp: IdentityProvider, accessToken: string) {
  const testUser = await createUser(domain.id, accessToken, {
    firstName: TEST_CONSTANTS.USER_FIRST_NAME,
    lastName: TEST_CONSTANTS.USER_LAST_NAME,
    email: TEST_CONSTANTS.USER_EMAIL,
    username: TEST_CONSTANTS.USER_USERNAME,
    password: TEST_CONSTANTS.USER_PASSWORD,
    client: application.id,
    source: defaultIdp.id,
    preRegistration: false,
  });

  expect(testUser).toBeDefined();
  return testUser;
}

/**
 * Create a complete mobile PKCE test environment
 */
export const setupMobilePKCEFixture = async (redirectUri: string): Promise<MobilePKCEFixture> => {
  // Setup test environment
  const { domain, defaultIdp, accessToken } = await setupTestEnvironment();

  // Create test application
  const application = await createTestApplication(domain, defaultIdp, accessToken, redirectUri);

  // Create test user
  const user = await createTestUser(domain, application, defaultIdp, accessToken);

  // Wait for domain to be ready and get OIDC configuration
  const domainReady = await waitForDomainStart(domain);
  const readyDomain = domainReady.domain;
  const openIdConfiguration = domainReady.oidcConfig;
  expect(openIdConfiguration).toBeDefined();

  // Helper functions
  const completeAuthorizationFlow = async (codeChallenge: string): Promise<string> => {
    const clientId = application.settings.oauth.clientId;
    const authUrl = buildAuthorizationUrl(openIdConfiguration.authorization_endpoint, clientId, redirectUri, codeChallenge);

    const authResponse = await performGet(authUrl).expect(302);
    const loginResponse = await login(authResponse, user.username, clientId, TEST_CONSTANTS.USER_PASSWORD, false, false);

    const authorizeResponse = await performGet(loginResponse.headers['location'], '', {
      Cookie: loginResponse.headers['set-cookie'],
    }).expect(302);

    const redirectUrl = authorizeResponse.headers['location'];
    expect(redirectUrl).toContain(redirectUri);
    expect(redirectUrl).toContain('code=');

    return extractAuthorizationCode(redirectUrl);
  };

  const exchangeCodeForToken = (authCode: string, codeVerifier: string) => {
    const tokenParams = new URLSearchParams({
      grant_type: 'authorization_code',
      code: authCode,
      redirect_uri: redirectUri,
      code_verifier: codeVerifier,
    });

    return performPost(openIdConfiguration.token_endpoint, '', tokenParams.toString(), {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${applicationBase64Token(application)}`,
    });
  };

  const buildInvalidAuthUrl = (params: Record<string, string>): string => {
    const urlParams = new URLSearchParams({
      response_type: 'code',
      client_id: application.settings.oauth.clientId,
      redirect_uri: redirectUri,
      state: TEST_CONSTANTS.STATE,
      ...params,
    });
    return `${openIdConfiguration.authorization_endpoint}?${urlParams.toString()}`;
  };

  // Cleanup function
  const cleanup = async () => {
    if (readyDomain && accessToken) {
      await deleteDomain(readyDomain.id, accessToken);
    }
  };

  return {
    domain: readyDomain,
    application,
    user,
    defaultIdp,
    openIdConfiguration,
    accessToken,
    redirectUri,
    cleanup,
    completeAuthorizationFlow,
    exchangeCodeForToken,
    buildInvalidAuthUrl,
  };
};
