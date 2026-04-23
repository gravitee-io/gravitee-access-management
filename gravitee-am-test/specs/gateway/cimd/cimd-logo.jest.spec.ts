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
import { clearWireMockRequestJournal, waitForWireMockGetToPath } from './fixtures/cimd-wiremock-helpers';

setup(120000);

let fixture: CimdAuthorizeFixture;

beforeAll(async () => {
  fixture = await setupCimdAuthorizeFixture('ENABLED_BASE');
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('CIMD logo endpoint', () => {
  it('should return 400 when clientId parameter is absent', async () => {
    await fixture.fetchCimdLogo().expect(400);
  });

  it('should return 404 when logo is not yet cached', async () => {
    await fixture.fetchCimdLogo(fixture.buildClientId('valid-none')).expect(404);
  });

  it('should return 200 with image/png after authorize populates the logo cache', async () => {
    await clearWireMockRequestJournal();
    fixture.expectLoginRedirect(await fixture.authorize(fixture.buildClientId('valid-none')));
    await waitForWireMockGetToPath('/cimd/logos/example.png');

    const response = await fixture.fetchCimdLogo(fixture.buildClientId('valid-none')).expect(200);
    expect(response.headers['content-type']).toContain('image/png');
    expect(response.body.length).toBeGreaterThan(0);
  });
});
