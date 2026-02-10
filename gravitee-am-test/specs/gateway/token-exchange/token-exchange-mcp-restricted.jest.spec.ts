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
import { parseJwt } from '@api-fixtures/jwt';
import {
  expectTokenIntrospectionMatchesMcpClient,
  setupTokenExchangeMcpFixture,
  TokenExchangeMcpFixture,
} from './fixtures/token-exchange-mcp-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let restrictedFixture: TokenExchangeMcpFixture;

beforeAll(async () => {
  restrictedFixture = await setupTokenExchangeMcpFixture({
    domainNamePrefix: 'token-exchange-mcp-id-only',
    domainDescription: 'Token exchange MCP ID only',
    appClientName: 'token-exchange-mcp-id-only-app',
    mcpServerName: 'token-exchange-mcp-id-only-server',
    appGrantTypes: ['password'],
    scopes: [
      { scope: 'openid', defaultScope: true },
      { scope: 'profile', defaultScope: true },
    ],
    allowedSubjectTokenTypes: ['urn:ietf:params:oauth:token-type:id_token'],
  });
});

afterAll(async () => {
  if (restrictedFixture) {
    await restrictedFixture.cleanup();
  }
});

describe('Token Exchange with restricted subject token types (MCP Server)', () => {
  it('should reject access token when domain only allows id tokens', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = restrictedFixture;

    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('subject_token_type');
  });

  it('should accept id token when domain only allows id tokens', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainToken } = restrictedFixture;

    const { idToken } = await obtainToken('openid%20profile');
    expect(idToken).toBeDefined();

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${idToken}&subject_token_type=urn:ietf:params:oauth:token-type:id_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(response.body.access_token).toBeDefined();

    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['client_id']).toBe(mcpServer.clientId);

    await expectTokenIntrospectionMatchesMcpClient(
      oidc,
      mcpServerBasicAuth,
      response.body.access_token,
      mcpServer.clientId!,
    );
  });
});
