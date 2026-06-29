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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import {
  getProtectedResourceFlows,
  toFlowPayload,
  updateProtectedResourceFlows,
} from '@management-commands/protected-resources-management-commands';
import { McpOAuth2ResourceFixture, setupMcpOAuth2ResourceFixture } from '../fixtures/mcp-oauth2-resource-fixture';
import { setup } from '../../test-fixture';

setup(200000);

/**
 * A protected resource (MCP server) is exposed to the gateway as an OAuth client whose id is the
 * protected resource id. A flow persisted with application = protectedResourceId must therefore be
 * executed for that client during token issuance. This proves the token flow runs end-to-end by
 * attaching a rate-limit policy to the protected resource TOKEN flow and observing its effect on a
 * client_credentials token request.
 */
describe('Protected resource (MCP server) token flow execution', () => {
  let fixture: McpOAuth2ResourceFixture;

  const tokenRequest = () =>
    performPost(fixture.openIdConfiguration.token_endpoint, '', 'grant_type=client_credentials', {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + getBase64BasicAuth(fixture.mcpResource.clientId, fixture.clientSecret.secret),
    });

  beforeAll(async () => {
    fixture = await setupMcpOAuth2ResourceFixture({
      oauth: { tokenEndpointAuthMethod: 'client_secret_basic' },
    });
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanup();
    }
  });

  it('should issue a token without rate-limit headers before any token flow policy', async () => {
    const response = await tokenRequest().expect(200);
    expect(response.body.access_token).toBeDefined();
    expect(response.headers['x-rate-limit-limit']).toBeUndefined();
  });

  it('should execute the token flow policy during client_credentials', async () => {
    const flows = await getProtectedResourceFlows(fixture.domain.id, fixture.accessToken, fixture.mcpResource.id).then((r) => r.body);
    const tokenFlow = flows.find((f) => f.type.toLowerCase() === 'token');
    tokenFlow.pre = [
      {
        name: 'Rate Limit Policy',
        policy: 'rate-limit',
        description: 'Rate limit protected resource token requests',
        condition: '',
        enabled: true,
        configuration: JSON.stringify({
          async: false,
          addHeaders: true,
          rate: {
            useKeyOnly: true,
            periodTime: 60,
            periodTimeUnit: 'SECONDS',
            key: 'pr-token-flow-test',
            limit: 5,
            dynamicLimit: 5,
          },
        }),
      },
    ];

    // Only the token flow may be submitted for a protected resource.
    await waitForSyncAfter(fixture.domain.id, () =>
      updateProtectedResourceFlows(fixture.domain.id, fixture.accessToken, fixture.mcpResource.id, [toFlowPayload(tokenFlow)]),
    );

    const response = await tokenRequest().expect(200);
    expect(response.body.access_token).toBeDefined();
    // Presence of the rate-limit headers proves the protected resource TOKEN flow executed.
    expect(response.headers['x-rate-limit-limit']).toBe('5');
    expect(response.headers['x-rate-limit-remaining']).toBeDefined();
  });
});
