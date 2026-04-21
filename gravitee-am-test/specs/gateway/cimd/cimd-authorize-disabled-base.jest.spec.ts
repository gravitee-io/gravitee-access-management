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

import { afterAll, beforeAll, describe, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import { CimdAuthorizeFixture, setupCimdAuthorizeFixture } from './fixtures/cimd-authorize-fixture';

setup(200000);

let fixture: CimdAuthorizeFixture;

beforeAll(async () => {
  fixture = await setupCimdAuthorizeFixture('DISABLED_BASE');
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('CIMD authorize - DISABLED_BASE', () => {
  it('should load a pre-registered URL client_id when CIMD is disabled', async () => {
    const response = await fixture.authorize(fixture.preRegisteredUrlApplication.settings.oauth.clientId);
    fixture.expectLoginRedirect(response);
  });

  it('should keep regular invalid_request behavior when CIMD is disabled and URL client_id is unknown', async () => {
    const response = await fixture.authorize('http://wiremock:8080/cimd/DISABLED_BASE/unknown-client');
    fixture.expectInvalidRequest(response, 'No client found for client_id');
  });

  it('should not resolve metadata when CIMD is disabled', async () => {
    const response = await fixture.authorize(fixture.buildClientId('valid-none'));
    fixture.expectInvalidRequest(response, 'No client found for client_id');
  });
});
