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
import { extractXsrfTokenAndActionResponse, performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';
import { DEVICE_FLOW_TEST, DeviceFlowFixture, setupDeviceFlowFixture } from './fixtures/device-flow-fixture';

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

describe('OAuth2 - RFC 8628 - Device Authorization Grant - complete verification URI', () => {
  it('must pre-fill the scanned code in plain sight for an authenticated user', async () => {
    const body = await fixture.requestDeviceCode();
    const session = await fixture.signInOnSecondDevice(fixture.confidentialClientId);

    const prefilled = await performGet(body.verification_uri_complete, '', { Cookie: session.cookie }).expect(200);

    expect(prefilled.text).toContain(`value="${body.user_code}"`);
    expect(prefilled.text).toContain('name="user_code"');
    expect(prefilled.text).toContain(
      `${fixture.confidentialApp.settings.oauth.clientName ?? fixture.confidentialApp.name} wants to connect a device to your account.`,
    );
    expect(prefilled.text).toContain('Only continue if the device is in front of you and shows this code.');
  });

  it('must carry the scanned code through the login redirect', async () => {
    const body = await fixture.requestDeviceCode();

    const unauthenticated = await performGet(body.verification_uri_complete).expect(302);
    expect(unauthenticated.headers['location']).toContain(`/${fixture.domain.hrid}/login`);

    const loginPage = await extractXsrfTokenAndActionResponse(unauthenticated);
    const postLogin = await performFormPost(
      loginPage.action,
      '',
      {
        'X-XSRF-TOKEN': loginPage.token,
        username: fixture.user.username,
        password: fixture.user.password,
        client_id: fixture.confidentialClientId,
      },
      { Cookie: loginPage.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(302);

    expect(postLogin.headers['location']).toContain(`user_code=${body.user_code}`);

    const prefilled = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'] ?? loginPage.headers['set-cookie'],
    }).expect(200);
    expect(prefilled.text).toContain(`value="${body.user_code}"`);
  });

  it('must keep the scanned code out of the login page URL', async () => {
    const body = await fixture.requestDeviceCode();

    const unauthenticated = await performGet(body.verification_uri_complete).expect(302);
    expect(unauthenticated.headers['location']).toContain(`/${fixture.domain.hrid}/login`);
    expect(unauthenticated.headers['location']).toContain(`client_id=${fixture.confidentialClientId}`);
    expect(unauthenticated.headers['location']).not.toContain('user_code');

    const loginPage = await extractXsrfTokenAndActionResponse(unauthenticated);
    expect(loginPage.action).not.toContain('user_code');
  });

  it('must not approve a scanned code without an explicit confirmation', async () => {
    const body = await fixture.requestDeviceCode();

    await fixture.signInOnSecondDevice(fixture.confidentialClientId, body.verification_uri_complete);

    const stillPending = await fixture.poll(body.device_code).expect(400);
    expect(stillPending.body.error).toEqual('authorization_pending');
  });

  it('must report the invalid outcome for a tampered code carried by the URI', async () => {
    const body = await fixture.requestDeviceCode();
    const tamperedUri = body.verification_uri_complete.replace(body.user_code, DEVICE_FLOW_TEST.UNKNOWN_USER_CODE);

    const session = await fixture.signInOnSecondDevice(fixture.confidentialClientId, tamperedUri);
    const submitted = await fixture.enterCode(session, DEVICE_FLOW_TEST.UNKNOWN_USER_CODE).expect(302);

    expect(submitted.headers['location']).toContain('/oauth/device');
    expect(submitted.headers['location']).not.toContain('/oauth/authorize');

    const stillPending = await fixture.poll(body.device_code).expect(400);
    expect(stillPending.body.error).toEqual('authorization_pending');
  });

  it('must let the user correct a pre-filled code before confirming', async () => {
    const scanned = await fixture.requestDeviceCode();
    const intended = await fixture.requestDeviceCode();

    const session = await fixture.signInOnSecondDevice(fixture.confidentialClientId, scanned.verification_uri_complete);
    const corrected = await fixture.enterCode(session, intended.user_code).expect(302);

    expect(corrected.headers['location']).toContain('/oauth/authorize');

    const untouched = await fixture.poll(scanned.device_code).expect(400);
    expect(untouched.body.error).toEqual('authorization_pending');
  });
});
