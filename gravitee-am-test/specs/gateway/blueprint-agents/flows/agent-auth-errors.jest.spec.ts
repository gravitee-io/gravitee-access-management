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
import { setup } from '../../../test-fixture';
import {
  setupBlueprintFixture,
  BlueprintFixture,
} from '../../../../specs/management/blueprint-application/fixtures/blueprint-fixture';
import crypto from 'crypto';
import jwt from 'jsonwebtoken';

setup(180000);

const WORKLOAD_JWT_TYPE = 'urn:ietf:params:oauth:client-assertion-type:workload-jwt';
const TOKEN_EXCHANGE_GRANT = 'urn:ietf:params:oauth:grant-type:token-exchange';
const ACCESS_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:access_token';

describe('Agent auth flows — cross-agent error handling', () => {
  let fixture: BlueprintFixture;
  let userEmbedded: any;
  let hostedDelegated: any;
  let autonomous: any;

  beforeAll(async () => {
    fixture = await setupBlueprintFixture();
    userEmbedded = await fixture.createBlueprintApp('USER_EMBEDDED', undefined, 'https://user-embedded.example.com');
    hostedDelegated = await fixture.createBlueprintApp('HOSTED_DELEGATED', undefined, 'https://hosted.example.com');
    autonomous = await fixture.createBlueprintApp('AUTONOMOUS');
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  it('should reject user-embedded agent attempting client_credentials flow', async () => {
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=client_credentials&client_id=${userEmbedded.settings.oauth.clientId}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    // USER_EMBEDDED app only allows authorization_code
    expect([400, 401]).toContain(response.status);
  });

  it('should reject user-embedded agent attempting token_exchange flow', async () => {
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=${encodeURIComponent(TOKEN_EXCHANGE_GRANT)}&subject_token=dummy&subject_token_type=${encodeURIComponent(ACCESS_TOKEN_TYPE)}&client_id=${userEmbedded.settings.oauth.clientId}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    expect([400, 401]).toContain(response.status);
    if (response.body.error) {
      expect(['unsupported_grant_type', 'invalid_grant']).toContain(response.body.error);
    }
  });

  it('should reject hosted-delegated agent using wrong secret in client_credentials', async () => {
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=client_credentials&client_id=${hostedDelegated.settings.oauth.clientId}&client_secret=wrong-secret`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    // Hosted agents require proper auth
    expect([401, 400]).toContain(response.status);
  });

  it('should reject autonomous agent without client credentials', async () => {
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=client_credentials&client_id=${autonomous.settings.oauth.clientId}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    // Must include client_secret
    expect([401, 400]).toContain(response.status);
  });

  it('should reject workload-jwt assertion with wrong issuer', async () => {
    // Setup a key for hostedDelegated
    const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
    const kid = `key-${Date.now()}`;

    const publicJwk = publicKey.export({ format: 'jwk' });
    const agentJwk = {
      kty: publicJwk.kty,
      n: publicJwk.n,
      e: publicJwk.e,
      kid,
      use: 'sig',
      alg: 'RS256',
    };

    const addKeyResponse = await fixture.addAgentKey(hostedDelegated.id, agentJwk);
    expect(addKeyResponse.ok).toEqual(true);

    // Sign assertion with WRONG issuer (autonomous agent's client_id, not hostedDelegated's)
    const now = Math.floor(Date.now() / 1000);
    const wrongIssuerAssertion = jwt.sign(
      {
        iss: autonomous.settings.oauth.clientId, // Wrong issuer!
        sub: 'instance-123',
        aud: fixture.domain.oidcConfig?.token_endpoint,
        jti: crypto.randomUUID(),
        iat: now,
        exp: now + 300,
      },
      privateKey.export({ format: 'pem', type: 'pkcs8' }),
      { algorithm: 'RS256', keyid: kid },
    );

    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(WORKLOAD_JWT_TYPE)}&client_assertion=${encodeURIComponent(wrongIssuerAssertion)}&client_id=${hostedDelegated.settings.oauth.clientId}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    // Should reject — issuer doesn't match client_id
    expect([400, 401]).toContain(response.status);
  });

  it('should reject workload-jwt assertion signed with key from different agent', async () => {
    // Setup a key on autonomous agent
    const { publicKey: autoKey, privateKey: autoPrivateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
    const autoKid = `auto-key-${Date.now()}`;

    const autoPublicJwk = autoKey.export({ format: 'jwk' });
    const autoAgentJwk = {
      kty: autoPublicJwk.kty,
      n: autoPublicJwk.n,
      e: autoPublicJwk.e,
      kid: autoKid,
      use: 'sig',
      alg: 'RS256',
    };

    // Create a separate hosted agent to receive the cross-signed assertion
    const hostedAgent2 = await fixture.createBlueprintApp('HOSTED_DELEGATED', undefined, 'https://hosted2.example.com');

    // Sign assertion from autonomous agent's private key, targeting hosted agent
    const now = Math.floor(Date.now() / 1000);
    const crossSignedAssertion = jwt.sign(
      {
        iss: hostedAgent2.settings.oauth.clientId,
        sub: 'instance-456',
        aud: fixture.domain.oidcConfig?.token_endpoint,
        jti: crypto.randomUUID(),
        iat: now,
        exp: now + 300,
      },
      autoPrivateKey.export({ format: 'pem', type: 'pkcs8' }),
      { algorithm: 'RS256', keyid: autoKid }, // Using autonomous agent's key ID
    );

    // Try to use it on hosted2 (which doesn't have autonomous agent's key)
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(WORKLOAD_JWT_TYPE)}&client_assertion=${encodeURIComponent(crossSignedAssertion)}&client_id=${hostedAgent2.settings.oauth.clientId}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    // Should reject — key ID not found in agent's JWKS
    expect([400, 401]).toContain(response.status);
  });

  it('should reject token_exchange with mismatched subject_token_type', async () => {
    // Attempt token exchange with wrong subject_token_type
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=${encodeURIComponent(TOKEN_EXCHANGE_GRANT)}&subject_token=dummy-jwt&subject_token_type=urn:ietf:params:oauth:token-type:jwt&client_id=${autonomous.settings.oauth.clientId}&client_secret=${autonomous.settings.oauth.clientSecret}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    // Should fail during token validation
    expect([400, 401]).toContain(response.status);
  });

  it('should reject client_credentials from user-embedded (public app trying to authenticate)', async () => {
    // USER_EMBEDDED apps have no client_secret and only support authorization_code
    const response = await performPost(
      fixture.domain.oidcConfig?.token_endpoint,
      '',
      `grant_type=client_credentials&client_id=${userEmbedded.settings.oauth.clientId}&client_secret=some-secret`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    expect([400, 401]).toContain(response.status);
  });
});
