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
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';
import { DeviceFlowFixture, setupDeviceFlowFixture } from './fixtures/device-flow-fixture';

setup(200000);

let fixture: DeviceFlowFixture;

beforeAll(async () => {
  fixture = await setupDeviceFlowFixture({ deviceFlowEnabled: false });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('OAuth2 - RFC 8628 - Device Authorization Grant - domain setting off', () => {
  it('must not expose the device authorization endpoint', async () => {
    await performPost(fixture.deviceAuthorizationEndpoint, '', 'scope=openid', {
      'Content-type': 'application/x-www-form-urlencoded',
      ...fixture.confidentialAuth(),
    }).expect(404);
  });

  it('must not expose the verification page', async () => {
    await performGet(fixture.verificationUri(fixture.confidentialClientId)).expect(404);
  });
});
