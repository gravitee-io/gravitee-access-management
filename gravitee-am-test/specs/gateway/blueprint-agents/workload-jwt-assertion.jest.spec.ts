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
import { setup } from '../../test-fixture';
import { WorkloadJwtFixture, setupWorkloadJwtFixture } from './fixtures/workload-jwt-fixture';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';

setup(120000);

const WORKLOAD_JWT_TYPE = 'urn:ietf:params:oauth:client-assertion-type:workload-jwt';
const JWT_FORMAT = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

describe('Blueprint Agent — Workload-JWT Assertion', () => {
  let fixture: WorkloadJwtFixture;

  beforeAll(async () => {
    fixture = await setupWorkloadJwtFixture();
  });

  afterAll(async () => {
    await fixture?.cleanUp();
  });

  function signWorkloadJwt(
    instanceId: string,
    overrides: Record<string, any> = {},
    keyOverride?: { privateKey: crypto.KeyObject; kid: string },
  ): string {
    const now = Math.floor(Date.now() / 1000);
    const payload = {
      iss: fixture.blueprintClientId,
      sub: instanceId,
      aud: fixture.oidc.token_endpoint,
      jti: crypto.randomUUID(),
      iat: now,
      exp: now + 300,
      ...overrides,
    };
    const key = keyOverride?.privateKey ?? fixture.privateKey;
    const kid = keyOverride?.kid ?? fixture.kid;
    return jwt.sign(payload, key.export({ format: 'pem', type: 'pkcs8' }), {
      algorithm: 'RS256',
      keyid: kid,
    });
  }

  it('should obtain token with valid workload-jwt assertion', async () => {
    const agentInstanceId = 'agent-instance-001';
    const assertion = signWorkloadJwt(agentInstanceId);

    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(WORKLOAD_JWT_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(200);

    expect(response.body.access_token).toMatch(JWT_FORMAT);
    expect(response.body.token_type).toEqual('bearer');

    // Decode and verify token claims
    const decoded = decodeJwt(response.body.access_token);
    expect(decoded.sub).toEqual(agentInstanceId);
    expect(decoded.act).toBeDefined();
    expect((decoded.act as any).sub).toEqual(fixture.blueprintClientId);
  });

  it('should reject workload-jwt with invalid signature', async () => {
    // Generate a different keypair — signature won't match blueprint JWKS
    const { privateKey: wrongKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });

    const assertion = signWorkloadJwt('agent-instance-bad-sig', {}, { privateKey: wrongKey, kid: fixture.kid });

    await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(WORKLOAD_JWT_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(401);
  });

  it('should reject workload-jwt with unknown kid', async () => {
    const assertion = signWorkloadJwt('agent-instance-unknown-kid', {}, { privateKey: fixture.privateKey, kid: 'nonexistent-kid' });

    await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(WORKLOAD_JWT_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(401);
  });

  it('should reject expired workload-jwt assertion', async () => {
    const assertion = signWorkloadJwt('agent-instance-expired', {
      iat: Math.floor(Date.now() / 1000) - 600,
      exp: Math.floor(Date.now() / 1000) - 300,
    });

    await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(WORKLOAD_JWT_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(401);
  });

  it('should allow different agent instance IDs with the same blueprint', async () => {
    const instances = ['instance-alpha', 'instance-beta', 'instance-gamma'];

    for (const instanceId of instances) {
      const assertion = signWorkloadJwt(instanceId);

      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(WORKLOAD_JWT_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
        { 'Content-type': 'application/x-www-form-urlencoded' },
      ).expect(200);

      const decoded = decodeJwt(response.body.access_token);
      expect(decoded.sub).toEqual(instanceId);
      expect((decoded.act as any).sub).toEqual(fixture.blueprintClientId);
    }
  });
});
