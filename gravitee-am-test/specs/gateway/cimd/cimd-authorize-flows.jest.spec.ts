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
import { setup } from '../../test-fixture';
import { CIMD_REDIRECT_URI, CimdAuthorizeFixture, setupCimdAuthorizeFixture } from './fixtures/cimd-authorize-fixture';
import { decodeJwt } from '@utils-commands/jwt';
import { generatePkcePair } from '@utils-commands/pkce';
import crypto from 'crypto';

setup(200000);

let fixture: CimdAuthorizeFixture;

beforeAll(async () => {
  fixture = await setupCimdAuthorizeFixture('ENABLED_BASE');
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('CIMD authorize - ENABLED_BASE - OAuth/OIDC flows', () => {
  describe('Authorization code flow with PKCE', () => {
    it('should complete the authorization code flow with a valid S256 code_verifier', async () => {
      const clientId = fixture.buildClientId('valid-none');
      const pkce = generatePkcePair();

      const code = await fixture.completeAuthorizationCodeFlow(clientId, CIMD_REDIRECT_URI, {
        code_challenge: pkce.codeChallenge,
        code_challenge_method: pkce.codeChallengeMethod,
      });

      const tokenResponse = await fixture.exchangeAuthCodeForToken(code, clientId, CIMD_REDIRECT_URI, {
        code_verifier: pkce.codeVerifier,
      });
      const accessToken = tokenResponse.body.access_token;
      expect(accessToken).toBeDefined();
      expect(decodeJwt(accessToken).aud).toBe(clientId);

      const introspection = await fixture.introspectToken(accessToken);
      expect(introspection.active).toBe(true);
    });

    it('should reject the token exchange when the PKCE code_verifier does not match the challenge', async () => {
      const clientId = fixture.buildClientId('valid-none');
      const pkce = generatePkcePair();

      const code = await fixture.completeAuthorizationCodeFlow(clientId, CIMD_REDIRECT_URI, {
        code_challenge: pkce.codeChallenge,
        code_challenge_method: pkce.codeChallengeMethod,
      });

      const tokenResponse = await fixture.exchangeAuthCodeForTokenExpectingError(code, clientId, CIMD_REDIRECT_URI, {
        code_verifier: generatePkcePair().codeVerifier,
      });
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.body.error).toBe('invalid_grant');
    });

    it('should reject the token exchange when the PKCE code_verifier is missing', async () => {
      const clientId = fixture.buildClientId('valid-none');
      const pkce = generatePkcePair();

      const code = await fixture.completeAuthorizationCodeFlow(clientId, CIMD_REDIRECT_URI, {
        code_challenge: pkce.codeChallenge,
        code_challenge_method: pkce.codeChallengeMethod,
      });

      const tokenResponse = await fixture.exchangeAuthCodeForTokenExpectingError(code, clientId);
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.body.error).toBe('invalid_grant');
    });
  });

  describe('OIDC id_token and userinfo on the authorization code path', () => {
    it('should issue an id_token bound to the CIMD client and expose claims via userinfo', async () => {
      const clientId = fixture.buildClientId('valid-none');
      const nonce = `cimd-nonce-${crypto.randomUUID()}`;

      const code = await fixture.completeAuthorizationCodeFlow(clientId, CIMD_REDIRECT_URI, { nonce });
      const tokenResponse = await fixture.exchangeAuthCodeForToken(code, clientId);

      const idToken = tokenResponse.body.id_token;
      expect(idToken).toBeDefined();
      const idTokenClaims = decodeJwt(idToken);
      expect(idTokenClaims.aud).toBe(clientId);
      expect(idTokenClaims.iss).toContain(fixture.domain.hrid);
      expect(idTokenClaims.sub).toBeDefined();
      expect(idTokenClaims.nonce).toBe(nonce);

      const userInfo = await fixture.fetchUserInfo(tokenResponse.body.access_token);
      expect(userInfo.sub).toBe(idTokenClaims.sub);
    });
  });

  describe('Template intersection of metadata grant_types and scope', () => {
    it('should restrict the synthesized client scopes to those allowed by the template', async () => {
      const clientId = fixture.buildClientId('scope-intersection');

      const callbackLocation = await fixture.loginAndGetCallbackLocation(clientId, CIMD_REDIRECT_URI, {
        scope: 'openid profile email',
      });
      const callbackUrl = new URL(callbackLocation);
      expect(callbackUrl.searchParams.get('error')).toBe('invalid_scope');
      expect(callbackUrl.searchParams.get('error_description')).toContain('profile');
      expect(callbackUrl.searchParams.get('error_description')).toContain('email');
    });

    it('should reject the code flow when metadata grant_types exclude authorization_code', async () => {
      // Metadata declares only client_credentials; intersected against the template
      // (authorization_code) this leaves the client with no usable grant for the code flow.
      const response = await fixture.authorize(fixture.buildClientId('grant-types-intersection'));
      const error = fixture.readOAuthError(response);
      expect(error.error).toBeTruthy();
      expect(error.location).not.toContain('/login');
    });
  });
});
