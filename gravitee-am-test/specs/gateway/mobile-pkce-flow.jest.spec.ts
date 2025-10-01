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
  generateCodeVerifier, 
  generateCodeChallenge, 
  validateTokenResponse,
  MobilePKCEFixture 
} from './fixtures/mobile-pkce-fixture';

// Global setup
global.fetch = fetch;
jest.setTimeout(200000);

// Test constants
const MOBILE_REDIRECT_URI = 'net.openid.appauthdemo:/oauth2redirect';

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
    const codeVerifier = generateCodeVerifier();
    const codeChallenge = generateCodeChallenge();

    const authCode = await fixture.completeAuthorizationFlow(codeChallenge);
    const tokenResponse = await fixture.exchangeCodeForToken(authCode, codeVerifier).expect(200);

    validateTokenResponse(tokenResponse);
  });

  describe('PKCE Error Scenarios', () => {
    it('should fail when missing code_challenge parameter', async () => {
      const authUrl = fixture.buildInvalidAuthUrl({});

      const response = await performGet(authUrl).expect(302);
      expect(response.headers['location']).toContain(fixture.redirectUri);
      expect(response.headers['location']).toContain('error=invalid_request');
      expect(response.headers['location']).toContain('Missing+parameter%3A+code_challenge');
    });

    it('should fail when using plain code_challenge_method when S256 is forced', async () => {
      const authUrl = fixture.buildInvalidAuthUrl({
        code_challenge: 'plain-challenge',
        code_challenge_method: 'plain',
      });

      const response = await performGet(authUrl).expect(302);
      expect(response.headers['location']).toContain(fixture.redirectUri);
      expect(response.headers['location']).toContain('error=invalid_request');
      expect(response.headers['location']).toContain('Invalid+parameter%3A+code_challenge_method');
    });

    it('should fail when missing code_verifier in token exchange', async () => {
      const codeVerifier = generateCodeVerifier();
      const codeChallenge = generateCodeChallenge();
      const authCode = await fixture.completeAuthorizationFlow(codeChallenge);

      const tokenParams = new URLSearchParams({
        grant_type: 'authorization_code',
        code: authCode,
        redirect_uri: fixture.redirectUri,
        // Missing code_verifier
      });

      const response = await performPost(
        `${fixture.openIdConfiguration.token_endpoint}?${tokenParams.toString()}`,
        '',
        null,
        {
          Authorization: `Basic ${applicationBase64Token(fixture.application)}`,
        }
      ).expect(400);

      expect(response.body.error).toBe('invalid_grant');
      expect(response.body.error_description).toContain('code_verifier');
    });

    it('should fail when code_verifier does not match code_challenge', async () => {
      const codeVerifier = generateCodeVerifier();
      const codeChallenge = generateCodeChallenge();
      const authCode = await fixture.completeAuthorizationFlow(codeChallenge);

      const tokenParams = new URLSearchParams({
        grant_type: 'authorization_code',
        code: authCode,
        redirect_uri: fixture.redirectUri,
        code_verifier: 'wrong-verifier-that-does-not-match-challenge',
      });

      const response = await performPost(
        `${fixture.openIdConfiguration.token_endpoint}?${tokenParams.toString()}`,
        '',
        null,
        {
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
        state: 'test-state',
        code_challenge: generateCodeChallenge(),
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
        state: 'test-state',
        code_challenge: generateCodeChallenge(),
        code_challenge_method: 'S256',
      });
      const authUrl = `${fixture.openIdConfiguration.authorization_endpoint}?${params.toString()}`;

      const response = await performGet(authUrl).expect(302);
      expect(response.headers['location']).toContain('/oauth/error');
      expect(response.headers['location']).toContain('error=redirect_uri_mismatch');
    });

    it('should fail with redirect_uri mismatch in token exchange', async () => {
      const codeVerifier = generateCodeVerifier();
      const codeChallenge = generateCodeChallenge();
      const authCode = await fixture.completeAuthorizationFlow(codeChallenge);

      const tokenParams = new URLSearchParams({
        grant_type: 'authorization_code',
        code: authCode,
        redirect_uri: 'com.different.app:/callback', // Different from authorization
        code_verifier: codeVerifier,
      });

      const response = await performPost(
        `${fixture.openIdConfiguration.token_endpoint}?${tokenParams.toString()}`,
        '',
        null,
        {
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
        state: 'test-state',
        code_challenge: generateCodeChallenge(),
        code_challenge_method: 'S256',
      });
      const authUrl = `${fixture.openIdConfiguration.authorization_endpoint}?${params.toString()}`;

      const response = await performGet(authUrl).expect(302);
      expect(response.headers['location']).toContain('/oauth/error');
      expect(response.headers['location']).toContain('error=redirect_uri_mismatch');
    });
  });
});
