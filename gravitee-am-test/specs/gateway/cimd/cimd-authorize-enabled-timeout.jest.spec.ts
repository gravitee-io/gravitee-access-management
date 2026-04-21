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
  fixture = await setupCimdAuthorizeFixture('ENABLED_TIMEOUT');
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('CIMD authorize - ENABLED_TIMEOUT', () => {
  it('should return invalid_client_metadata when metadata fetch exceeds configured timeout', async () => {
    const response = await fixture.authorize(fixture.buildClientId('slow-response'));
    fixture.expectInvalidClientMetadata(response, 'timed out');
  });
});
