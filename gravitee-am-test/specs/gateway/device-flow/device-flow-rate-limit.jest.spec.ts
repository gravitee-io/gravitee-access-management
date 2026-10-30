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
import { DEVICE_FLOW_TEST, DeviceFlowFixture, setupDeviceFlowFixture, wrongCodeBudget } from './fixtures/device-flow-fixture';

setup(200000);

let fixture: DeviceFlowFixture;

beforeAll(async () => {
  fixture = await setupDeviceFlowFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('OAuth2 - RFC 8628 - Device Authorization Grant - per-user code entry rate limit', () => {
  it('must spend a user budget on wrong codes only, and refuse that user alone once it runs out', async () => {
    const budget = await wrongCodeBudget();
    const guessingUser = await fixture.createUser();
    const guessingUserSession = () =>
      fixture.signInOnSecondDevice(fixture.publicClientId, fixture.verificationUri(fixture.publicClientId), guessingUser);

    for (let pairing = 0; pairing < budget; pairing++) {
      const paired = await fixture.requestPublicDeviceCode();
      const rightCode = await fixture.enterCode(await guessingUserSession(), paired.user_code).expect(302);
      expect(rightCode.headers['location']).toContain('/oauth/authorize');
    }

    let session = await guessingUserSession();
    for (let guess = 0; guess < budget; guess++) {
      const wrongCode = await fixture.enterCode(session, DEVICE_FLOW_TEST.UNKNOWN_USER_CODE).expect(302);
      expect(wrongCode.headers['location']).toContain('/oauth/device');
      session = await fixture.reopenCodeEntryPage(session, fixture.publicClientId);
    }

    const body = await fixture.requestPublicDeviceCode();
    const afterTheBudget = await fixture.enterCode(session, body.user_code).expect(302);
    expect(afterTheBudget.headers['location']).toContain('/oauth/device');
    expect(afterTheBudget.headers['location']).not.toContain('/oauth/authorize');

    const otherUserSession = await fixture.signInOnSecondDevice(fixture.publicClientId);
    const accepted = await fixture.enterCode(otherUserSession, body.user_code).expect(302);
    expect(accepted.headers['location']).toContain('/oauth/authorize');
  });
});
