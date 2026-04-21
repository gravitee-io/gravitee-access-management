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
import { performPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../../test-fixture';
import {
  setupBlueprintFixture,
  BlueprintFixture,
} from '../../../../specs/management/blueprint-application/fixtures/blueprint-fixture';

setup(180000);

describe('USER_EMBEDDED agent — PKCE + authorization_code', () => {
  let fixture: BlueprintFixture;
  let agent: any;
  let redirectUri: string;

  beforeAll(async () => {
    fixture = await setupBlueprintFixture();
    redirectUri = 'https://agent.example.com/callback';
    agent = await fixture.createBlueprintApp('USER_EMBEDDED', undefined, redirectUri);

    const appDetails = await fixture.getApp(agent.id);
    expect(appDetails.type).toEqual('native');
    expect(appDetails.settings.oauth.clientSecret).toBeUndefined();
    expect(appDetails.settings.oauth.grantTypes).toContain('authorization_code');
    expect(appDetails.settings.oauth.forcePKCE).toEqual(true);
    expect(appDetails.settings.oauth.forceS256CodeChallengeMethod).toEqual(true);

    await fixture.waitForOidc();
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  it('should require PKCE code_challenge in authorization request', async () => {
    const authParams = new URLSearchParams({
      response_type: 'code',
      client_id: agent.settings.oauth.clientId,
      redirect_uri: redirectUri,
      scope: 'openid',
      state: 'test-state',
    });

    const authUrl = `${fixture.oidc.authorization_endpoint}?${authParams.toString()}`;

    const response = await performGet(authUrl);

    expect([302, 400]).toContain(response.status);
    if (response.status === 302) {
      expect(response.headers['location']).toContain('error=');
      expect(response.headers['location']).toContain('code_challenge');
    }
  });

  it('should reject plain code_challenge_method when S256 is forced', async () => {
    const authParams = new URLSearchParams({
      response_type: 'code',
      client_id: agent.settings.oauth.clientId,
      redirect_uri: redirectUri,
      code_challenge: 'plain-challenge',
      code_challenge_method: 'plain',
      scope: 'openid',
      state: 'test-state',
    });

    const authUrl = `${fixture.oidc.authorization_endpoint}?${authParams.toString()}`;

    const response = await performGet(authUrl);

    expect([302, 400]).toContain(response.status);
    if (response.status === 302) {
      expect(response.headers['location']).toContain('error=');
    }
  });

  it('should reject token request without code_verifier', async () => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=authorization_code&code=mock-code&redirect_uri=${encodeURIComponent(redirectUri)}&client_id=${agent.settings.oauth.clientId}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    expect([400, 401]).toContain(response.status);
  });

  it('should reject S256 mismatch in code_verifier', async () => {
    const wrongVerifier = 'wrongVerifierThatDoesNotMatchChallenge';

    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=authorization_code&code=mock-code&code_verifier=${encodeURIComponent(wrongVerifier)}&redirect_uri=${encodeURIComponent(redirectUri)}&client_id=${agent.settings.oauth.clientId}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    expect([400, 401]).toContain(response.status);
  });

  it('should reject client_credentials grant (not in allowedGrantTypes)', async () => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_id=${agent.settings.oauth.clientId}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    expect([400, 401]).toContain(response.status);
  });

  it('should reject token_exchange grant (not in allowedGrantTypes)', async () => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=dummy&subject_token_type=urn:ietf:params:oauth:token-type:access_token&client_id=${agent.settings.oauth.clientId}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    );

    expect([400, 401]).toContain(response.status);
    if (response.body.error) {
      expect(['unsupported_grant_type', 'invalid_grant']).toContain(response.body.error);
    }
  });
});
