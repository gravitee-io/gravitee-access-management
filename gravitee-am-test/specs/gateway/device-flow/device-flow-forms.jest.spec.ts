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
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';
import { DEVICE_FLOW_TEST, DeviceFlowFixture, setupDeviceFlowFixture } from './fixtures/device-flow-fixture';

setup(200000);

let fixture: DeviceFlowFixture;

beforeAll(async () => {
  fixture = await setupDeviceFlowFixture({ withOverriddenForms: true });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('OAuth2 - RFC 8628 - Device Authorization Grant - overridden forms', () => {
  it('must serve both overridden forms through an approval', async () => {
    const approvingUser = await fixture.createUser();
    const body = await fixture.requestDeviceCode();

    const { session, completion } = await fixture.approveThroughConsent(body.user_code, fixture.confidentialClientId, approvingUser);
    expect(completion.text).toContain('overridden completion page');
    expect(completion.text).toContain('approved');

    const entryPage = await performGet(fixture.verificationUri(fixture.confidentialClientId), '', { Cookie: session.cookie }).expect(200);
    expect(entryPage.text).toContain('overridden code entry page');
  });

  it('must report the denied outcome on the completion page', async () => {
    const body = await fixture.requestDeviceCode();
    const session = await fixture.signInOnSecondDevice(fixture.confidentialClientId);

    const rejection = await fixture.rejectCode(session, body.user_code).expect(200);

    expect(rejection.text).toContain('overridden completion page');
    expect(rejection.text).toContain('denied');
  });

  it('must report the invalid outcome for a code that is not outstanding', async () => {
    const session = await fixture.signInOnSecondDevice(fixture.confidentialClientId);

    const rejection = await fixture.rejectCode(session, DEVICE_FLOW_TEST.UNKNOWN_USER_CODE).expect(200);

    expect(rejection.text).toContain('overridden completion page');
    expect(rejection.text).toContain('invalid');
  });
});
