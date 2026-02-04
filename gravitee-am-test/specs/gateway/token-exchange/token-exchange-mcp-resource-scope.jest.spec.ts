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
  setupTokenExchangeMcpFixture,
  TokenExchangeMcpFixture,
  TOKEN_EXCHANGE_MCP_TEST,
} from './fixtures/token-exchange-mcp-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: TokenExchangeMcpFixture;

beforeAll(async () => {
  fixture = await setupTokenExchangeMcpFixture({
    domainNamePrefix: 'token-exchange-mcp-resource-scope',
    domainDescription: 'Token exchange MCP resource scope validation',
    appClientName: 'token-exchange-mcp-resource-scope-app',
    mcpServerName: 'token-exchange-mcp-resource-scope-server',
    mcpToolScopes: ['openid', 'profile'],
    allowDelegation: true,
    maxDelegationDepth: 1,
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Token Exchange with resource parameter (scope validation)', () => {
  const resourceUri = encodeURIComponent(TOKEN_EXCHANGE_MCP_TEST.RESOURCE_IDENTIFIER);

  it('should return invalid_scope when requested scope is not in resource scopes', async () => {
    const { oidc, mcpServerBasicAuth, obtainSubjectToken } = fixture;

    // Arrange: subject token has openid, profile, offline_access; resource allows only openid, profile
    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile%20offline_access');

    // Act: request scope includes offline_access (not in resource)
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&resource=${resourceUri}&scope=openid%20profile%20offline_access`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(400);

    // Assert: dedicated error for resource-scope mismatch
    expect(response.body.error).toBe('invalid_scope');
    expect(response.body.error_description).toBe('Requested scope is not allowed for the specified resource.');
  });

  it('should succeed when requested scope is subset of resource scopes', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainSubjectToken } = fixture;

    // Arrange: subject token has openid, profile, offline_access; resource allows openid, profile
    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile%20offline_access');

    // Act: request only openid, profile (subset of resource)
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&resource=${resourceUri}&scope=openid%20profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    // Assert: issued token has requested scope only
    expect(response.body.access_token).toBeDefined();
    expect(response.body.scope).toBe('openid profile');

    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['client_id']).toBe(mcpServer.clientId);
    expect(decoded.payload['scope']).toBe('openid profile');

    // Introspect: token is active; when resource is set, aud may be resource URI so we do not assert aud
    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${response.body.access_token}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);
    expect(introspectResponse.body.active).toBe(true);
    expect(introspectResponse.body.scope).toBe('openid profile');
  });

  it('should succeed when resource is set and scope is omitted (full allowed)', async () => {
    const { oidc, mcpServerBasicAuth, obtainSubjectToken } = fixture;

    // Arrange: subject token has openid, profile, offline_access; resource allows openid, profile
    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile%20offline_access');

    // Act: no scope parameter → full allowed (subject ∩ resource = openid, profile)
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&resource=${resourceUri}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    // Assert: issued token has resource-allowed scope
    expect(response.body.access_token).toBeDefined();
    expect(response.body.scope).toBe('openid profile');

    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['scope']).toBe('openid profile');

    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${response.body.access_token}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);
    expect(introspectResponse.body.active).toBe(true);
    expect(introspectResponse.body.scope).toBe('openid profile');
  });

  it('should restrict scope to subject ∩ actor ∩ resource when delegation and resource are used', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainSubjectToken, obtainActorToken } = fixture;
    if (!obtainActorToken) throw new Error('Fixture has no obtainActorToken (delegation not enabled)');

    // Arrange: subject has openid, profile, offline_access; actor has openid, profile only; resource allows openid, profile
    const { accessToken: subjectAccessToken } = await obtainSubjectToken('openid%20profile%20offline_access');
    const { accessToken: actorAccessToken } = await obtainActorToken('openid%20profile');

    // Act: delegation with resource → allowed = (subject ∩ actor) ∩ resource = openid, profile
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorAccessToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&resource=${resourceUri}` +
        `&scope=openid%20profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    // Assert: issued token has openid, profile only (subject ∩ actor ∩ resource)
    expect(response.body.access_token).toBeDefined();
    expect(response.body.scope).toBe('openid profile');

    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['client_id']).toBe(mcpServer.clientId);
    expect(decoded.payload['scope']).toBe('openid profile');
    expect(decoded.payload['act']).toBeDefined();

    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${response.body.access_token}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);
    expect(introspectResponse.body.active).toBe(true);
    expect(introspectResponse.body.scope).toBe('openid profile');
  });
});
