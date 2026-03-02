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

setup(300000);

/**
 * Scope handling integration tests for both DOWNSCOPING and PERMISSIVE modes.
 *
 * Two MCP Server fixtures are created — one per mode — so that each describe block
 * exercises the same scopes under different server configurations:
 *
 *   Application scopes: openid, profile, offline_access   (password grant → subject tokens)
 *   MCP Server client scopes: openid, profile, offline_access
 *   MCP Server resource tool scopes: openid, profile  (only these two on the resource)
 *
 * DOWNSCOPING (default): allowed = (client ∪ resource) ∩ subject_token_scopes
 * PERMISSIVE:            allowed = client ∪ resource   (subject token scopes are irrelevant)
 */

const SHARED_FIXTURE_CONFIG = {
  scopes: TOKEN_EXCHANGE_MCP_TEST.DEFAULT_SCOPES,
  mcpClientScopes: [
    { scope: 'openid', defaultScope: true },
    { scope: 'profile', defaultScope: true },
    { scope: 'offline_access', defaultScope: false },
  ],
  mcpToolScopes: ['openid', 'profile'],
  allowDelegation: true,
  maxDelegationDepth: 1,
};

let downscopingFixture: TokenExchangeMcpFixture;
let permissiveFixture: TokenExchangeMcpFixture;

beforeAll(async () => {
  [downscopingFixture, permissiveFixture] = await Promise.all([
    setupTokenExchangeMcpFixture({
      ...SHARED_FIXTURE_CONFIG,
      domainNamePrefix: 'te-mcp-scope-downscoping',
      domainDescription: 'Token exchange MCP scope handling – downscoping',
      appClientName: 'te-mcp-scope-downscoping-app',
      mcpServerName: 'te-mcp-scope-downscoping-server',
      // tokenExchangeOAuthSettings omitted → defaults to DOWNSCOPING (inherited from domain)
    }),
    setupTokenExchangeMcpFixture({
      ...SHARED_FIXTURE_CONFIG,
      domainNamePrefix: 'te-mcp-scope-permissive',
      domainDescription: 'Token exchange MCP scope handling – permissive',
      appClientName: 'te-mcp-scope-permissive-app',
      mcpServerName: 'te-mcp-scope-permissive-server',
      tokenExchangeOAuthSettings: { inherited: false, scopeHandling: 'permissive' },
    }),
  ]);
});

afterAll(async () => {
  await Promise.allSettled([downscopingFixture?.cleanup(), permissiveFixture?.cleanup()]);
});

// ---------------------------------------------------------------------------
// DOWNSCOPING – allowed = (client ∪ resource) ∩ subject_token_scopes
// ---------------------------------------------------------------------------

describe('DOWNSCOPING – scope handling (allowed = (client ∪ resource) ∩ subject)', () => {
  const resourceUri = encodeURIComponent(TOKEN_EXCHANGE_MCP_TEST.RESOURCE_IDENTIFIER);

  it('should grant full (client ∪ resource) ∩ subject when no scope is requested', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = downscopingFixture;

    // Subject has openid, profile, offline_access; all are in client and ≥ in resource → grant all.
    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile%20offline_access');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&resource=${resourceUri}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    expect(response.body.access_token).toBeDefined();
    const scopeSet = new Set((response.body.scope as string).split(' '));
    expect(scopeSet.has('openid')).toBe(true);
    expect(scopeSet.has('profile')).toBe(true);
    expect(scopeSet.has('offline_access')).toBe(true);
    expect(scopeSet.size).toBe(3);

    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${response.body.access_token}`,
      { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${mcpServerBasicAuth}` },
    ).expect(200);
    expect(introspectResponse.body.active).toBe(true);
    expect(new Set((introspectResponse.body.scope as string).split(' ')).size).toBe(3);
  });

  it('should grant requested scope that is in subject, client, and resource', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainToken } = downscopingFixture;

    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile%20offline_access');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&resource=${resourceUri}` +
        `&scope=openid%20profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    expect(response.body.scope).toBe('openid profile');

    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['client_id']).toBe(mcpServer.clientId);
    expect(decoded.payload['scope']).toBe('openid profile');

    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${response.body.access_token}`,
      { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${mcpServerBasicAuth}` },
    ).expect(200);
    expect(introspectResponse.body.active).toBe(true);
    expect(introspectResponse.body.scope).toBe('openid profile');
  });

  it('should grant scope in client but not in resource (pool = client ∪ resource)', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = downscopingFixture;

    // Subject has all three; resource has only openid+profile; offline_access comes from client.
    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile%20offline_access');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&resource=${resourceUri}` +
        `&scope=openid%20profile%20offline_access`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    const scopeSet = new Set((response.body.scope as string).split(' '));
    expect(scopeSet.has('openid')).toBe(true);
    expect(scopeSet.has('profile')).toBe(true);
    expect(scopeSet.has('offline_access')).toBe(true);
    expect(scopeSet.size).toBe(3);
  });

  it('should reject scope in resource but absent from subject token', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = downscopingFixture;

    // Subject has only openid — profile is in resource but not in subject → reject.
    const { accessToken: subjectAccessToken } = await obtainToken('openid');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&resource=${resourceUri}` +
        `&scope=profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_scope');
  });

  it('should restrict delegation to (client ∪ resource) ∩ (subject ∩ actor)', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainToken } = downscopingFixture;

    // Subject openid+profile+offline_access; actor openid+profile; resource openid+profile.
    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile%20offline_access');
    const { accessToken: actorAccessToken } = await obtainToken('openid%20profile');

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

    expect(response.body.scope).toBe('openid profile');

    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['client_id']).toBe(mcpServer.clientId);
    expect(decoded.payload['scope']).toBe('openid profile');
    expect(decoded.payload['act']).toBeDefined();

    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${response.body.access_token}`,
      { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${mcpServerBasicAuth}` },
    ).expect(200);
    expect(introspectResponse.body.active).toBe(true);
    expect(introspectResponse.body.scope).toBe('openid profile');
  });
});

// ---------------------------------------------------------------------------
// PERMISSIVE – allowed = client ∪ resource  (subject token scopes are irrelevant)
// ---------------------------------------------------------------------------

describe('PERMISSIVE – scope handling (allowed = client ∪ resource, subject scopes irrelevant)', () => {
  const resourceUri = encodeURIComponent(TOKEN_EXCHANGE_MCP_TEST.RESOURCE_IDENTIFIER);

  /**
   * Core distinguishing test: subject has only 'openid'; client has 'openid, profile, offline_access'.
   * DOWNSCOPING → profile not in subject → 400.
   * PERMISSIVE  → profile ∈ client pool → 200.
   */
  it('should grant scope in client even when absent from subject token', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = permissiveFixture;

    const { accessToken: subjectAccessToken } = await obtainToken('openid');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&scope=profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    expect(response.body.scope).toBe('profile');
    expect(parseJwt(response.body.access_token).payload['scope']).toBe('profile');
  });

  it('should grant all client scopes when no scope is requested, regardless of narrow subject token', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = permissiveFixture;

    // Narrow subject: only openid. Permissive must still grant the full client pool.
    const { accessToken: subjectAccessToken } = await obtainToken('openid');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    const scopeSet = new Set((response.body.scope as string).split(' '));
    expect(scopeSet.has('openid')).toBe(true);
    expect(scopeSet.has('profile')).toBe(true);
    expect(scopeSet.has('offline_access')).toBe(true);
    expect(scopeSet.size).toBe(3);

    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${response.body.access_token}`,
      { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${mcpServerBasicAuth}` },
    ).expect(200);
    expect(introspectResponse.body.active).toBe(true);
    expect(new Set((introspectResponse.body.scope as string).split(' ')).size).toBe(3);
  });

  it('should grant scope from resource even when absent from subject token', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = permissiveFixture;

    // Subject has only openid; profile is in resource; request profile → granted.
    const { accessToken: subjectAccessToken } = await obtainToken('openid');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&resource=${resourceUri}` +
        `&scope=profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    expect(response.body.scope).toBe('profile');
    expect(parseJwt(response.body.access_token).payload['scope']).toBe('profile');
  });

  it('should reject scope absent from both client and resource', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = permissiveFixture;

    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile');

    // 'some_unknown_scope' is not in client or resource pool.
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&scope=some_unknown_scope`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_scope');
  });

  it('should grant all client scopes in delegation even when subject ∩ actor is empty', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = permissiveFixture;

    // Subject: only openid; actor: only profile → intersection = {}.
    // DOWNSCOPING would grant nothing; PERMISSIVE grants full client pool.
    const { accessToken: subjectAccessToken } = await obtainToken('openid');
    const { accessToken: actorAccessToken } = await obtainToken('profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorAccessToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    const scopeSet = new Set((response.body.scope as string).split(' '));
    expect(scopeSet.has('openid')).toBe(true);
    expect(scopeSet.has('profile')).toBe(true);
    expect(scopeSet.has('offline_access')).toBe(true);

    expect(parseJwt(response.body.access_token).payload['act']).toBeDefined();
  });

  it('should grant requested scope in delegation even when actor token lacks it', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = permissiveFixture;

    // Subject openid+profile; actor openid only; request profile.
    // DOWNSCOPING: intersection = {openid} → profile not in intersection → 400.
    // PERMISSIVE: pool = client = {openid,profile,offline_access} → profile granted.
    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile');
    const { accessToken: actorAccessToken } = await obtainToken('openid');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${actorAccessToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&scope=profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    expect(response.body.scope).toBe('profile');
    expect(parseJwt(response.body.access_token).payload['act']).toBeDefined();
  });
});

// ---------------------------------------------------------------------------
// Resource parameter – audience (aud) claim  (mode-independent)
// ---------------------------------------------------------------------------

describe('Resource parameter – audience (aud) claim', () => {
  const resourceIdentifier = TOKEN_EXCHANGE_MCP_TEST.RESOURCE_IDENTIFIER;
  const resourceUri = encodeURIComponent(resourceIdentifier);

  function audAsArray(aud: unknown): string[] {
    if (aud == null) return [];
    return Array.isArray(aud) ? (aud as string[]) : [aud as string];
  }

  it('should set aud to resource URI when resource parameter is present', async () => {
    const { oidc, mcpServerBasicAuth, obtainToken } = downscopingFixture;

    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&resource=${resourceUri}` +
        `&scope=openid%20profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    const jwtAud = audAsArray(parseJwt(response.body.access_token).payload['aud']);
    expect(jwtAud).toContain(resourceIdentifier);

    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${response.body.access_token}`,
      { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${mcpServerBasicAuth}` },
    ).expect(200);
    expect(introspectResponse.body.active).toBe(true);
    expect(audAsArray(introspectResponse.body.aud)).toContain(resourceIdentifier);
  });

  it('should set aud to client_id and not contain resource URI when resource parameter is absent', async () => {
    const { oidc, mcpServer, mcpServerBasicAuth, obtainToken } = downscopingFixture;

    const { accessToken: subjectAccessToken } = await obtainToken('openid%20profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectAccessToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&scope=openid%20profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${mcpServerBasicAuth}`,
      },
    ).expect(200);

    const jwtAud = audAsArray(parseJwt(response.body.access_token).payload['aud']);
    expect(jwtAud).toContain(mcpServer.clientId);
    expect(jwtAud).not.toContain(resourceIdentifier);

    const introspectResponse = await performPost(
      oidc.introspection_endpoint,
      '',
      `token=${response.body.access_token}`,
      { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${mcpServerBasicAuth}` },
    ).expect(200);
    expect(introspectResponse.body.active).toBe(true);
    expect(introspectResponse.body.client_id).toBe(mcpServer.clientId);
    expect(audAsArray(introspectResponse.body.aud)).toContain(mcpServer.clientId);
  });
});
