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

import { afterAll, afterEach, beforeAll, describe, expect, it } from '@jest/globals';
import { addTuple, deleteTuple } from '@management-commands/openfga-settings-commands';
import { tupleFactory } from '@api-fixtures/openfga-fixtures';
import { setupMcpTestServerFixture, type McpTestServerFixture } from './fixtures/mcp-test-server-fixture';
import { setup } from '../../test-fixture';

// Tool resource ID matches middleware format: mcp_tool_<toolName> (underscore, not colon, for OpenFGA compatibility)
const TOOL_RESOURCE_ID = 'mcp_tool_getTests';

setup(200000);

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
    try {
      const clientId = getClientId(fixture.mcpClientApp);
      await deleteTuple(fixture.domain.id, fixture.authEngine.id, fixture.accessToken, tupleFactory.ownerTuple(clientId, TOOL_RESOURCE_ID));
    } catch {
      // Ignore cleanup errors
    }
    await fixture.clearTestMetadata();
  });

  it('should successfully call getTests tool when client has owner permission', async () => {
    const metadataResult = await fixture.injectTestMetadata(testId);
    expect(metadataResult.success).toBe(true);
    expect(metadataResult.testId).toBe(testId);

    const toolResponse = await fixture.callTool('getTests');

    expect(toolResponse.status).toBe(200);
    const toolResult = await toolResponse.json();
    expect(toolResult.testId).toBe(testId);
    expect(toolResult.message).toBe('getTests tool responded');
    expect(toolResult.tests).toBeDefined();
    expect(Array.isArray(toolResult.tests)).toBe(true);
  });
});

describe('MCP Test Server Tool Calls - Failure Scenarios', () => {
  afterEach(async () => {
    await fixture.clearTestMetadata();
  });

  it('should return 403 Forbidden when client lacks permission', async () => {
    const testId = `test-auth-failure-${Date.now()}`;

    await fixture.injectTestMetadata(testId);

    // Call getTests tool with client token (no OpenFGA tuple = no permission)
    const toolResponse = await fixture.callTool('getTests');

    expect(toolResponse.status).toBe(403);
    const errorResult = await toolResponse.json();
    expect(errorResult.error).toBeDefined();
    expect(errorResult.error).toContain('denied');
  });

  it('should return 401 Unauthorized when Authorization header is missing', async () => {
    const testId = `test-no-auth-${Date.now()}`;

    await fixture.injectTestMetadata(testId);

    // Call getTests tool without Authorization header (pass empty string token)
    const toolResponse = await fixture.callTool('getTests', '');

    expect(toolResponse.status).toBe(401);
    const errorResult = await toolResponse.json();
    expect(errorResult.error).toBeDefined();
    expect(errorResult.error).toContain('Authorization header');
  });
});
