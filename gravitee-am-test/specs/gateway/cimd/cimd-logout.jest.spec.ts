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
import {
  CIMD_POST_LOGOUT_REDIRECT_URI,
  CIMD_SOCIAL_CLIENT_ID,
  CimdSocialLoginFixture,
  DOMAIN_POST_LOGOUT_REDIRECT_URI,
  setupCimdSocialLoginFixture,
} from './fixtures/cimd-social-login-fixture';

setup(200000);

let fixture: CimdSocialLoginFixture;

beforeAll(async () => {
  fixture = await setupCimdSocialLoginFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

/**
 * AM-7658: after a social login the user record stores the CIMD `client_id` (a URL), so RP-initiated
 * logout has to re-synthesize the client from its metadata document. The lookup used to ignore CIMD,
 * leaving the endpoint with no client at all and falling back to the domain settings.
 *
 * The logout requests below never carry a `client_id` parameter on purpose: the client can only come
 * from the session, which is exactly the code path under test.
 */
describe('CIMD logout - RP-initiated logout after an external (social) login', () => {
  it('should accept a post_logout_redirect_uri registered on the CIMD client', async () => {
    const session = await fixture.loginThroughExternalIdp(CIMD_SOCIAL_CLIENT_ID);

    const response = await fixture.logout(session, CIMD_POST_LOGOUT_REDIRECT_URI);

    expect(response.headers['location']).toEqual(CIMD_POST_LOGOUT_REDIRECT_URI);
  });

  it('should reject a post_logout_redirect_uri only registered at domain level', async () => {
    // The CIMD client declares its own list, so the domain-wide one must no longer apply.
    const session = await fixture.loginThroughExternalIdp(CIMD_SOCIAL_CLIENT_ID);

    const response = await fixture.logout(session, DOMAIN_POST_LOGOUT_REDIRECT_URI);

    expect(response.headers['location']).toContain('/error');
    expect(response.headers['location']).toContain('error=invalid_request');
  });

  it('should reject an unregistered post_logout_redirect_uri', async () => {
    const session = await fixture.loginThroughExternalIdp(CIMD_SOCIAL_CLIENT_ID);

    const response = await fixture.logout(session, 'https://unknown.example.com/logged-out');

    expect(response.headers['location']).toContain('/error');
    expect(response.headers['location']).toContain('error=invalid_request');
  });
});
