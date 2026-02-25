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
    domainNamePrefix: 'te-mcp-res-scope',
    domainDescription: 'Token exchange MCP resource scope validation',
    appClientName: 'te-mcp-res-scope-app',
    mcpServerName: 'te-mcp-res-scope-server',
    mcpToolScopes: ['openid', 'profile'],
    mcpClientScopes: [
      { scope: 'openid', defaultScope: true },
      { scope: 'profile', defaultScope: true },
      { scope: 'offline_access', defaultScope: false },
    ],
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

  it('should succeed when requested scope includes client scope not in resource (client ∪ resource ∩ subject)', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainToken } = fixture;

    // Given: subject has openid, profile, offline_access; resource allows openid, profile; client has all three.
    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile%20offline_access');

    // When: exchange with resource and scope=openid profile offline_access.
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&resource=${resourceUri}&scope=openid%20profile%20offline_access`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    // Then: granted openid, profile, offline_access.
    expect(response.body.access_token).toBeDefined();
    expect(response.body.scope).toBeDefined();
    const responseScopeSet = new Set((response.body.scope as string).split(' '));
    expect(responseScopeSet.has('openid')).toBe(true);
    expect(responseScopeSet.has('profile')).toBe(true);
    expect(responseScopeSet.has('offline_access')).toBe(true);
    expect(responseScopeSet.size).toBe(3);

    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['client_id']).toBe(mcpServer.clientId);
    const issuedScopeSet = new Set((decoded.payload['scope'] as string).split(' '));
    expect(issuedScopeSet.has('openid')).toBe(true);
    expect(issuedScopeSet.has('profile')).toBe(true);
    expect(issuedScopeSet.has('offline_access')).toBe(true);
  });

  it('should succeed when requested scope is subset of resource scopes', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainToken } = fixture;

    // Given: subject has openid, profile, offline_access; resource allows openid, profile.
    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile%20offline_access');

    // When: exchange with resource and scope=openid profile.
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&resource=${resourceUri}&scope=openid%20profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    // Then: granted openid profile.
    expect(response.body.access_token).toBeDefined();
    expect(response.body.scope).toBe('openid profile');

    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['client_id']).toBe(mcpServer.clientId);
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

  it('should succeed when resource is set and scope is omitted (full allowed = (client ∪ resource) ∩ subject)', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = fixture;

    // Given: subject has openid, profile, offline_access; resource and client allow.
    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile%20offline_access');

    // When: exchange with resource, no scope param.
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&resource=${resourceUri}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    // Then: full allowed (openid, profile, offline_access).
    expect(response.body.access_token).toBeDefined();
    const responseScopeSet = new Set((response.body.scope as string).split(' '));
    expect(responseScopeSet.has('openid')).toBe(true);
    expect(responseScopeSet.has('profile')).toBe(true);
    expect(responseScopeSet.has('offline_access')).toBe(true);
    expect(responseScopeSet.size).toBe(3);

    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['scope']).toBeDefined();
    const issuedScopeSet = new Set((decoded.payload['scope'] as string).split(' '));
    expect(issuedScopeSet.has('openid')).toBe(true);
    expect(issuedScopeSet.has('profile')).toBe(true);
    expect(issuedScopeSet.has('offline_access')).toBe(true);

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
    expect(new Set((introspectResponse.body.scope as string).split(' ')).size).toBe(3);
  });

  it('should restrict scope to (client ∪ resource) ∩ (subject ∩ actor) when delegation and resource are used', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainToken } = fixture;

    // Given: subject openid, profile, offline_access; actor openid, profile; resource openid, profile.
    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile%20offline_access');
    const { accessToken: actorAccessToken } = await obtainToken('openid%20profile');

    // When: delegation with resource and scope=openid profile.
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

    // Then: granted openid profile; act claim present.
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

describe('Token Exchange - audience (aud) and resource parameter', () => {
  const resourceIdentifier = TOKEN_EXCHANGE_MCP_TEST.RESOURCE_IDENTIFIER;
  const resourceUri = encodeURIComponent(resourceIdentifier);

  /** Normalise aud (string or array) to array for assertions. */
  function audAsArray(aud: unknown): string[] {
    if (aud == null) return [];
    return Array.isArray(aud) ? (aud as string[]) : [aud as string];
  }

  it('should set issued token aud to resource URI(s) when resource parameter is present', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = fixture;

    // Given: subject token.
    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile');

    // When: exchange with resource param.
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&resource=${resourceUri}&scope=openid%20profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    // Then: aud contains resource URI.
    expect(response.body.access_token).toBeDefined();
    const decoded = parseJwt(response.body.access_token);
    const jwtAud = audAsArray(decoded.payload['aud']);
    expect(jwtAud).toContain(resourceIdentifier);

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
    const introspectAud = audAsArray(introspectResponse.body.aud);
    expect(introspectAud).toContain(resourceIdentifier);
  });

  it('when resource parameter is absent, aud is client_id and does not contain resource URI', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainToken } = fixture;

    // Given: subject token.
    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile');

    // When: exchange without resource param.
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${subjectAccessToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token&scope=openid%20profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    // Then: aud is client_id; no resource URI.
    expect(response.body.access_token).toBeDefined();
    const decoded = parseJwt(response.body.access_token);
    const jwtAud = audAsArray(decoded.payload['aud']);
    expect(jwtAud).toContain(mcpServer.clientId);
    expect(jwtAud).not.toContain(resourceIdentifier);

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
    expect(introspectResponse.body.client_id).toBe(mcpServer.clientId);
    const introspectAud = audAsArray(introspectResponse.body.aud);
    expect(introspectAud).toContain(mcpServer.clientId);
  });
});
