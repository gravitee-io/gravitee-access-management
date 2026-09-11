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
  CIMD_SOCIAL_CLIENT_ID,
  CIMD_SOCIAL_REDIRECT_URI,
  CimdSocialLoginFixture,
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

describe('CIMD authorize - external (social) identity provider', () => {
  // AM-7658: /login/callback used to re-resolve the client with a lookup that ignores CIMD,
  // which aborted the flow with social_authentication_failed / Bad client credentials.
  it('should resume the authorization request and issue a code after the external IdP callback', async () => {
    const { location } = await fixture.loginThroughExternalIdp(CIMD_SOCIAL_CLIENT_ID);

    expect(location).toContain(CIMD_SOCIAL_REDIRECT_URI);
    expect(location).not.toContain('social_authentication_failed');
    expect(location).toMatch(/[?&]code=([-_a-zA-Z0-9]+)/);
    expect(new URL(location).searchParams.get('state')).toBe('cimd-social-state');
  });
});
