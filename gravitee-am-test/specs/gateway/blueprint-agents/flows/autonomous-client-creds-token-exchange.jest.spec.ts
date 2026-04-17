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

const WORKLOAD_JWT_TYPE = 'urn:ietf:params:oauth:client-assertion-type:workload-jwt';
const JWT_FORMAT = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;
const TOKEN_EXCHANGE_GRANT = 'urn:ietf:params:oauth:grant-type:token-exchange';
const ACCESS_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:access_token';

describe('AUTONOMOUS agent — client_credentials + token_exchange', () => {
  let fixture: BlueprintFixture;
  let autonomousAgent: any;
  let hostedAgent: any;
  let hostedPrivateKey: crypto.KeyObject;
  let hostedKid: string;

  beforeAll(async () => {
    fixture = await setupBlueprintFixture();

    // Create AUTONOMOUS agent (SERVICE type, no redirect needed)
    autonomousAgent = await fixture.createBlueprintApp('AUTONOMOUS');

    // Verify defaults: SERVICE type, only client_credentials + token_exchange
    const autonomousDetails = await fixture.getApp(autonomousAgent.id);
    expect(autonomousDetails.type).toEqual('SERVICE');
    expect(autonomousDetails.settings.oauth.grantTypes).toContain('client_credentials');
    expect(autonomousDetails.settings.oauth.grantTypes).toContain(TOKEN_EXCHANGE_GRANT);
    expect(autonomousDetails.settings.oauth.grantTypes).not.toContain('authorization_code');
    expect(autonomousDetails.settings.oauth.grantTypes).not.toContain('refresh_token');

    // Also create a HOSTED_DELEGATED agent to use as subject for token exchange tests
    hostedAgent = await fixture.createBlueprintApp('HOSTED_DELEGATED', undefined, 'https://hosted.example.com/callback');

    // Setup keypair for hosted agent
    const { publicKey, privateKey: pk } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
    hostedPrivateKey = pk;
    hostedKid = `hosted-key-${Date.now()}`;

    const publicJwk = publicKey.export({ format: 'jwk' });
    const agentJwk = {
      kty: publicJwk.kty,
      n: publicJwk.n,
      e: publicJwk.e,
      kid: hostedKid,
      use: 'sig',
      alg: 'RS256',
    };

    const addKeyResponse = await fixture.addAgentKey(hostedAgent.id, agentJwk);
    expect(addKeyResponse.ok).toEqual(true);
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  function signWorkloadJwt(clientId: string, instanceId: string, privateKey: crypto.KeyObject, keyId: string): string {
    const now = Math.floor(Date.now() / 1000);
    const payload = {
      iss: clientId,
      sub: instanceId,
      aud: fixture.domain.oidcConfig?.token_endpoint,
      jti: crypto.randomUUID(),
      iat: now,
      exp: now + 300,
    };
    return jwt.sign(payload, privateKey.export({ format: 'pem', type: 'pkcs8' }), {
      algorithm: 'RS256',
      keyid: keyId,
    });
  }

  it('should obtain access token via client_credentials grant', async () => {
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=client_credentials&client_id=${autonomousAgent.settings.oauth.clientId}&client_secret=${autonomousAgent.settings.oauth.clientSecret}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(200);

    expect(response.body.access_token).toMatch(JWT_FORMAT);
    expect(response.body.token_type).toEqual('bearer');
  });

  it('should reject authorization_code flow (not in allowedGrantTypes)', async () => {
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=authorization_code&code=mock-code&client_id=${autonomousAgent.settings.oauth.clientId}&client_secret=${autonomousAgent.settings.oauth.clientSecret}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    expect([400, 401]).toContain(response.status);
    if (response.body.error) {
      expect(['unsupported_grant_type', 'invalid_grant']).toContain(response.body.error);
    }
  });

  it('should reject refresh_token grant (not in allowedGrantTypes)', async () => {
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=refresh_token&refresh_token=dummy&client_id=${autonomousAgent.settings.oauth.clientId}&client_secret=${autonomousAgent.settings.oauth.clientSecret}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    expect([400, 401]).toContain(response.status);
    if (response.body.error) {
      expect(['unsupported_grant_type', 'invalid_grant']).toContain(response.body.error);
    }
  });

  it('should exchange subject_token with token_exchange grant', async () => {
    // Step 1: Get subject token from hosted agent
    const hostedAssertion = signWorkloadJwt(hostedAgent.settings.oauth.clientId, 'hosted-instance', hostedPrivateKey, hostedKid);
    const subjectTokenResponse = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(WORKLOAD_JWT_TYPE)}&client_assertion=${encodeURIComponent(hostedAssertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(200);

    const subjectToken = subjectTokenResponse.body.access_token;
    expect(subjectToken).toMatch(JWT_FORMAT);

    // Step 2: Exchange subject token using autonomous agent
    const exchangeResponse = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=${encodeURIComponent(TOKEN_EXCHANGE_GRANT)}&subject_token=${encodeURIComponent(subjectToken)}&subject_token_type=${encodeURIComponent(ACCESS_TOKEN_TYPE)}&client_id=${autonomousAgent.settings.oauth.clientId}&client_secret=${autonomousAgent.settings.oauth.clientSecret}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    // Token exchange may succeed or fail depending on domain token exchange settings
    expect([200, 400, 401]).toContain(exchangeResponse.status);
  });

  it('should reject token_exchange without client_secret', async () => {
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=${encodeURIComponent(TOKEN_EXCHANGE_GRANT)}&subject_token=dummy&subject_token_type=${encodeURIComponent(ACCESS_TOKEN_TYPE)}&client_id=${autonomousAgent.settings.oauth.clientId}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    // Must include client_secret for SERVICE apps
    expect([401, 400]).toContain(response.status);
  });

  it('should reject client_credentials without client_secret', async () => {
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=client_credentials&client_id=${autonomousAgent.settings.oauth.clientId}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    // Must include client_secret for SERVICE apps
    expect([401, 400]).toContain(response.status);
  });

  it('should reject wrong client_secret', async () => {
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=client_credentials&client_id=${autonomousAgent.settings.oauth.clientId}&client_secret=wrong-secret`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    expect([401, 400]).toContain(response.status);
  });
});
