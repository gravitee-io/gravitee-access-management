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
import { createProtectedResource } from '@management-commands/protected-resources-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { NewProtectedResource } from '@management-models/NewProtectedResource';
import { uniqueName } from '@utils-commands/misc';
import { login } from '@gateway-commands/login-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import * as faker from 'faker';

export interface ProtectedResourcesTestContext {
  accessToken: string;
  domain: Domain;
  application: Application;
  protectedResource: any;
  oidcConfig: any;
  user: any;
}

export async function setupProtectedResourcesTest(): Promise<ProtectedResourcesTestContext> {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  // Create domain
  const createdDomain = await createDomain(accessToken, uniqueName('protected-resources-gateway'), faker.company.catchPhraseDescriptor());
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  // Start domain and wait for it to be ready
  await startDomain(createdDomain.id, accessToken);
  const domainReady = await waitForDomainStart(createdDomain);
  const oidcConfig = domainReady.oidcConfig;
  expect(oidcConfig).toBeDefined();

  // Create test application using createTestApp (which properly sets OAuth settings)
  const application = await createTestApp(uniqueName('test-app'), createdDomain, accessToken, 'web', {
    settings: {
      oauth: {
        grantTypes: ['client_credentials', 'authorization_code'],
        redirectUris: ['https://example.com/callback'],
      },
    },
  });
  expect(application).toBeDefined();
  
  // Verify the application was created correctly
  expect(application.settings.oauth.grantTypes).toContain('authorization_code');
  expect(application.settings.oauth.redirectUris).toContain('https://example.com/callback');
  
  // Wait for the application to be synchronized
  await new Promise(resolve => setTimeout(resolve, 2000));

  // Create protected resource (MCP server)
  const protectedResourceRequest = {
    name: faker.commerce.productName(),
    type: 'MCP_SERVER',
    resourceIdentifiers: ['https://api.example.com/photos', 'https://api.example.com/albums'],
  } as NewProtectedResource;

  const protectedResource = await createProtectedResource(createdDomain.id, accessToken, protectedResourceRequest);
  expect(protectedResource).toBeDefined();
  expect(protectedResource.id).toBeDefined();

  // Create test user for authorization endpoint testing
  const user = await createUser(createdDomain.id, accessToken, {
    username: uniqueName('testuser'),
    email: faker.internet.email(),
    firstName: faker.name.firstName(),
    lastName: faker.name.lastName(),
    password: 'TestP@ssw0rd123!',
  });
  expect(user).toBeDefined();

  await waitForDomainSync();

  return {
    accessToken,
    domain: domainReady.domain,
    application,
    protectedResource,
    oidcConfig,
    user,
  };
}

export async function cleanupProtectedResourcesTest(context: ProtectedResourcesTestContext): Promise<void> {
  if (context?.domain?.id) {
    await deleteDomain(context.domain.id, context.accessToken);
  }
}

export async function testAuthorizationEndpointWithLogin(
  context: ProtectedResourcesTestContext,
  resourceParams: string
): Promise<any> {
  const authUrl = `${context.oidcConfig.authorization_endpoint}?response_type=code&client_id=${context.application.settings.oauth.clientId}&redirect_uri=https://example.com/callback&${resourceParams}`;
  
  // Step 1: Initial authorization request (should redirect to login)
  const authResponse = await performGet(authUrl).expect(302);
  
  // Step 2: Login with the test user
  const loginResponse = await login(
    authResponse,
    context.user.username,
    context.application.settings.oauth.clientId,
    'TestP@ssw0rd123!',
    false,
    false
  );
  
  // Step 3: Follow the authorization flow after login
  const authorizeResponse = await performGet(loginResponse.headers['location'], '', {
    Cookie: loginResponse.headers['set-cookie'],
  });
  
  return authorizeResponse;
}
