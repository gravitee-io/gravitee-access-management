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
import { CimdAuthorizeFixture, setupCimdAuthorizeFixture } from './fixtures/cimd-authorize-fixture';
import {
  clearWireMockRequestJournal,
  countGetRequestsForPathSubstring,
  fetchWireMockRequestJournal,
} from './fixtures/cimd-wiremock-helpers';

setup(120000);

let fixture: CimdAuthorizeFixture;

beforeAll(async () => {
  fixture = await setupCimdAuthorizeFixture('ENABLED_REDIRECT_TRUST');
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

// Only "cimd-trusted.example.test" is a trusted host for this domain, "cimd-internal.example.test" is the same
// WireMock server under another name.
const clientIdOf = (scenario: string): string => `http://cimd-trusted.example.test:8080/cimd/ENABLED_REDIRECT_TRUST/${scenario}`;

describe('CIMD logo endpoint - redirects and content', () => {
  it('should not follow a redirect to a host that is not trusted', async () => {
    await clearWireMockRequestJournal();
    const clientId = clientIdOf('logo-redirect-internal');
    fixture.expectLoginRedirect(await fixture.authorize(clientId));

    await fixture.fetchCimdLogo(clientId).expect(404);

    const journal = await fetchWireMockRequestJournal();
    expect(countGetRequestsForPathSubstring(journal, '/cimd/logos/redirect-internal.png')).toBeGreaterThanOrEqual(1);
    expect(countGetRequestsForPathSubstring(journal, '/cimd/internal/secret')).toBe(0);
  });

  it('should follow a redirect to a trusted host and serve the logo', async () => {
    const clientId = clientIdOf('logo-redirect-allowed');
    fixture.expectLoginRedirect(await fixture.authorize(clientId));

    const response = await fixture.fetchCimdLogo(clientId).expect(200);

    expect(response.headers['content-type']).toContain('image/png');
    expect(response.body.length).toBeGreaterThan(0);
  });

  it('should not serve a response that is not an image', async () => {
    const clientId = clientIdOf('logo-not-image');
    fixture.expectLoginRedirect(await fixture.authorize(clientId));

    await fixture.fetchCimdLogo(clientId).expect(404);
  });

  it('should serve the logo with headers that prevent content sniffing', async () => {
    const clientId = clientIdOf('logo-redirect-allowed');
    fixture.expectLoginRedirect(await fixture.authorize(clientId));

    const response = await fixture.fetchCimdLogo(clientId).expect(200);

    expect(response.headers['x-content-type-options']).toBe('nosniff');
    expect(response.headers['content-security-policy']).toContain("default-src 'none'");
  });
});
