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
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import { addTuple, deleteTuple } from '@management-commands/openfga-settings-commands';
import { tupleFactory } from '@api-fixtures/openfga-fixtures';
import { setupMcpTestServerFixture, type McpTestServerFixture } from './fixtures/mcp-test-server-fixture';

global.fetch = fetch;

const MCP_TEST_SERVER_URL = process.env.MCP_TEST_SERVER_URL || 'http://localhost:3001';
// Tool resource ID matches middleware format: mcp_tool_<toolName> (underscore, not colon, for OpenFGA compatibility)
const TOOL_RESOURCE_ID = 'mcp_tool_getTests';

jest.setTimeout(200000);

let fixture: McpTestServerFixture;

beforeAll(async () => {
  fixture = await setupMcpTestServerFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

function getClientId(app: any): string {
  return app.settings?.oauth?.clientId || app.clientId || app.settings?.clientId;
}

describe('MCP Test Server Tool Calls - Happy Path', () => {
  const testId = `test-${Date.now()}`;

  beforeAll(async () => {
    // Setup OpenFGA tuple: client application is owner of getTests tool
    const clientId = getClientId(fixture.mcpClientApp);
    expect(clientId).toBeDefined();
    const tuple = tupleFactory.ownerTuple(clientId, TOOL_RESOURCE_ID);
    await addTuple(fixture.domain.id, fixture.authEngine.id, fixture.accessToken, tuple);
  });

  afterAll(async () => {
    // Cleanup tuple
    try {
      const clientId = getClientId(fixture.mcpClientApp);
      await deleteTuple(fixture.domain.id, fixture.authEngine.id, fixture.accessToken, tupleFactory.ownerTuple(clientId, TOOL_RESOURCE_ID));
    } catch {
      // Ignore cleanup errors
    }
    // Cleanup metadata
    await fetch(`${MCP_TEST_SERVER_URL}/admin/test-metadata`, {
      method: 'DELETE',
    }).catch(() => {
      // Ignore cleanup errors
    });
  });

  it('should successfully call getTests tool when client has owner permission', async () => {
    // Step 1: Inject test metadata into MCP test server
    const metadataResponse = await fetch(`${MCP_TEST_SERVER_URL}/admin/test-metadata`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        testId,
        token: fixture.mcpServerAccessToken,
        clientId: fixture.mcpServerResource.clientId,
        clientSecret: fixture.mcpServerClientSecret.secret,
        expiresIn: 3600,
        domainHrid: fixture.domain.hrid,
      }),
    });
    expect(metadataResponse.status).toBe(200);
    const metadataResult = await metadataResponse.json();
    expect(metadataResult.success).toBe(true);
    expect(metadataResult.testId).toBe(testId);

    // Step 2: Call getTests tool with client token
    const toolResponse = await fetch(`${MCP_TEST_SERVER_URL}/tools/getTests`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${fixture.mcpClientAppAccessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({}),
    });

    // Step 3: Verify successful response
    expect(toolResponse.status).toBe(200);
    const toolResult = await toolResponse.json();
    expect(toolResult.testId).toBe(testId);
    expect(toolResult.message).toBe('getTests tool responded');
    expect(toolResult.tests).toBeDefined();
    expect(Array.isArray(toolResult.tests)).toBe(true);
  });
});

describe('MCP Test Server Tool Calls - Failure Scenarios', () => {
  afterAll(async () => {
    // Cleanup metadata
    await fetch(`${MCP_TEST_SERVER_URL}/admin/test-metadata`, {
      method: 'DELETE',
    }).catch(() => {
      // Ignore cleanup errors
    });
  });

  it('should return 403 Forbidden when client lacks permission', async () => {
    const testId = `test-auth-failure-${Date.now()}`;

    // Step 1: Inject test metadata into MCP test server
    const metadataResponse = await fetch(`${MCP_TEST_SERVER_URL}/admin/test-metadata`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        testId,
        token: fixture.mcpServerAccessToken,
        clientId: fixture.mcpServerResource.clientId,
        clientSecret: fixture.mcpServerClientSecret.secret,
        expiresIn: 3600,
        domainHrid: fixture.domain.hrid,
      }),
    });
    expect(metadataResponse.status).toBe(200);

    // Step 2: Call getTests tool with client token (no OpenFGA tuple = no permission)
    const toolResponse = await fetch(`${MCP_TEST_SERVER_URL}/tools/getTests`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${fixture.mcpClientAppAccessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({}),
    });

    // Step 3: Verify authorization failure
    expect(toolResponse.status).toBe(403);
    const errorResult = await toolResponse.json();
    expect(errorResult.error).toBeDefined();
    expect(errorResult.error).toContain('denied');
  });

  it('should return 401 Unauthorized when Authorization header is missing', async () => {
    const testId = `test-no-auth-${Date.now()}`;

    // Step 1: Inject test metadata
    await fetch(`${MCP_TEST_SERVER_URL}/admin/test-metadata`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        testId,
        token: fixture.mcpServerAccessToken,
        clientId: fixture.mcpServerResource.clientId,
        clientSecret: fixture.mcpServerClientSecret.secret,
        expiresIn: 3600,
        domainHrid: fixture.domain.hrid,
      }),
    });

    // Step 2: Call getTests tool without Authorization header
    const toolResponse = await fetch(`${MCP_TEST_SERVER_URL}/tools/getTests`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({}),
    });

    // Step 3: Verify authentication failure (OAuth2 Bearer token format validation)
    expect(toolResponse.status).toBe(401);
    const errorResult = await toolResponse.json();
    expect(errorResult.error).toBeDefined();
    expect(errorResult.error).toContain('Authorization header');
  });
});
