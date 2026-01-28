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
import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  safeDeleteDomain,
  setupDomainForTest,
  startDomain,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { createAuthorizationEngine, deleteAuthorizationEngine } from '@management-commands/authorization-engine-management-commands';
import { mcpAuthorizationModel } from '@api-fixtures/openfga-fixtures';
import { AuthorizationEngine } from '@management-models/AuthorizationEngine';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { requestClientCredentialsToken, getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';
import { decodeJwt } from '@utils-commands/jwt';
import {
  createProtectedResource,
  deleteProtectedResource,
  createMcpClientSecret,
  getMcpServer,
} from '@management-commands/protected-resources-management-commands';
import { Fixture } from '../../../test-fixture';

export interface McpTestServerFixture extends Fixture {
  domain: any;
  authEngine: AuthorizationEngine;
  mcpServerResource: any;
  mcpServerClientSecret: any;
  mcpServerAccessToken: string;
  mcpClientApp: any;
  mcpClientAppAccessToken: string;
  openIdConfiguration: any;
  storeId: string;
  authorizationModelId: string;
}

const MCP_TEST_SERVER_RESOURCE_IDENTIFIER = 'https://example.com/mcp-test-server';

export async function setupMcpTestServerFixture(): Promise<McpTestServerFixture> {
  let domain: any = null;
  let accessToken: string | null = null;
  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    // Create test domain
    domain = await setupDomainForTest(uniqueName('mcp-toolcall', true), { accessToken }).then((it) => it.domain);
    expect(domain).toBeDefined();

    // Start domain for gateway endpoints
    await startDomain(domain.id, accessToken);

  // Create OpenFGA store
  const storeName = `mcp-toolcall-store-${Date.now()}`;
  const storeResponse = await fetch(`${process.env.AM_OPENFGA_URL}/stores`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: storeName }),
  });
  expect(storeResponse.status).toBe(201);
  const storeId = (await storeResponse.json()).id;

  // Create authorization model
  const modelResponse = await fetch(`${process.env.AM_OPENFGA_URL}/stores/${storeId}/authorization-models`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(mcpAuthorizationModel),
  });
  expect(modelResponse.status).toBe(201);
  const authorizationModelId = (await modelResponse.json()).authorization_model_id;

    // Create authorization engine
    const authEngine = await createAuthorizationEngine(domain.id, accessToken, {
      type: 'openfga',
      name: 'MCP Toolcall Test Engine',
      configuration: JSON.stringify({
        connectionUri: process.env.AM_INTERNAL_OPENFGA_URL,
        storeId,
        authorizationModelId,
        apiToken: '',
      }),
    });
    expect(authEngine?.id).toBeDefined();
    await waitForDomainSync(domain.id, accessToken);

    // Create MCP Protected Resource
    let mcpServerResource = await createProtectedResource(domain.id, accessToken, {
      name: uniqueName('mcp-test-server'),
      type: 'MCP_SERVER',
      resourceIdentifiers: [MCP_TEST_SERVER_RESOURCE_IDENTIFIER],
    });
    expect(mcpServerResource?.id).toBeDefined();

    mcpServerResource = await getMcpServer(domain.id, accessToken, mcpServerResource.id);
    expect(mcpServerResource?.clientId).toBeDefined();

    // Create client secret for MCP server
    const mcpServerClientSecret = await createMcpClientSecret(domain.id, accessToken, mcpServerResource.id, {
      name: 'default-secret',
    });
    expect(mcpServerClientSecret).toBeDefined();
    expect(mcpServerClientSecret.secret).toBeDefined();

    // Create client application that will call MCP server tools
    const appClientId = `mcp-client-${Date.now()}`;
    const appClientSecret = `mcp-secret-${Date.now()}`;
    const mcpClientApp = await createApplication(domain.id, accessToken, {
      name: 'MCP Client Application',
      type: 'SERVICE',
      clientId: appClientId,
      clientSecret: appClientSecret,
    }).then((app) =>
      updateApplication(
        domain.id,
        accessToken,
        {
          settings: {
            oauth: {
              grantTypes: ['client_credentials'],
            },
          },
        },
        app.id,
      ).then((updatedApp) => {
        updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
        return updatedApp;
      }),
    );
    expect(mcpClientApp?.id).toBeDefined();
    expect(mcpClientApp?.settings?.oauth?.clientId).toBeDefined();
    expect(mcpClientApp?.settings?.oauth?.clientSecret).toBeDefined();

    // Wait for sync before getting OIDC config
    await waitForDomainSync(domain.id, accessToken);

    // Get OpenID configuration
    const openIdConfigResponse = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
    const openIdConfiguration = openIdConfigResponse.body;
    expect(openIdConfiguration?.token_endpoint).toBeDefined();

    // Obtain access token for MCP server (for AuthZen calls)
    const mcpServerAccessToken = await requestClientCredentialsToken(
      mcpServerResource.clientId,
      mcpServerClientSecret.secret,
      openIdConfiguration,
    );
    expect(mcpServerAccessToken).toBeDefined();

    // Obtain access token for client application WITH resource parameter
    // This makes token aud = Protected Resource resource identifier
    const mcpClientAppAccessToken = await requestClientCredentialsToken(
      mcpClientApp.settings.oauth.clientId,
      mcpClientApp.settings.oauth.clientSecret,
      openIdConfiguration,
      undefined,
      mcpServerResource.resourceIdentifiers[0],
    );
    expect(mcpClientAppAccessToken).toBeDefined();

    // Verify token has correct audience
    const decodedToken = decodeJwt(mcpClientAppAccessToken);
    expect(decodedToken.aud).toBe(mcpServerResource.resourceIdentifiers[0]);

    return {
      accessToken,
      domain,
      authEngine,
      mcpServerResource,
      mcpServerClientSecret,
      mcpServerAccessToken,
      mcpClientApp,
      mcpClientAppAccessToken,
      openIdConfiguration,
      storeId,
      authorizationModelId,
      async cleanUp() {
        if (authEngine?.id) {
          await deleteAuthorizationEngine(domain.id, authEngine.id, accessToken);
        }
        if (storeId) {
          await fetch(`${process.env.AM_OPENFGA_URL}/stores/${storeId}`, {
            method: 'DELETE',
          }).catch(() => {
            // Ignore cleanup errors
          });
        }
        if (mcpServerResource?.id) {
          await deleteProtectedResource(domain.id, accessToken, mcpServerResource.id, 'MCP_SERVER').catch(() => {
            // Ignore cleanup errors
          });
        }
        if (domain?.id) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
    };
  } catch (error) {
    // Cleanup domain if setup fails partway through
    if (domain && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
}
