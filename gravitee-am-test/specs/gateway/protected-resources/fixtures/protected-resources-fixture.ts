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
import { createDomain, deleteDomain, startDomain, waitForDomainStart, waitForDomainSync } from '@management-commands/domain-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createProtectedResource } from '@management-commands/protected-resources-management-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { uniqueName } from '@utils-commands/misc';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { login } from '@gateway-commands/login-commands';
import { applicationBase64Token } from '@gateway-commands/utils';

export interface ProtectedResourcesFixture {
  domain: Domain;
  application: Application;
  serviceApplication: Application;
  user: any;
  defaultIdp: IdentityProvider;
  openIdConfiguration: any;
  accessToken: string;
  redirectUri: string;
  protectedResources: any[];
  cleanup: () => Promise<void>;
  completeAuthorizationFlow: (resources: string[]) => Promise<string>;
  exchangeAuthCodeForToken: (authCode: string, resources?: string[]) => any;
  exchangeAuthCodeForTokenWithoutResources: (authCode: string) => any;
  exchangeRefreshToken: (refreshToken: string, resources?: string[]) => any;
}

// Test constants
export const PROTECTED_RESOURCES_TEST = {
  DOMAIN_NAME_PREFIX: 'protected-resources',
  DOMAIN_DESCRIPTION: 'Protected Resources test domain',
  APP_NAME: 'protected-resources-app',
  APP_TYPE: 'WEB' as const,
  CLIENT_ID: 'protected-resources-client',
  CLIENT_SECRET: 'protected-resources-secret',
  STATE: 'test-state',
  USER_PASSWORD: 'ProtectedP@ssw0rd123!',
  USER_USERNAME: 'protecteduser',
  USER_EMAIL: 'protected.user@test.com',
  USER_FIRST_NAME: 'Protected',
  USER_LAST_NAME: 'User',
  REDIRECT_URI: 'https://example.com/callback',
} as const;


// Helper functions for authorization flow
export function buildAuthorizationUrlWithResources(
  endpoint: string, 
  clientId: string, 
  redirectUri: string, 
  resources: string[]
): string {
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: clientId,
    redirect_uri: redirectUri,
    state: PROTECTED_RESOURCES_TEST.STATE,
  });
  resources.forEach(resource => params.append('resource', resource));
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

  const domain = await createDomain(accessToken, uniqueName(PROTECTED_RESOURCES_TEST.DOMAIN_NAME_PREFIX), PROTECTED_RESOURCES_TEST.DOMAIN_DESCRIPTION);
  expect(domain).toBeDefined();
  expect(domain.id).toBeDefined();

  const startedDomain = await startDomain(domain.id, accessToken);

  const idpSet = await getAllIdps(startedDomain.id, accessToken);
  const defaultIdp = idpSet.values().next().value;
  expect(defaultIdp).toBeDefined();

  return { domain: startedDomain, defaultIdp, accessToken };
}

async function createTestApplication(domain: Domain, defaultIdp: IdentityProvider, accessToken: string, redirectUri: string) {
  const application = await createApplication(domain.id, accessToken, {
    name: PROTECTED_RESOURCES_TEST.APP_NAME,
    type: PROTECTED_RESOURCES_TEST.APP_TYPE,
    clientId: PROTECTED_RESOURCES_TEST.CLIENT_ID,
    clientSecret: PROTECTED_RESOURCES_TEST.CLIENT_SECRET,
    redirectUris: [redirectUri],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: [redirectUri],
            grantTypes: ['authorization_code', 'refresh_token', 'client_credentials'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
          advanced: { skipConsent: true },
        },
        identityProviders: [{ identity: defaultIdp.id, priority: 0 }],
      },
      app.id,
    ).then((updatedApp) => {
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  expect(application).toBeDefined();
  expect(application.id).toBeDefined();
  expect(application.settings.oauth.clientId).toBe(PROTECTED_RESOURCES_TEST.CLIENT_ID);
  expect(application.settings.oauth.clientSecret).toBe(PROTECTED_RESOURCES_TEST.CLIENT_SECRET);
  expect(application.settings.oauth.grantTypes).toEqual(['authorization_code', 'refresh_token', 'client_credentials']);

  return application;
}

async function createServiceApplication(domain: Domain, accessToken: string) {
  const application = await createApplication(domain.id, accessToken, {
    name: `${PROTECTED_RESOURCES_TEST.APP_NAME}-service`,
    type: 'SERVICE',
    clientId: `${PROTECTED_RESOURCES_TEST.CLIENT_ID}-service`,
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      { settings: { oauth: { grantTypes: ['client_credentials'] } } },
      app.id,
    ).then((updatedApp) => {
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  expect(application).toBeDefined();
  expect(application.id).toBeDefined();
  expect(application.settings.oauth.grantTypes).toEqual(['client_credentials']);
  return application;
}

async function createTestUser(domain: Domain, application: Application, defaultIdp: IdentityProvider, accessToken: string) {
  const testUser = await createUser(domain.id, accessToken, {
    firstName: PROTECTED_RESOURCES_TEST.USER_FIRST_NAME,
    lastName: PROTECTED_RESOURCES_TEST.USER_LAST_NAME,
    email: PROTECTED_RESOURCES_TEST.USER_EMAIL,
    username: PROTECTED_RESOURCES_TEST.USER_USERNAME,
    password: PROTECTED_RESOURCES_TEST.USER_PASSWORD,
    client: application.id,
    source: defaultIdp.id,
    preRegistration: false,
  });
  expect(testUser).toBeDefined();
  return testUser;
}

async function createTestProtectedResources(domain: Domain, accessToken: string) {
  const resources = [
    { name: 'Photos API', resourceIdentifiers: ['https://api.example.com/photos'], description: 'Photos API resource for testing', type: 'MCP_SERVER' },
    { name: 'Albums API', resourceIdentifiers: ['https://api.example.com/albums'], description: 'Albums API resource for testing', type: 'MCP_SERVER' },
    { name: 'Meta API', resourceIdentifiers: ['https://api.example.com/meta?foo=bar#frag'], description: 'Meta API with query/fragment', type: 'MCP_SERVER' },
  ];
  const protectedResources = [] as any[];
  for (const resource of resources) {
    const protectedResource = await createProtectedResource(domain.id, accessToken, resource);
    expect(protectedResource).toBeDefined();
    protectedResources.push(protectedResource);
  }
  return protectedResources;
}

export const setupProtectedResourcesFixture = async (): Promise<ProtectedResourcesFixture> => {
  const { domain, defaultIdp, accessToken } = await setupTestEnvironment();
  const application = await createTestApplication(domain, defaultIdp, accessToken, PROTECTED_RESOURCES_TEST.REDIRECT_URI);
  const serviceApplication = await createServiceApplication(domain, accessToken);
  const user = await createTestUser(domain, application, defaultIdp, accessToken);
  const protectedResources = await createTestProtectedResources(domain, accessToken);
  const domainReady = await waitForDomainStart(domain);
  const readyDomain = domainReady.domain;
  const openIdConfiguration = domainReady.oidcConfig;
  expect(openIdConfiguration).toBeDefined();
  await waitForDomainSync();

  const completeAuthorizationFlowWithResources = async (resources: string[]): Promise<string> => {
    const clientId = application.settings.oauth.clientId;
    const authUrl = buildAuthorizationUrlWithResources(
      openIdConfiguration.authorization_endpoint,
      clientId,
      PROTECTED_RESOURCES_TEST.REDIRECT_URI,
      resources,
    );
    const authResponse = await performGet(authUrl).expect(302);
    const loginResponse = await login(authResponse, user.username, clientId, PROTECTED_RESOURCES_TEST.USER_PASSWORD, false, false);
    const authorizeResponse = await performGet(loginResponse.headers['location'], '', { Cookie: loginResponse.headers['set-cookie'] }).expect(302);
    const redirectUrl = authorizeResponse.headers['location'];
    expect(redirectUrl).toContain(PROTECTED_RESOURCES_TEST.REDIRECT_URI);
    expect(redirectUrl).toContain('code=');
    return extractAuthorizationCode(redirectUrl);
  };

  const exchangeCodeForTokenWithResources = (authCode: string, resources?: string[]) => {
    const tokenParams = new URLSearchParams({ grant_type: 'authorization_code', code: authCode, redirect_uri: PROTECTED_RESOURCES_TEST.REDIRECT_URI });
    if (resources && resources.length > 0) resources.forEach((r) => tokenParams.append('resource', r));
    return performPost(openIdConfiguration.token_endpoint, '', tokenParams.toString(), {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${applicationBase64Token(application)}`,
    });
  };

  const exchangeCodeForTokenWithoutResources = (authCode: string) => exchangeCodeForTokenWithResources(authCode, []);

  const exchangeRefreshForTokenWithResources = (refreshToken: string, resources?: string[]) => {
    const tokenParams = new URLSearchParams({ grant_type: 'refresh_token', refresh_token: refreshToken });
    if (resources && resources.length > 0) resources.forEach((r) => tokenParams.append('resource', r));
    return performPost(openIdConfiguration.token_endpoint, '', tokenParams.toString(), {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${applicationBase64Token(application)}`,
    });
  };

  const cleanup = async () => {
    if (readyDomain && accessToken) {
      await deleteDomain(readyDomain.id, accessToken);
    }
  };

  return {
    domain: readyDomain,
    application,
    serviceApplication,
    user,
    defaultIdp,
    openIdConfiguration,
    accessToken,
    redirectUri: PROTECTED_RESOURCES_TEST.REDIRECT_URI,
    protectedResources,
    cleanup,
    completeAuthorizationFlow: completeAuthorizationFlowWithResources,
    exchangeAuthCodeForToken: exchangeCodeForTokenWithResources,
    exchangeAuthCodeForTokenWithoutResources: exchangeCodeForTokenWithoutResources,
    exchangeRefreshToken: exchangeRefreshForTokenWithResources,
  };
};
