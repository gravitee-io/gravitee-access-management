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
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { createConsentGatewayFixture, createCustomIdpGatewayFixture, createExtensionGrantGatewayFixture, createGatewayFixture, GatewayFixture } from './fixture/gateway-fixture';
import { EXT_GRANT_ASSERTION_JWT, EXT_GRANT_ASSERTION_SUB, getDataPlaneTargets, getInstanceLabel } from '../../migration-seeding/seed';
import { setup } from '../test-fixture';

setup(60000);

const channelLabel = process.env.AM_MIGRATION_TEST_LABEL || 'alpha';

// Run the full gateway suite against every configured data plane (its own domain + gateway URL).
describe.each(getDataPlaneTargets())('migration Gateway data [data plane $id]', ({ id, gatewayUrl }) => {
  const label = getInstanceLabel(channelLabel, id);
  let fixture: GatewayFixture;
  let consentFixture: GatewayFixture;
  let customIdpFixture: GatewayFixture;
  let extGrantFixture: GatewayFixture;
  let previousGatewayUrl: string | undefined;

  beforeAll(async () => {
    // Point the gateway commands (well-known, authorize, login) at this data plane's gateway.
    previousGatewayUrl = process.env.AM_GATEWAY_URL;
    process.env.AM_GATEWAY_URL = gatewayUrl;
    fixture = await createGatewayFixture(label);
    consentFixture = await createConsentGatewayFixture(label);
    customIdpFixture = await createCustomIdpGatewayFixture(label);
    extGrantFixture = await createExtensionGrantGatewayFixture(label);
  });

  afterAll(() => {
    if (previousGatewayUrl === undefined) {
      delete process.env.AM_GATEWAY_URL;
    } else {
      process.env.AM_GATEWAY_URL = previousGatewayUrl;
    }
  });

  it('logs in the user and redirects to an MFA step', async () => {
    const postLogin = await fixture.loginUser();
    const authorize = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    expect(authorize.headers['location']).toContain('/mfa/');
  });

  it('logs in with MFA, creates a token and introspects it', async () => {
    const finalAuthorizeResponse = await fixture.loginUserWithMfa();
    const tokenResponse = await fixture.requestToken(finalAuthorizeResponse);
    const introspectionResponse = await fixture.introspectToken(tokenResponse.body.access_token);

    expect(tokenResponse.body.access_token).toBeDefined();
    expect(tokenResponse.body.token_type).toBe('bearer');
    expect(introspectionResponse.active).toBe(true);
    expect(introspectionResponse.domain).toBe(fixture.domain.id);
    expect(introspectionResponse.sub).toBeDefined();
  });

  it('grants consent for a requested scope and creates a token carrying it', async () => {
    const finalAuthorizeResponse = await consentFixture.loginUserAndConsent();
    const tokenResponse = await consentFixture.requestToken(finalAuthorizeResponse);
    const introspectionResponse = await consentFixture.introspectToken(tokenResponse.body.access_token);

    expect(tokenResponse.body.access_token).toBeDefined();
    expect(introspectionResponse.active).toBe(true);
    expect(introspectionResponse.scope).toContain('email');
  });

  it('logs in a user backed by the custom identity provider and creates a token', async () => {
    const finalAuthorizeResponse = await customIdpFixture.loginUserWithMfa();
    const tokenResponse = await customIdpFixture.requestToken(finalAuthorizeResponse);
    const introspectionResponse = await customIdpFixture.introspectToken(tokenResponse.body.access_token);

    expect(tokenResponse.body.access_token).toBeDefined();
    expect(tokenResponse.body.token_type).toBe('bearer');
    expect(introspectionResponse.active).toBe(true);
    expect(introspectionResponse.domain).toBe(customIdpFixture.domain.id);
    expect(introspectionResponse.sub).toBeDefined();
  });

  it('exchanges a JWT assertion for a token via the seeded jwt-bearer extension grant and introspects it', async () => {
    const tokenResponse = await extGrantFixture.requestJwtBearerToken(EXT_GRANT_ASSERTION_JWT);
    const introspectionResponse = await extGrantFixture.introspectToken(tokenResponse.body.access_token);

    expect(tokenResponse.body.access_token).toBeDefined();
    expect(introspectionResponse.active).toBe(true);
    expect(introspectionResponse.domain).toBe(extGrantFixture.domain.id);
    expect(introspectionResponse.sub).toBe(EXT_GRANT_ASSERTION_SUB);
  });
});
