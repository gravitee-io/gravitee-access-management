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

setup(200000);

// Host of every CIMD client_id used in these tests; the trust policy keys off this host.
const WIREMOCK_VALID_CLIENT_ID = 'http://wiremock:8080/cimd/ENABLED_BASE/valid-none';

let denyFixture: CimdAuthorizeFixture;
let httpsOnlyFixture: CimdAuthorizeFixture;
let privateIpFixture: CimdAuthorizeFixture;

beforeAll(async () => {
  [denyFixture, httpsOnlyFixture, privateIpFixture] = await Promise.all([
    setupCimdAuthorizeFixture('ENABLED_DOMAIN_DENY'),
    setupCimdAuthorizeFixture('ENABLED_HTTPS_ONLY'),
    setupCimdAuthorizeFixture('ENABLED_PRIVATE_IP_DENY'),
  ]);
});

afterAll(async () => {
  await Promise.all([denyFixture?.cleanUp(), httpsOnlyFixture?.cleanUp(), privateIpFixture?.cleanUp()]);
});

describe('CIMD authorize - URL trust policy', () => {
  it('should reject CIMD resolution when the host is not in the allowed-domains allowlist', async () => {
    const response = await denyFixture.authorize(WIREMOCK_VALID_CLIENT_ID);
    denyFixture.expectInvalidClientMetadata(response, 'not in allowed domains');
  });

  it('should reject an unsecured http client_id when only https is allowed', async () => {
    const response = await httpsOnlyFixture.authorize(WIREMOCK_VALID_CLIENT_ID);
    httpsOnlyFixture.expectInvalidClientMetadata(response, 'Unsecured HTTP');
  });

  it('should reject CIMD resolution when the host resolves to a private IP and private addresses are disallowed', async () => {
    const response = await privateIpFixture.authorize(WIREMOCK_VALID_CLIENT_ID);
    privateIpFixture.expectInvalidClientMetadata(response, 'private or reserved IP');
  });
});
