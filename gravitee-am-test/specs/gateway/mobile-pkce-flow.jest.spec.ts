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
import { login } from '@gateway-commands/login-commands';
import { applicationBase64Token } from '@gateway-commands/utils';

// Fixtures
import { 
  setupMobilePKCEFixture, 
  generateCodeVerifier, 
  generateCodeChallenge, 
  buildAuthorizationUrl,
  extractAuthorizationCode,
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
  it('should complete mobile PKCE flow with custom URI scheme', async () => {
    // Generate PKCE parameters for mobile app
    const mobileCodeVerifier = generateCodeVerifier();
    const mobileCodeChallenge = generateCodeChallenge();

    const mobileClientId = fixture.application.settings.oauth.clientId;

    // Step 1: Initiate mobile authorization flow with PKCE parameters
    const mobileAuthUrl = buildAuthorizationUrl(
      fixture.openIdConfiguration.authorization_endpoint,
      mobileClientId,
      MOBILE_REDIRECT_URI,
      mobileCodeChallenge
    );
    const mobileAuthResponse = await performGet(mobileAuthUrl).expect(302);

    // Step 2: Complete mobile user login flow
    const mobileLoginResponse = await login(
      mobileAuthResponse,
      fixture.user.username,
      mobileClientId,
      'MobileP@ssw0rd123!',
      false,
      false
    );

    // Step 3: Follow redirect to mobile authorization endpoint
    const mobileAuthorizeResponse = await performGet(mobileLoginResponse.headers['location'], '', {
      Cookie: mobileLoginResponse.headers['set-cookie'],
    }).expect(302);

    // Step 4: Extract authorization code from mobile custom URI scheme redirect
    const mobileRedirectUrl = mobileAuthorizeResponse.headers['location'];
    expect(mobileRedirectUrl).toContain(MOBILE_REDIRECT_URI);
    expect(mobileRedirectUrl).toContain('code=');
    const mobileAuthorizationCode = extractAuthorizationCode(mobileRedirectUrl);

    // Step 5: Exchange mobile authorization code for access token
    const mobileTokenParams = new URLSearchParams({
      grant_type: 'authorization_code',
      code: mobileAuthorizationCode,
      redirect_uri: MOBILE_REDIRECT_URI,
      code_verifier: mobileCodeVerifier,
    });

    const mobileTokenResponse = await performPost(
      `${fixture.openIdConfiguration.token_endpoint}?${mobileTokenParams.toString()}`,
      '',
      null,
      {
        Authorization: `Basic ${applicationBase64Token(fixture.application)}`,
      }
    ).expect(200);

    // Step 6: Validate mobile access token response
    validateTokenResponse(mobileTokenResponse);
  });
});
