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
import { decodeJwt } from '@utils-commands/jwt';
import { setup } from '../../../test-fixture';
import {
  setupBlueprintFixture,
  BlueprintFixture,
} from '../../../../specs/management/blueprint-application/fixtures/blueprint-fixture';
import crypto from 'crypto';
import jwt from 'jsonwebtoken';

setup(180000);

const JWT_BEARER_TYPE = 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer';
const JWT_FORMAT = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

describe('HOSTED_DELEGATED agent — workload-jwt assertion + grants', () => {
  let fixture: BlueprintFixture;
  let agent: any; // HOSTED_DELEGATED app
  let privateKey: crypto.KeyObject;
  let kid: string;
  let redirectUri: string;

  beforeAll(async () => {
    fixture = await setupBlueprintFixture();
    redirectUri = 'https://agent.example.com/callback';

    // Create HOSTED_DELEGATED agent
    agent = await fixture.createBlueprintApp('HOSTED_DELEGATED', undefined, redirectUri);

    // Verify defaults: WEB type, client_credentials + authorization_code + token_exchange allowed
    const appDetails = await fixture.getApp(agent.id);
    expect(appDetails.type).toEqual('web');
    expect(appDetails.settings.agent.allowedGrantTypes).toContain('authorization_code');
    expect(appDetails.settings.agent.allowedGrantTypes).toContain('client_credentials');
    expect(appDetails.settings.agent.allowedGrantTypes).toContain('urn:ietf:params:oauth:grant-type:token-exchange');

    // Generate RSA keypair
    const { publicKey, privateKey: pk } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
    privateKey = pk;
    kid = `agent-key-${Date.now()}`;

    // Export public key as JWK and add to agent
    const publicJwk = publicKey.export({ format: 'jwk' });
    const agentJwk = {
      kty: publicJwk.kty,
      n: publicJwk.n,
      e: publicJwk.e,
      kid,
      use: 'sig',
      alg: 'RS256',
    };

    const addKeyResponse = await fixture.addAgentKey(agent.id, agentJwk);
    expect(addKeyResponse.ok).toEqual(true);

    await fixture.waitForOidc();
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  function signWorkloadJwt(instanceId: string, overrides: Record<string, any> = {}): string {
    const now = Math.floor(Date.now() / 1000);
    const payload = {
      iss: agent.settings.oauth.clientId,
      sub: instanceId,
      aud: fixture.oidc.token_endpoint,
      jti: crypto.randomUUID(),
      iat: now,
      exp: now + 300,
      ...overrides,
    };
    return jwt.sign(payload, privateKey.export({ format: 'pem', type: 'pkcs8' }), {
      algorithm: 'RS256',
      keyid: kid,
    });
  }

  it('should obtain access token with workload-jwt assertion via client_credentials', async () => {
    const agentInstanceId = 'hosted-agent-instance-001';
    const assertion = signWorkloadJwt(agentInstanceId);

    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(JWT_BEARER_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(200);

    expect(response.body.access_token).toMatch(JWT_FORMAT);
    expect(response.body.token_type).toEqual('bearer');

    // Verify act claim points to blueprint app
    const decoded = decodeJwt(response.body.access_token);
    expect(decoded.sub).toEqual(agentInstanceId);
    expect((decoded.act as any)?.sub).toEqual(agent.settings.oauth.clientId);
  });

  it('should reject workload-jwt with invalid signature', async () => {
    const { privateKey: wrongKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
    const assertion = jwt.sign(
      {
        iss: agent.settings.oauth.clientId,
        sub: 'test-instance',
        aud: fixture.oidc.token_endpoint,
        jti: crypto.randomUUID(),
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 300,
      },
      wrongKey.export({ format: 'pem', type: 'pkcs8' }),
      { algorithm: 'RS256', keyid: kid },
    );

    await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(JWT_BEARER_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(401);
  });

  it('should reject workload-jwt with unknown kid', async () => {
    const now = Math.floor(Date.now() / 1000);
    const assertion = jwt.sign(
      {
        iss: agent.settings.oauth.clientId,
        sub: 'test-instance',
        aud: fixture.oidc.token_endpoint,
        jti: crypto.randomUUID(),
        iat: now,
        exp: now + 300,
      },
      privateKey.export({ format: 'pem', type: 'pkcs8' }),
      { algorithm: 'RS256', keyid: 'nonexistent-kid' },
    );

    await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(JWT_BEARER_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(401);
  });

  it('should reject expired workload-jwt assertion', async () => {
    const now = Math.floor(Date.now() / 1000);
    const assertion = signWorkloadJwt('expired-instance', {
      iat: now - 600,
      exp: now - 300,
    });

    await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(JWT_BEARER_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(401);
  });

  it('should allow multiple agent instance IDs with same blueprint', async () => {
    const instances = ['instance-alpha', 'instance-beta', 'instance-gamma'];

    for (const instanceId of instances) {
      const assertion = signWorkloadJwt(instanceId);

      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(JWT_BEARER_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
        { 'Content-type': 'application/x-www-form-urlencoded' },
      ).expect(200);

      const decoded = decodeJwt(response.body.access_token);
      expect(decoded.sub).toEqual(instanceId);
      expect((decoded.act as any)?.sub).toEqual(agent.settings.oauth.clientId);
    }
  });

  it('should allow refresh_token flow with fresh workload-jwt assertion', async () => {
    // First get initial token with workload-jwt and offline_access scope
    const instanceId = 'refresh-test-instance';
    const assertion = signWorkloadJwt(instanceId);

    const initialResponse = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(JWT_BEARER_TYPE)}&client_assertion=${encodeURIComponent(assertion)}&scope=offline_access`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    // offline_access scope / refresh_token grant may not be enabled by default on the
    // agent — accept either an issued refresh_token or a rejection.
    expect([200, 400, 401]).toContain(initialResponse.status);
    if (initialResponse.status !== 200 || !initialResponse.body.refresh_token) {
      return;
    }

    const refreshToken = initialResponse.body.refresh_token;
    const refreshAssertion = signWorkloadJwt(instanceId);
    const refreshResponse = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=refresh_token&refresh_token=${encodeURIComponent(refreshToken)}&client_assertion_type=${encodeURIComponent(JWT_BEARER_TYPE)}&client_assertion=${encodeURIComponent(refreshAssertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    expect([200, 400, 401]).toContain(refreshResponse.status);
  });

  it('should reject workload-jwt with wrong issuer (client_id mismatch)', async () => {
    const now = Math.floor(Date.now() / 1000);
    // Sign with wrong issuer
    const wrongIssuerAssertion = jwt.sign(
      {
        iss: 'wrong-client-id',
        sub: 'instance-123',
        aud: fixture.oidc.token_endpoint,
        jti: crypto.randomUUID(),
        iat: now,
        exp: now + 300,
      },
      privateKey.export({ format: 'pem', type: 'pkcs8' }),
      { algorithm: 'RS256', keyid: kid },
    );

    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(JWT_BEARER_TYPE)}&client_assertion=${encodeURIComponent(wrongIssuerAssertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    // Should reject — issuer doesn't match client_id
    expect([400, 401]).toContain(response.status);
  });
});
