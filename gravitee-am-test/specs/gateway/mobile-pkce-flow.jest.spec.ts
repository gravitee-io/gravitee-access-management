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

import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import fetch from 'cross-fetch';

// Gateway commands
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';

// Fixtures
import { 
  setupMobilePKCEFixture, 
  validateTokenResponse,
  MobilePKCEFixture,
  TEST_CONSTANTS,
  parseErrorFromLocation
} from './fixtures/mobile-pkce-fixture';

// Global setup
global.fetch = fetch;
jest.setTimeout(200000);

// Test constants
const MOBILE_REDIRECT_URI = 'net.openid.appauthdemo:/oauth2redirect';
const CODE_VERIFIER = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';
const CODE_CHALLENGE = 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM';

// Test fixture
let fixture: MobilePKCEFixture;

beforeAll(async () => {
  fixture = await setupMobilePKCEFixture(MOBILE_REDIRECT_URI);
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Mobile PKCE Flow', () => {
  it('should complete PKCE flow with custom URI scheme', async () => {

    const authCode = await fixture.completeAuthorizationFlow(CODE_CHALLENGE);
    const tokenResponse = await fixture.exchangeCodeForToken(authCode, CODE_VERIFIER).expect(200);

    validateTokenResponse(tokenResponse);
  });

  describe('PKCE Error Scenarios', () => {
    it('should fail when missing code_challenge parameter', async () => {
      const authUrl = fixture.buildInvalidAuthUrl({});

      const response = await performGet(authUrl).expect(302);
      expect(response.headers['location']).toContain(fixture.redirectUri);
      
      const { error, errorDescription } = parseErrorFromLocation(response.headers['location']);
      expect(error).toBe('invalid_request');
      expect(errorDescription).toContain('Missing parameter: code_challenge');
    });

    it('should fail when using plain code_challenge_method when S256 is forced', async () => {
      const authUrl = fixture.buildInvalidAuthUrl({
        code_challenge: 'plain-challenge',
        code_challenge_method: 'plain',
      });

      const response = await performGet(authUrl).expect(302);
      expect(response.headers['location']).toContain(fixture.redirectUri);
      
      const { error, errorDescription } = parseErrorFromLocation(response.headers['location']);
      expect(error).toBe('invalid_request');
      expect(errorDescription).toContain('Invalid parameter: code_challenge_method');
    });

    it('should fail when missing code_verifier in token exchange', async () => {
      const authCode = await fixture.completeAuthorizationFlow(CODE_CHALLENGE);

      const tokenParams = new URLSearchParams({
        grant_type: 'authorization_code',
        code: authCode,
        redirect_uri: fixture.redirectUri,
        // Missing code_verifier
      });

      const response = await performPost(
        fixture.openIdConfiguration.token_endpoint,
        '',
        tokenParams.toString(),
        {
          'Content-Type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${applicationBase64Token(fixture.application)}`,
        }
      ).expect(400);

      expect(response.body.error).toBe('invalid_grant');
      expect(response.body.error_description).toContain('code_verifier');
    });

    it('should fail when code_verifier does not match code_challenge', async () => {
      const codeVerifier = 'wrong-verifier-that-does-not-match-challenge';
      const authCode = await fixture.completeAuthorizationFlow(CODE_CHALLENGE);

      const tokenParams = new URLSearchParams({
        grant_type: 'authorization_code',
        code: authCode,
        redirect_uri: fixture.redirectUri,
        code_verifier: codeVerifier,
      });

      const response = await performPost(
        fixture.openIdConfiguration.token_endpoint,
        '',
        tokenParams.toString(),
        {
          'Content-Type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${applicationBase64Token(fixture.application)}`,
        }
      ).expect(400);

      expect(response.body.error).toBe('invalid_grant');
      expect(response.body.error_description).toContain('code_verifier');
    });
  });

  describe('Redirect URI Validation Scenarios', () => {
    it('should fail with unregistered custom URI scheme', async () => {
      const unregisteredUri = 'com.unauthorized.app:/callback';
      const params = new URLSearchParams({
        response_type: 'code',
        client_id: fixture.application.settings.oauth.clientId,
        redirect_uri: unregisteredUri,
        state: TEST_CONSTANTS.STATE,
        code_challenge: CODE_CHALLENGE,
        code_challenge_method: 'S256',
      });
      const authUrl = `${fixture.openIdConfiguration.authorization_endpoint}?${params.toString()}`;

      const response = await performGet(authUrl).expect(302);
      expect(response.headers['location']).toContain('/oauth/error');
      expect(response.headers['location']).toContain('error=redirect_uri_mismatch');
      expect(response.headers['location']).toContain('MUST+match+the+registered+callback+URL');
    });

    it('should fail with malformed custom URI scheme', async () => {
      const malformedUri = 'invalid-uri-scheme';
      const params = new URLSearchParams({
        response_type: 'code',
        client_id: fixture.application.settings.oauth.clientId,
        redirect_uri: malformedUri,
        state: TEST_CONSTANTS.STATE,
        code_challenge: CODE_CHALLENGE,
        code_challenge_method: 'S256',
      });
      const authUrl = `${fixture.openIdConfiguration.authorization_endpoint}?${params.toString()}`;

      const response = await performGet(authUrl).expect(302);
      expect(response.headers['location']).toContain('/oauth/error');
      expect(response.headers['location']).toContain('error=redirect_uri_mismatch');
    });

    it('should fail with redirect_uri mismatch in token exchange', async () => {
      const redirectUri = 'com.different.app:/callback'; // Different from authorization
      const authCode = await fixture.completeAuthorizationFlow(CODE_CHALLENGE);

      const tokenParams = new URLSearchParams({
        grant_type: 'authorization_code',
        code: authCode,
        redirect_uri: redirectUri,
        code_verifier: CODE_VERIFIER,
      });

      const response = await performPost(
        fixture.openIdConfiguration.token_endpoint,
        '',
        tokenParams.toString(),
        {
          'Content-Type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${applicationBase64Token(fixture.application)}`,
        }
      ).expect(400);

      expect(response.body.error).toBe('invalid_grant');
      expect(response.body.error_description).toContain('Redirect URI mismatch');
    });

    it('should fail with HTTP redirect_uri when custom URI scheme is expected', async () => {
      const httpUri = 'http://localhost:4000/callback';
      const params = new URLSearchParams({
        response_type: 'code',
        client_id: fixture.application.settings.oauth.clientId,
        redirect_uri: httpUri,
        state: TEST_CONSTANTS.STATE,
        code_challenge: CODE_CHALLENGE,
        code_challenge_method: 'S256',
      });
      const authUrl = `${fixture.openIdConfiguration.authorization_endpoint}?${params.toString()}`;

      const response = await performGet(authUrl).expect(302);
      expect(response.headers['location']).toContain('/oauth/error');
      expect(response.headers['location']).toContain('error=redirect_uri_mismatch');
    });
  });
});
